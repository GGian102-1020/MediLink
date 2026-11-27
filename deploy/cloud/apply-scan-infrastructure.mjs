// Accept only the owner password on non-echoed stdin; use the existing verified DIRECT app endpoint.
import {readFileSync,writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {resolve} from 'node:path';
import assert from 'node:assert/strict';
assert.ok(process.argv.includes('--apply'),'Explicit additive infrastructure application required');
const backup=JSON.parse(readFileSync('.local-cloud/neon-geography-backup.json','utf8'));
assert.equal(backup.projectId,'flat-shadow-76171117');assert.equal(backup.tables,22);
assert.ok(backup.archiveCatalogVerified&&Date.now()-Date.parse(backup.at)<3600000);
assert.equal(createHash('sha256').update(readFileSync(backup.file)).digest('hex'),backup.sha256);
const app=JSON.parse(readFileSync('.local-cloud/neon-app.private.json','utf8'));
if(process.stdin.isTTY)process.stdin.setRawMode(true);
console.log('Ready for private additive-schema input (not echoed).');
const input=await new Promise(done=>{let s='';process.stdin.setEncoding('utf8');process.stdin.on('data',chunk=>{s+=chunk;if(/[\r\n]/.test(s)){process.stdin.pause();done(s.split(/[\r\n]/)[0]);}});});
if(process.stdin.isTTY)process.stdin.setRawMode(false);
let stage='private-input-validation';
try {
    const config=JSON.parse(input);assert.equal(config.projectId,'flat-shadow-76171117');
    const expected=new URL(app.jdbc.replace(/^jdbc:/,''));
    assert.equal(expected.protocol,'postgresql:');assert.ok(expected.hostname.endsWith('.neon.tech')&&!expected.hostname.includes('-pooler'));
    assert.equal(expected.pathname,'/neondb');assert.equal(expected.searchParams.get('sslmode'),'verify-full');
    assert.equal(typeof config.ownerPassword,'string');assert.ok(config.ownerPassword.length>=12);
    stage='flyway-additive-migration';
    const output=execFileSync('java',['-cp',resolve('.local-cloud/backend-security/target/classes')+';'+resolve('.local-cloud/jdbc-preflight/BOOT-INF/lib/*'),'deploy/cloud/ApplyScanInfrastructure.java'],{
        env:{...process.env,DB_URL:app.jdbc,DB_USER:'neondb_owner',DB_PASSWORD:config.ownerPassword},
        encoding:'utf8',maxBuffer:1024*1024,stdio:['ignore','pipe','pipe']
    });
    const match=output.match(/SCAN_INFRASTRUCTURE_OK:(\d+)/);assert.ok(match);
    const report={at:new Date().toISOString(),projectId:config.projectId,migration:'15',migrationsApplied:Number(match[1]),flywayValidated:true,backupVerified:true,applicationRoleCanCreateTables:false,applicationLeasePrivileges:['SELECT','UPDATE'],businessDataRestored:false,success:true};
    writeFileSync('.local-cloud/scan-infrastructure-deploy.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));
} catch(error) {
    const safeCause=error.stderr?.toString().match(/SCAN_INFRASTRUCTURE_FAILED:([A-Za-z0-9_]+)/)?.[1]??error.name;
    console.error(JSON.stringify({failedStage:stage,errorType:safeCause,privateValuesOmitted:true}));process.exitCode=1;
}
