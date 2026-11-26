import {execFileSync} from 'node:child_process';
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import {resolve} from 'node:path';
import assert from 'node:assert/strict';

const credentials=JSON.parse(readFileSync('.local-cloud/neon-app.private.json','utf8'));
assert.equal(credentials.projectId,'flat-shadow-76171117');
assert.equal(credentials.user,'medifind_app');
const jar=resolve('backend/target/medlink-backend-0.0.1-SNAPSHOT.jar');
const directory=resolve('.local-cloud/jdbc-preflight');
mkdirSync(directory,{recursive:true});
const libraries=execFileSync('jar',['tf',jar],{encoding:'utf8'}).split(/\r?\n/).filter(x=>/^BOOT-INF\/lib\/[^/]+\.jar$/.test(x));
assert.ok(libraries.some(x=>x.includes('postgresql-'))&&libraries.some(x=>x.includes('flyway-core-')));
execFileSync('jar',['xf',jar,...libraries],{cwd:directory});
const output=execFileSync('java',['-cp',resolve(directory,'BOOT-INF/lib/*'),'deploy/cloud/NeonPreflight.java'],{
    env:{...process.env,DB_URL:credentials.jdbc,DB_USER:credentials.user,DB_PASSWORD:credentials.password},
    encoding:'utf8',maxBuffer:1024*1024,stdio:['ignore','pipe','pipe']
});
assert.ok(output.includes('NEON_PREFLIGHT_OK:'));
const result={at:new Date().toISOString(),projectId:credentials.projectId,success:true,jdbcTlsVerifyFull:true,applicationRole:true,readOnly:true,flywayValidation:true,countsVerified:true};
writeFileSync('.local-cloud/neon-java-preflight.json',JSON.stringify(result,null,2));
console.log(JSON.stringify(result,null,2));
