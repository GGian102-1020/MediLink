import {readFileSync,writeFileSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
const backup=JSON.parse(readFileSync('.local-cloud/neon-geography-backup.json','utf8'));
assert.equal(backup.projectId,'flat-shadow-76171117');
assert.equal(createHash('sha256').update(readFileSync(backup.file)).digest('hex'),backup.sha256);
const name='medifind-cloud-geography-backup-check';
const run=args=>execFileSync('docker',args,{encoding:'utf8',maxBuffer:4*1024*1024,stdio:['ignore','pipe','pipe']});
let created=false;
try {
    run(['run','-d','--name',name,'--label','medifind.cloud.review=true','--network','none','--tmpfs','/var/lib/postgresql','--mount',`type=bind,src=${backup.file},dst=/backup.dump,readonly`,'-e','POSTGRES_USER=medlink','-e','POSTGRES_DB=backup_check','-e','POSTGRES_HOST_AUTH_METHOD=trust','postgres:18-alpine']);created=true;
    for(let i=0;;i++){try{run(['exec',name,'pg_isready','-U','medlink','-d','backup_check']);break;}catch{assert.ok(i<60,'Backup verification PostgreSQL did not start');await new Promise(r=>setTimeout(r,500));}}
    run(['exec',name,'pg_restore','--exit-on-error','--single-transaction','--no-owner','--no-privileges','-U','medlink','--dbname','backup_check','/backup.dump']);
    const query=sql=>run(['exec',name,'psql','-X','-At','-v','ON_ERROR_STOP=1','-U','medlink','-d','backup_check','-c',sql]).trim();
    assert.equal(query("SELECT count(*) FROM pg_tables WHERE schemaname='public'"),'22');
    assert.equal(query("SELECT count(*) FROM pg_constraint WHERE conrelid IN (SELECT oid FROM pg_class WHERE relnamespace='public'::regnamespace) AND NOT convalidated"),'0');
    assert.equal(query("SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal AND tgenabled='D'"),'0');
    assert.equal(query('SELECT count(*) FROM farmacia WHERE latitud IS NULL AND longitud IS NULL'),'5');
    backup.restoredInIsolatedContainer=true;backup.foreignKeysAndConstraintsValidated=true;backup.userTriggersActive=true;backup.noProductionRestore=true;
    writeFileSync('.local-cloud/neon-geography-backup.json',JSON.stringify(backup,null,2));
    console.log(JSON.stringify({backupSha256:backup.sha256,restoredInIsolatedContainer:true,tables:22,constraintsValidated:true,productionUntouched:true}));
} finally {
    if(created){const info=JSON.parse(run(['inspect',name]))[0];assert.equal(info.Config.Labels['medifind.cloud.review'],'true');assert.equal(info.HostConfig.NetworkMode,'none');run(['rm','-f',name]);}
}
