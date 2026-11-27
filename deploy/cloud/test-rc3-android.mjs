import {readFileSync,writeFileSync,unlinkSync,existsSync} from 'node:fs';
import {resolve} from 'node:path';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
const adb='C:/Users/gians/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const run=args=>execFileSync(adb,['-s','emulator-5554',...args],{encoding:'utf8',maxBuffer:8*1024*1024,stdio:['ignore','pipe','pipe']});
assert.equal(run(['shell','getprop','ro.kernel.qemu']).trim(),'1','Only the disposable emulator is allowed');
const build=JSON.parse(readFileSync('.local-cloud/android-build-rc3.json','utf8'));
const previous=JSON.parse(readFileSync('.local-cloud/android-build-rc2.json','utf8'));
for(const b of [build,previous]) {
    assert.equal(b.applicationId,'ar.edu.uade.medilink.android.cloud');
    assert.equal(createHash('sha256').update(readFileSync(b.path)).digest('hex'),b.sha256);
}
assert.equal(build.published,false);assert.equal(build.versionCode,5);
const account=JSON.parse(readFileSync('.local-cloud/credentials.private.json','utf8')).find(x=>x.rol==='USUARIO');
const input=resolve('.local-cloud/rc3-input.private.json');
const remote='/sdcard/Android/data/'+build.applicationId+'/files/cloud-test.private.json';
const report={at:new Date().toISOString(),apkSha256:build.sha256,emulator:true,physicalPhone:false,published:false,reservationRequests:0,documentUploads:0,passed:false,cases:[]};
function credentials() {
    if(existsSync(input))unlinkSync(input);
    writeFileSync(input,JSON.stringify({email:account.email,password:account.password}),{flag:'wx',mode:0o600});
    run(['shell','mkdir','-p',remote.slice(0,remote.lastIndexOf('/'))]);run(['push',input,remote]);
}
function instrument(name,classes,count) {
    const raw=run(['shell','am','instrument','-w','-r','-e','fromVersionCode','4','-e','toVersionCode','5','-e','class',classes,build.applicationId+'.test/androidx.test.runner.AndroidJUnitRunner']);
    writeFileSync('.local-cloud/android-rc3-'+name+'.log',raw.replaceAll(account.email,'[academic account]').replaceAll(account.password,'[redacted]'));
    const evidence={name,tests:count,passed:raw.includes(`OK (${count} ${count===1?'test':'tests'})`)};
    report.cases.push(evidence);console.log(JSON.stringify(evidence));
    writeFileSync('.local-cloud/android-rc3-tests.json',JSON.stringify(report,null,2));
    assert.ok(evidence.passed,'Private rc.3 instrumentation log requires review');
}
try {
    run(['install','-r',resolve('.local-cloud/android/app/build/outputs/apk/androidTest/cloud/app-cloud-androidTest.apk')]);
    const only=process.argv.find(x=>x.startsWith('--only='))?.slice(7);
    if(!only||only==='upgrade') {
        // Disposable emulator only: create a clean rc.2 fixture even if a previous test installed rc.3.
        try{run(['uninstall',build.applicationId]);}catch{}
        run(['install','-r',resolve(previous.path)]);run(['shell','pm','clear',build.applicationId]);credentials();
        run(['install','-r',resolve('.local-cloud/android/app/build/outputs/apk/androidTest/cloud/app-cloud-androidTest.apk')]);
        instrument('upgrade-before','ar.edu.uade.medilink.android.cloudtest.CloudUpgradeBeforeTest',1);
        run(['install','-r',resolve(build.path)]);
        instrument('upgrade-after','ar.edu.uade.medilink.android.cloudtest.CloudUpgradeAfterTest',1);
    }
    const cases=[
        ['prescription','CloudAvailabilityFirstTest#prescriptionAvailabilityAndMapComeBeforeValidationButReservationRemainsLocked',1],
        ['otc','CloudAvailabilityFirstTest#overTheCounterReservationActionIsUnchangedAndNotSubmitted',1],
        ['capture','CloudCaptureTest',1],['location','CloudLocationTest',1],['smoke','CloudSmokeTest',1],
    ];
    if(only)assert.ok(only==='upgrade'||cases.some(c=>c[0]===only));
    for(const [name,cls,n] of cases.filter(c=>!only||c[0]===only)) {
        run(['install','-r',resolve(build.path)]);run(['shell','pm','clear',build.applicationId]);credentials();
        if(name==='location') {
            run(['shell','appops','set',build.applicationId,'android:mock_location','allow']);
            run(['shell','cmd','location','set-location-enabled','true']);
        }
        instrument(name,'ar.edu.uade.medilink.android.cloudtest.'+cls,n);
        if(name==='capture')run(['pull','/sdcard/Android/data/'+build.applicationId+'/files/cloud-capture-network.json',resolve('.local-cloud/rc3-capture-network.json')]);
    }
    const installed=run(['shell','pm','path',build.applicationId]).trim().split(/\r?\n/)[0].replace(/^package:/,'');
    const local=resolve('.local-cloud/installed-rc3.apk');run(['pull',installed,local]);
    assert.equal(createHash('sha256').update(readFileSync(local)).digest('hex'),build.sha256);
    report.installedBytesMatch=true;report.passed=true;
}catch{report.error='Private emulator checks require review; credentials omitted';process.exitCode=1;}
finally {
    if(existsSync(input))unlinkSync(input);try{run(['shell','rm','-f',remote]);}catch{}
    for(const args of [['shell','appops','set',build.applicationId,'android:mock_location','default'],['shell','pm','revoke',build.applicationId,'android.permission.ACCESS_FINE_LOCATION'],['shell','pm','revoke',build.applicationId,'android.permission.ACCESS_COARSE_LOCATION']])try{run(args);}catch{}
    writeFileSync('.local-cloud/android-rc3-tests'+(process.argv.find(x=>x.startsWith('--only='))?'-'+process.argv.find(x=>x.startsWith('--only=')).slice(7):'')+'.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));
}
