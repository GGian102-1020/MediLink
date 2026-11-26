import {readFileSync,writeFileSync,unlinkSync,existsSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {resolve} from 'node:path';
import assert from 'node:assert/strict';
const adb='C:/Users/gians/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const build=JSON.parse(readFileSync('.local-cloud/android-build.json','utf8'));
assert.equal(build.applicationId,'ar.edu.uade.medilink.android.cloud');
const account=JSON.parse(readFileSync('.local-cloud/credentials.private.json','utf8')).find(x=>x.rol==='USUARIO');
assert.equal(createHash('sha256').update(readFileSync(build.path)).digest('hex'),build.sha256);
const privateInput=resolve('.local-cloud/android-ui.private.json');
const remote='/sdcard/Android/data/ar.edu.uade.medilink.android.cloud/files/cloud-test.private.json';
const run=args=>execFileSync(adb,['-s','emulator-5554',...args],{encoding:'utf8',maxBuffer:8*1024*1024});
const report={at:new Date().toISOString(),emulator:true,physicalPhone:false,apkSha256:build.sha256,checks:[],passed:false};
try {
    assert.match(run(['shell','getprop','sys.boot_completed']),/1/);
    run(['install','-r',resolve(build.path)]);
    // Only this disposable emulator fixture; never clear/uninstall the LAN package.
    run(['shell','pm','clear','ar.edu.uade.medilink.android.cloud']);
    run(['install','-r',resolve('.local-cloud/android/app/build/outputs/apk/androidTest/cloud/app-cloud-androidTest.apk')]);
    report.checks.push('cloud app and matching test APK installed');
    run(['shell','mkdir','-p',remote.slice(0,remote.lastIndexOf('/'))]);
    writeFileSync(privateInput,JSON.stringify({email:account.email,password:account.password}),{flag:'wx',mode:0o600});
    run(['push',privateInput,remote]);
    const output=run(['shell','am','instrument','-w','-r','-e','class','ar.edu.uade.medilink.android.cloudtest.CloudSmokeTest','ar.edu.uade.medilink.android.cloud.test/androidx.test.runner.AndroidJUnitRunner']);
    const redacted=output.replaceAll(account.password,'[redacted]').replaceAll(account.email,'[academic account]');
    writeFileSync('.local-cloud/android-instrumentation.log',redacted);
    const failed=redacted.match(/Cloud smoke failed at step: [^\r\n]+/)?.[0];
    assert.ok(/OK \(1 test\)/.test(output),failed||'Cloud instrumentation did not pass; see private test log');
    report.checks.push('guest catalog and availability from Render','real login and account UI','session retained after activity recreation','reservations navigation','logout');
    const installed=run(['shell','pm','path',build.applicationId]).trim().split(/\r?\n/)[0].replace(/^package:/,'');
    run(['pull',installed,resolve('.local-cloud/installed-cloud.apk')]);
    assert.equal(createHash('sha256').update(readFileSync('.local-cloud/installed-cloud.apk')).digest('hex'),build.sha256);
    report.checks.push('installed APK exactly matches the release candidate bytes');report.passed=true;
} catch(error) {report.error=error.message;process.exitCode=1;}
finally {if(existsSync(privateInput))unlinkSync(privateInput);try{run(['shell','rm','-f',remote]);}catch{};writeFileSync('.local-cloud/android-ui-tests.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));}
