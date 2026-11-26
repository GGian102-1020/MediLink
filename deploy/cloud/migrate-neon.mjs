// Reads credentials privately from stdin. Never prints or versions a connection URL.
import{readFileSync,writeFileSync,unlinkSync}from'node:fs';
import{resolve}from'node:path';
import{execFileSync}from'node:child_process';
import{createHash}from'node:crypto';
import assert from'node:assert/strict';
const report=JSON.parse(readFileSync('.local-cloud/copy-review.json','utf8'));
assert.ok(report.anonymized&&report.credentialsRotated&&report.attachmentReviewApproved&&report.sanitizedRestoreVerified);
assert.equal(createHash('sha256').update(readFileSync(report.dump)).digest('hex'),report.dumpSha256);
const privateFile=process.argv.includes('--private-file');
let input;
if(privateFile){input=readFileSync('.local-cloud/neon-input.private.json','utf8');unlinkSync('.local-cloud/neon-input.private.json');}
else{
 if(process.stdin.isTTY)process.stdin.setRawMode(true);
 process.stdout.write('Ready for private migration input (not echoed).\n');
 input=await new Promise((done,reject)=>{let s='';process.stdin.setEncoding('utf8');process.stdin.on('data',x=>{s+=x;if(/[\r\n]/.test(s)){process.stdin.pause();done(s.split(/[\r\n]/)[0]);}});process.stdin.on('error',reject);});
 if(process.stdin.isTTY)process.stdin.setRawMode(false);
}
const config=JSON.parse(input);
const checkOnly=process.argv.includes('--check-only');
if(!checkOnly)assert.equal(config.publicationApproved,true,'User publication approval is required');
assert.equal(config.projectId,'flat-shadow-76171117');assert.equal(config.dumpSha256,report.dumpSha256);
const u=new URL(config.connection);assert.equal(u.protocol,'postgresql:');assert.ok(u.hostname.endsWith('.neon.tech'));assert.ok(!u.hostname.includes('-pooler'),'Use direct connection for restore');
if(!checkOnly)assert.match(config.applicationPassword,/^[A-Za-z0-9_-]{32,80}$/);
const file=resolve('.local-cloud/neon-migration.private.env');
const variables={PGHOST:u.hostname,PGPORT:u.port||'5432',PGDATABASE:u.pathname.slice(1),PGUSER:decodeURIComponent(u.username),PGPASSWORD:decodeURIComponent(u.password),PGSSLMODE:'verify-full',PGSSLROOTCERT:'/etc/ssl/certs/ca-certificates.crt',PGCONNECT_TIMEOUT:'15'};
for(const v of Object.values(variables))assert.ok(!/[\r\n]/.test(v));
writeFileSync(file,Object.entries(variables).map(([k,v])=>`${k}=${v}`).join('\n')+'\n',{flag:'wx'});
const base=['run','--rm','-i','--env-file',file];
const sql=query=>execFileSync('docker',[...base,'postgres:18-alpine','psql','-X','-At','-v','ON_ERROR_STOP=1','-c',query],{encoding:'utf8',maxBuffer:16*1024*1024}).trim();
try{
 assert.equal(Number(sql("SELECT count(*) FROM pg_tables WHERE schemaname NOT IN('pg_catalog','information_schema')")),0,'Destination is not empty; restore refused');
 if(checkOnly){const info=sql('\\conninfo');const protocol=info.match(/TLSv[0-9.]+/i)?.[0];assert.ok(protocol,'Client TLS could not be verified');const result={readOnlyConnectionVerified:true,destinationEmpty:true,tlsMode:'verify-full',tlsProtocol:protocol};writeFileSync('.local-cloud/neon-preflight.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result));process.exitCode=0;}
 else {
 assert.equal(sql("SELECT count(*) FROM pg_roles WHERE rolname='medifind_app'"),'0','Application role already exists; inspect before changing it');
 execFileSync('docker',[...base,'--mount',`type=bind,src=${resolve(report.dump)},dst=/backup.dump,readonly`,'postgres:18-alpine','pg_restore','--exit-on-error','--single-transaction','--no-owner','--no-privileges','--dbname',variables.PGDATABASE,'/backup.dump'],{maxBuffer:16*1024*1024});
 const comparisons=[];
 for(const t of report.frozenSnapshot){assert.match(t.table,/^[a-z_]+$/);const data=JSON.parse(sql(`SET TIME ZONE 'UTC'; SELECT json_build_object('count',count(*),'hash',md5(coalesce(string_agg(to_jsonb(t)::text,E'\\n' ORDER BY to_jsonb(t)::text COLLATE "C"),''))) FROM "${t.table}" t`).split('\n').filter(x=>x!=='SET').join('\n'));assert.equal(data.count,t.count,t.table);assert.equal(data.hash,t.hash,t.table);comparisons.push({table:t.table,count:data.count,matched:true});}
 const roleSql=`CREATE ROLE medifind_app LOGIN PASSWORD '${config.applicationPassword}' NOSUPERUSER NOCREATEDB NOCREATEROLE; GRANT CONNECT ON DATABASE "${variables.PGDATABASE.replaceAll('"','""')}" TO medifind_app; GRANT USAGE ON SCHEMA public TO medifind_app; GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO medifind_app; GRANT USAGE,SELECT,UPDATE ON ALL SEQUENCES IN SCHEMA public TO medifind_app; REVOKE INSERT,UPDATE,DELETE ON flyway_schema_history FROM medifind_app;`;
 execFileSync('docker',[...base,'postgres:18-alpine','psql','-X','-v','ON_ERROR_STOP=1'],{input:roleSql,encoding:'utf8'});
 const bytes=Number(sql('SELECT pg_database_size(current_database())'));
 writeFileSync('.local-cloud/neon-migration.json',JSON.stringify({at:new Date().toISOString(),projectId:config.projectId,database:variables.PGDATABASE,hostFingerprint:createHash('sha256').update(u.hostname).digest('hex'),tls:'verify-full',applicationRole:'medifind_app',bytes,comparisons,success:true},null,2));
 console.log(JSON.stringify({migrated:true,tablesCompared:comparisons.length,bytes,tls:'verify-full',credentials:'not printed'},null,2));
 }
}finally{unlinkSync(file);}
