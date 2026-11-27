// Full private pg_dump of the existing Neon database; never restores into Neon or local production.
import {readFileSync,writeFileSync,mkdirSync,unlinkSync,readdirSync,existsSync} from 'node:fs';
import {resolve} from 'node:path';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
const app=JSON.parse(readFileSync('.local-cloud/neon-app.private.json','utf8'));
assert.equal(app.projectId,'flat-shadow-76171117');assert.equal(app.user,'medifind_app');
const url=new URL(app.jdbc.replace(/^jdbc:/,''));
assert.equal(url.protocol,'postgresql:');assert.ok(url.hostname.endsWith('.neon.tech')&&!url.hostname.includes('-pooler'));
assert.equal(url.pathname,'/neondb');
const stamp=new Date().toISOString().replace(/[:.]/g,'-');
const dir=resolve('.local-cloud/backups');mkdirSync(dir,{recursive:true});
const verifyExisting=process.argv.includes('--verify-existing');
const existing=verifyExisting?readdirSync(dir).filter(x=>/^neon-before-geography-.*\.dump$/.test(x)).sort().at(-1):null;
assert.ok(!verifyExisting||existing,'No previous dump to verify');
const file=resolve(dir,existing||`neon-before-geography-${stamp}.dump`);
const privateEnv=resolve(dir,`connection-${stamp}.private.env`);
const variables={PGHOST:url.hostname,PGPORT:url.port||'5432',PGDATABASE:'neondb',PGUSER:app.user,PGPASSWORD:app.password,PGSSLMODE:'verify-full',PGSSLROOTCERT:'/etc/ssl/certs/ca-certificates.crt',PGCONNECT_TIMEOUT:'20'};
for(const value of Object.values(variables))assert.ok(!/[\r\n]/.test(value));
if(!verifyExisting)writeFileSync(privateEnv,Object.entries(variables).map(([k,v])=>`${k}=${v}`).join('\n')+'\n',{flag:'wx',mode:0o600});
try {
    const expectedTables=verifyExisting?null:Number(execFileSync('docker',['run','--rm','--env-file',privateEnv,'postgres:18-alpine','psql','-X','-At','--dbname','neondb','-v','ON_ERROR_STOP=1','-c',"SELECT count(*) FROM pg_tables WHERE schemaname='public'"],{encoding:'utf8',stdio:['ignore','pipe','pipe']}).trim());
    const dump=verifyExisting?readFileSync(file):execFileSync('docker',['run','--rm','--env-file',privateEnv,'postgres:18-alpine','pg_dump','--format=custom','--no-owner','--no-privileges','--dbname','neondb'],{maxBuffer:64*1024*1024,stdio:['ignore','pipe','pipe']});
    assert.equal(dump.subarray(0,5).toString(),'PGDMP');if(!verifyExisting)writeFileSync(file,dump,{flag:'wx',mode:0o600});
    const toc=execFileSync('docker',['run','--rm','--mount',`type=bind,src=${file},dst=/backup.dump,readonly`,'postgres:18-alpine','pg_restore','--list','/backup.dump'],{encoding:'utf8',maxBuffer:4*1024*1024,stdio:['ignore','pipe','pipe']});
    const tables=toc.split(/\r?\n/).filter(x=>/ TABLE DATA public /.test(x)).length;
    if(expectedTables!==null)assert.equal(tables,expectedTables,'Unexpected backup table inventory');
    else assert.ok(tables>=22,'Backup lacks the known cloud table inventory');
    const report={at:new Date().toISOString(),projectId:app.projectId,branchId:'br-small-fire-b6ptpcic',database:'neondb',file,bytes:dump.length,sha256:createHash('sha256').update(dump).digest('hex'),format:'custom',tables,tls:'verify-full',readOnlySource:true,archiveCatalogVerified:true};
    writeFileSync('.local-cloud/neon-geography-backup.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));
} finally {if(existsSync(privateEnv))unlinkSync(privateEnv);}
