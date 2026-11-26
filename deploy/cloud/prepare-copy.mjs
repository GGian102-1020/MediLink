// LOCAL ONLY. Restores to an isolated review container; never writes to medlink-db.
import {execFileSync} from 'node:child_process';
import {readFileSync,writeFileSync,mkdirSync,existsSync} from 'node:fs';
import {randomBytes,createHash} from 'node:crypto';
import {docker,inspect,query,rows,snapshot} from '../../scripts/demo-local/Database-Tools.mjs';
import assert from 'node:assert/strict';
const dir='.local-cloud';mkdirSync(dir,{recursive:true});
const name='medifind-cloud-review-db',network='medifind-cloud-review';
const backup=JSON.parse(readFileSync('.local-demo/original-database-backup.json','utf8'));
assert.ok(backup.restoredAndCompared&&backup.integrityCheckPassed);
assert.ok(inspect('medlink-db').Mounts.some(x=>x.Name==='medilink_pgdata'));
const existing=docker(['ps','-a','--filter',`name=^/${name}$`,'--format','{{.ID}}']);
if(existing&&!process.argv.includes('--resume-unmodified'))throw Error('Review container already exists; never overwrite an existing database');
if(!docker(['network','ls','--filter',`name=^${network}$`,'--format','{{.ID}}']))docker(['network','create','--internal','--label','medifind.cloud.review=true',network]);
if(!existing)docker(['run','-d','--name',name,'--label','medifind.cloud.review=true','--network',network,'--tmpfs','/var/lib/postgresql','-e','POSTGRES_USER=medlink','-e','POSTGRES_DB=medlink','-e','POSTGRES_HOST_AUTH_METHOD=trust','postgres:18-alpine']);
else assert.equal(inspect(name).Config.Labels['medifind.cloud.review'],'true');
let ready=false;for(let i=0;i<50;i++){try{docker(['exec',name,'pg_isready','-h','127.0.0.1','-U','medlink','-d','medlink']);ready=true;break;}catch{await new Promise(r=>setTimeout(r,500));}}
assert.ok(ready,'Isolated review database did not start');
const dump=readFileSync(backup.path);assert.equal(createHash('sha256').update(dump).digest('hex'),backup.sha256);
if(!existing)execFileSync('docker',['exec','-i',name,'pg_restore','--exit-on-error','-U','medlink','-d','medlink'],{input:dump,maxBuffer:16*1024*1024});
const q=sql=>rows(name,'medlink','medlink',sql);
const run=sql=>execFileSync('docker',['exec','-i',name,'psql','-X','-U','medlink','-d','medlink','-v','ON_ERROR_STOP=1'],{input:sql,encoding:'utf8'});
const before=snapshot(name,'medlink','medlink');
for(const t of backup.tables)assert.equal(before.find(x=>x.table===t.table)?.count,t.count,t.table);
if(existing)for(const t of backup.tables)assert.equal(before.find(x=>x.table===t.table)?.hash,t.hash,`Resume requires original untouched data: ${t.table}`);
const privacy=q(`SELECT (SELECT count(*) FROM cuenta WHERE correo !~* '\\.test$') personal_domain_accounts,(SELECT count(*) FROM usuario WHERE dni IS NOT NULL OR fecha_nacimiento IS NOT NULL OR telefono IS NOT NULL OR obra_social IS NOT NULL OR numero_afiliado IS NOT NULL) profiles_with_personal_fields,(SELECT count(*) FROM receta_adjunta) attachments,(SELECT count(*) FROM mensaje_chat) messages`)[0];
const accounts=q('SELECT id_cuenta,rol,estado FROM cuenta ORDER BY id_cuenta');
const activeIds=q("SELECT id_cuenta FROM usuario WHERE id_usuario=1 UNION ALL SELECT id_cuenta FROM operador WHERE id_operador=3 UNION ALL SELECT id_cuenta FROM administrador ORDER BY id_cuenta").map(x=>x.id_cuenta);
assert.equal(activeIds.length,3);
const credentials=accounts.map(a=>({...a,email:`${a.rol.toLowerCase()}-${a.id_cuenta}@demo.medifind.test`,password:randomBytes(32).toString('base64url'),enabled:activeIds.includes(a.id_cuenta)}));
run('CREATE EXTENSION IF NOT EXISTS pgcrypto;');
const commands=['BEGIN;'];
// Only the isolated clone: temporarily permit pseudonymizing immutable free text,
// then restore the exact original trigger protection before committing.
const immutable=q("SELECT c.relname,t.tgname,t.tgenabled FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_proc p ON p.oid=t.tgfoid WHERE p.proname IN ('medlink_no_actualizar','medlink_proteger_reserva')");
for(const t of immutable){assert.equal(t.tgenabled,'O');assert.match(t.relname,/^[a-z_]+$/);assert.match(t.tgname,/^[a-z_]+$/);commands.push(`ALTER TABLE ${t.relname} DISABLE TRIGGER ${t.tgname};`);}
for(const a of credentials)commands.push(`UPDATE cuenta SET correo='${a.email}',password_hash=crypt('${a.password}',gen_salt('bf',12)),token_version=token_version+1,estado='${a.enabled?'ACTIVA':'DESHABILITADA'}' WHERE id_cuenta=${a.id_cuenta};`);
commands.push(`UPDATE usuario SET nombre='Paciente',apellido='Academico '||id_usuario,dni=NULL,fecha_nacimiento=NULL,telefono=NULL,obra_social=NULL,numero_afiliado=NULL;`);
commands.push(`UPDATE operador SET nombre='Operador',apellido='Academico '||id_operador; UPDATE administrador SET nombre='Administrador',apellido='Academico '||id_administrador;`);
commands.push(`UPDATE mensaje_chat SET texto='[Mensaje academico anonimizado para cloud]';`);
commands.push(`UPDATE idempotency_record i SET response_201=i.response_201 || jsonb_build_object('usuarioNombre','Paciente','usuarioApellido','Academico '||u.id_usuario) FROM usuario u WHERE u.id_cuenta=i.id_cuenta;`);
commands.push(`UPDATE auditoria SET motivo=CASE WHEN motivo IS NULL THEN NULL ELSE '[Anonimizado cloud]' END,resumen_anterior=CASE WHEN resumen_anterior IS NULL THEN NULL ELSE '{"anonimizadoParaCloud":true}'::jsonb END,resumen_posterior=CASE WHEN resumen_posterior IS NULL THEN NULL ELSE '{"anonimizadoParaCloud":true}'::jsonb END;`);
commands.push(`UPDATE reserva SET motivo_cancelacion=CASE WHEN motivo_cancelacion IS NULL THEN NULL ELSE '[Anonimizado cloud]' END; UPDATE movimiento_stock SET motivo=CASE WHEN motivo IS NULL THEN NULL ELSE '[Anonimizado cloud]' END;`);
commands.push(`UPDATE recuperacion_contrasena SET codigo_hash=encode(gen_random_bytes(32),'hex'),intentos=5;`);
for(const t of immutable)commands.push(`ALTER TABLE ${t.relname} ENABLE TRIGGER ${t.tgname};`);
commands.push('COMMIT;');
run(commands.join('\n'));
const after=snapshot(name,'medlink','medlink');
for(const t of before)assert.equal(after.find(x=>x.table===t.table)?.count,t.count,t.table);
const preserved=['farmacia','stock','medicamento','presentacion_medicamento','receta_demo','prescripcion','item_prescripcion','receta_adjunta','flyway_schema_history','suspension'];
for(const table of preserved)assert.deepEqual(after.find(x=>x.table===table),before.find(x=>x.table===table),table);
assert.equal(q("SELECT count(*) n FROM cuenta WHERE correo !~* '\\.test$'")[0].n,0);
assert.equal(q('SELECT count(*) n FROM usuario WHERE dni IS NOT NULL OR fecha_nacimiento IS NOT NULL OR telefono IS NOT NULL OR obra_social IS NOT NULL OR numero_afiliado IS NOT NULL')[0].n,0);
mkdirSync(`${dir}/attachment-review`,{recursive:true});const attachments=q('SELECT id_receta_adjunta,content_type,tamano_bytes,sha256 FROM receta_adjunta ORDER BY id_receta_adjunta');
for(const a of attachments){const ext=({'application/pdf':'pdf','image/jpeg':'jpg','image/png':'png','image/webp':'webp'})[a.content_type];assert.ok(ext,'Unrecognized attachment type requires explicit privacy review');const bytes=Buffer.from(query(name,'medlink','medlink',`SELECT encode(contenido,'base64') FROM receta_adjunta WHERE id_receta_adjunta=${a.id_receta_adjunta}`).replace(/\s/g,''),'base64');assert.equal(bytes.length,a.tamano_bytes);assert.equal(createHash('sha256').update(bytes).digest('hex'),a.sha256.trim());a.path=`${dir}/attachment-review/${a.id_receta_adjunta}.${ext}`;writeFileSync(a.path,bytes);}
const sanitized=execFileSync('docker',['exec',name,'pg_dump','-U','medlink','-d','medlink','-Fc'],{maxBuffer:256*1024*1024});writeFileSync(`${dir}/medifind-cloud-sanitized.dump`,sanitized);
writeFileSync(`${dir}/credentials.private.json`,JSON.stringify(credentials.filter(a=>a.enabled),null,2));
const report={at:new Date().toISOString(),source:'medlink-db / medilink_pgdata (read only)',backup:backup.path,backupSha256:backup.sha256,reviewContainer:name,network,postgres:18,privacyFindings:privacy,anonymized:true,credentialsRotated:true,enabledAccounts:3,preservedTables:preserved,counts:after.map(({table,count})=>({table,count})),attachments,attachmentReviewApproved:false,dump:`${dir}/medifind-cloud-sanitized.dump`,dumpSha256:createHash('sha256').update(sanitized).digest('hex'),uploaded:false};
writeFileSync(`${dir}/copy-review.json`,JSON.stringify(report,null,2));console.log(JSON.stringify({...report,attachments:attachments.map(({path,...a})=>({...a,path}))},null,2));
