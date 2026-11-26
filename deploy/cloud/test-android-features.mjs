import {readFileSync,writeFileSync,unlinkSync,existsSync} from 'node:fs';
import {resolve} from 'node:path';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
const mode=process.argv.includes('--map-rc1')?'map-rc1':process.argv.includes('--capture-rc2')?'capture-rc2':null;
assert.ok(mode,'Choose --map-rc1 or --capture-rc2');
const adb='C:/Users/gians/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const run=args=>execFileSync(adb,['-s','emulator-5554',...args],{encoding:'utf8',maxBuffer:8*1024*1024,stdio:['ignore','pipe','pipe']});
assert.equal(run(['shell','getprop','ro.kernel.qemu']).trim(),'1','Only the disposable emulator fixture is allowed');
const build=JSON.parse(readFileSync(mode==='map-rc1'?'.local-cloud/android-build.json':'.local-cloud/android-build-rc2.json','utf8'));
assert.equal(build.applicationId,'ar.edu.uade.medilink.android.cloud');
assert.equal(createHash('sha256').update(readFileSync(build.path)).digest('hex'),build.sha256);
const account=JSON.parse(readFileSync('.local-cloud/credentials.private.json','utf8')).find(x=>x.rol==='USUARIO');
const input=resolve('.local-cloud/android-features.private.json');
const remote='/sdcard/Android/data/ar.edu.uade.medilink.android.cloud/files/cloud-test.private.json';
const report={at:new Date().toISOString(),mode,emulator:true,physicalPhone:false,apkSha256:build.sha256,passed:false};
try{
    run(['install','-r',resolve(build.path)]);
    run(['shell','pm','clear',build.applicationId]); // Never runs on a physical phone or LAN app.
    run(['install','-r',resolve('.local-cloud/android/app/build/outputs/apk/androidTest/cloud/app-cloud-androidTest.apk')]);
    const cases=mode==='map-rc1'?[{name:'map',classes:'ar.edu.uade.medilink.android.cloudtest.CloudMapTest,ar.edu.uade.medilink.android.data.ArchivosRecetaAndroidTest',tests:3}]:[
        {name:'capture',classes:'ar.edu.uade.medilink.android.cloudtest.CloudCaptureTest',tests:1},
        {name:'location',classes:'ar.edu.uade.medilink.android.cloudtest.CloudLocationTest',tests:1},
        {name:'smoke',classes:'ar.edu.uade.medilink.android.cloudtest.CloudSmokeTest',tests:1},
    ];
    const only=process.argv.find(x=>x.startsWith('--only='))?.slice(7);
    assert.ok(!only||cases.some(x=>x.name===only));report.cases=[];
    for(const test of cases.filter(x=>!only||x.name===only)){
        run(['shell','pm','clear',build.applicationId]);
        if(test.name==='location'){
            run(['shell','appops','set',build.applicationId,'android:mock_location','allow']);
            run(['shell','cmd','location','set-location-enabled','true']);
        }
        if(mode==='capture-rc2'&&test.name!=='location'){
            run(['shell','mkdir','-p',remote.slice(0,remote.lastIndexOf('/'))]);
            if(existsSync(input))unlinkSync(input);
            writeFileSync(input,JSON.stringify({email:account.email,password:account.password}),{flag:'wx',mode:0o600});run(['push',input,remote]);
        }
        const output=run(['shell','am','instrument','-w','-r','-e','class',test.classes,build.applicationId+'.test/androidx.test.runner.AndroidJUnitRunner']);
        const redacted=output.replaceAll(account.email,'[academic account]').replaceAll(account.password,'[redacted]');
        writeFileSync(`.local-cloud/android-${mode}-${test.name}.log`,redacted);
        const passed=output.includes(`OK (${test.tests} ${test.tests===1?'test':'tests'})`);
        const evidence={at:new Date().toISOString(),name:test.name,apkSha256:build.sha256,passed,tests:test.tests};
        writeFileSync(`.local-cloud/android-${mode}-${test.name}.json`,JSON.stringify(evidence,null,2));report.cases.push(evidence);console.log(JSON.stringify(evidence));
        if(passed&&(test.name==='map'||test.name==='capture')){
            const image=test.name==='map'?'cloud-map-verified.png':'cloud-capture-preview.png';
            run(['pull','/sdcard/Android/data/'+build.applicationId+'/files/'+image,resolve('.local-cloud/'+image)]);
        }
    }
    assert.ok(report.cases.every(x=>x.passed),'Feature instrumentation did not pass; inspect the private case logs');
    const installed=run(['shell','pm','path',build.applicationId]).trim().split(/\r?\n/)[0].replace(/^package:/,'');
    const installedFile=resolve(`.local-cloud/installed-${mode}.apk`);run(['pull',installed,installedFile]);
    assert.equal(createHash('sha256').update(readFileSync(installedFile)).digest('hex'),build.sha256);
    report.installedBytesMatch=true;report.tests=report.cases.reduce((n,x)=>n+x.tests,0);report.passed=true;
}catch(error){report.error=error.message;process.exitCode=1;}
finally{
    if(existsSync(input))unlinkSync(input);try{run(['shell','rm','-f',remote]);}catch{}
    if(mode==='capture-rc2')for(const args of [
        ['shell','appops','set',build.applicationId,'android:mock_location','default'],
        ['shell','pm','revoke',build.applicationId,'android.permission.ACCESS_FINE_LOCATION'],
        ['shell','pm','revoke',build.applicationId,'android.permission.ACCESS_COARSE_LOCATION'],
    ])try{run(args);}catch{}
    writeFileSync(`.local-cloud/android-${mode}-tests.json`,JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));
}
