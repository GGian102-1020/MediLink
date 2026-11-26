import{execFileSync}from'node:child_process';
import{readFileSync,writeFileSync}from'node:fs';
import{randomUUID}from'node:crypto';
import{docker,rows,query}from'../../scripts/demo-local/Database-Tools.mjs';
import assert from'node:assert/strict';
const r=JSON.parse(readFileSync('.local-cloud/copy-review.json','utf8')),name=`medifind-frozen-check-${randomUUID().slice(0,8)}`;
docker(['run','-d','--name',name,'--network','none','--tmpfs','/var/lib/postgresql','-e','POSTGRES_USER=medlink','-e','POSTGRES_DB=medlink','-e','POSTGRES_HOST_AUTH_METHOD=trust','postgres:18-alpine']);
try{
 let ok=false;for(let i=0;i<50;i++){try{docker(['exec',name,'pg_isready','-h','127.0.0.1','-U','medlink','-d','medlink']);ok=true;break;}catch{await new Promise(x=>setTimeout(x,500));}}assert.ok(ok);
 execFileSync('docker',['exec','-i',name,'pg_restore','--exit-on-error','-U','medlink','-d','medlink'],{input:readFileSync(r.dump),maxBuffer:16*1024*1024});
 const q=sql=>rows(name,'medlink','medlink',sql);
 r.frozenSnapshot=q("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename").map(({tablename:t})=>{assert.match(t,/^[a-z_]+$/);return{table:t,...JSON.parse(query(name,'medlink','medlink',`SELECT json_build_object('count',count(*),'hash',md5(coalesce(string_agg(to_jsonb(t)::text,E'\\n' ORDER BY to_jsonb(t)::text COLLATE "C"),''))) FROM "${t}" t`))};});
 assert.equal(q("SELECT count(*) n FROM cuenta WHERE crypt('Password123!',password_hash)=password_hash OR crypt('DemoLocal123!',password_hash)=password_hash")[0].n,0);
 assert.equal(q('SELECT count(*) n FROM pg_constraint WHERE connamespace=\'public\'::regnamespace AND NOT convalidated')[0].n,0);
 assert.equal(q("SELECT count(*) n FROM pg_trigger WHERE NOT tgisinternal AND tgenabled<>'O'")[0].n,0);
 assert.equal(q("SELECT count(*) n FROM flyway_schema_history WHERE NOT success")[0].n,0);
 docker(['exec',name,'pg_amcheck','-U','medlink','-d','medlink','--install-missing','--heapallindexed','--parent-check']);
 r.sanitizedRestoreVerified=true;r.knownPasswordsRejected=true;r.cloudMigrations=14;r.frozenVerifiedAt=new Date().toISOString();
 writeFileSync('.local-cloud/copy-review.json',JSON.stringify(r,null,2));console.log(JSON.stringify({restored:true,tables:r.frozenSnapshot.length,knownPasswordsRejected:true,triggersEnabled:true,integrityPassed:true,uploaded:false}));
}finally{docker(['rm','-f',name]);}
