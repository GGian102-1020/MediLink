import {cpSync,mkdirSync,existsSync,readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
const stage='.local-cloud/backend-security';
assert.ok(!process.env.MEDLINK_TEST_DB_URL,'Tests must use disposable Testcontainers, never an external database');
mkdirSync(stage,{recursive:true});
for(const name of ['pom.xml','mvnw','mvnw.cmd','.mvn','src'])if(existsSync('backend/'+name))
    cpSync('backend/'+name,stage+'/'+name,{recursive:true});
cpSync('deploy/cloud/backend',stage+'/src',{recursive:true});
cpSync('database','.local-cloud/database',{recursive:true});
assert.ok(readFileSync(stage+'/src/main/resources/application-cloud.properties','utf8').includes('MEDIFIND_SCAN_DAILY_LIMIT:2'));
console.log('Prepared isolated backend test source with cloud overlays; no database connections made.');
