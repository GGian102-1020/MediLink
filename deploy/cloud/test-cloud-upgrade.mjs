import{readFileSync,writeFileSync,unlinkSync,existsSync}from'node:fs';
import{execFileSync}from'node:child_process';
import{resolve}from'node:path';
import{createHash}from'node:crypto';
import assert from'node:assert/strict';
const id='ar.edu.uade.medilink.android.cloud',adb='C:/Users/gians/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const run=args=>execFileSync(adb,['-s','emulator-5554',...args],{encoding:'utf8',maxBuffer:4*1024*1024,stdio:['ignore','pipe','pipe']});
assert.equal(run(['shell','getprop','ro.kernel.qemu']).trim(),'1','Only the disposable emulator may be used');
const old=JSON.parse(readFileSync('.local-cloud/android-build.json','utf8'));
const next=JSON.parse(readFileSync('.local-cloud/android-build-rc2.json','utf8'));
for(const b of[old,next]){assert.equal(b.applicationId,id);assert.equal(createHash('sha256').update(readFileSync(b.path)).digest('hex'),b.sha256);}
const health=await fetch(new URL('api/health',next.url),{signal:AbortSignal.timeout(240000)});
assert.equal(health.status,200);assert.equal(await health.text(),'MediLink API funcionando');
const account=JSON.parse(readFileSync('.local-cloud/credentials.private.json','utf8')).find(x=>x.rol==='USUARIO');
const input=resolve('.local-cloud/android-upgrade.private.json'),remote='/sdcard/Android/data/'+id+'/files/cloud-test.private.json';
const report={at:new Date().toISOString(),emulator:true,rc1Sha256:old.sha256,rc2Sha256:next.sha256,passed:false};
try{
    // Reset only the emulator fixture before preparing the rc1 session.
    run(['uninstall',id]);run(['install',resolve(old.path)]);
    run(['install','-r',resolve('.local-cloud/android/app/build/outputs/apk/androidTest/cloud/app-cloud-androidTest.apk')]);
    run(['shell','mkdir','-p',remote.slice(0,remote.lastIndexOf('/'))]);
    writeFileSync(input,JSON.stringify({email:account.email,password:account.password}),{flag:'wx',mode:0o600});run(['push',input,remote]);
    const instrument=name=>{
        const out=run(['shell','am','instrument','-w','-r','-e','class','ar.edu.uade.medilink.android.cloudtest.'+name,id+'.test/androidx.test.runner.AndroidJUnitRunner']);
        writeFileSync('.local-cloud/'+name+'.log',out.replaceAll(account.email,'[academic account]').replaceAll(account.password,'[redacted]'));
        assert.ok(out.includes('OK (1 test)'),name+' failed; see private log');
    };
    instrument('CloudUpgradeBeforeTest');
    // The actual update: no uninstall, pm clear, permission reset, or login between these steps.
    run(['install','-r',resolve(next.path)]);
    instrument('CloudUpgradeAfterTest');
    run(['pull','/sdcard/Android/data/'+id+'/files/cloud-upgrade-result.json',resolve('.local-cloud/cloud-upgrade-result.json')]);
    Object.assign(report,JSON.parse(readFileSync('.local-cloud/cloud-upgrade-result.json','utf8')));
    const installed=run(['shell','pm','path',id]).trim().split(/\r?\n/)[0].replace(/^package:/,'');
    run(['pull',installed,resolve('.local-cloud/installed-upgrade-rc2.apk')]);
    assert.equal(createHash('sha256').update(readFileSync('.local-cloud/installed-upgrade-rc2.apk')).digest('hex'),next.sha256);
    report.installedBytesMatch=true;report.passed=true;
}catch(error){report.error=error.message;process.exitCode=1;}
finally{if(existsSync(input))unlinkSync(input);try{run(['shell','rm','-f',remote]);}catch{}writeFileSync('.local-cloud/cloud-upgrade-tests.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));}
