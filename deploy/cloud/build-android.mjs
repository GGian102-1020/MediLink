import {cpSync,existsSync,mkdirSync,readFileSync,writeFileSync,rmSync} from 'node:fs';
import {resolve} from 'node:path';
import {spawnSync,execFileSync} from 'node:child_process';
import {randomBytes,createHash} from 'node:crypto';
import {applyCapturePreview} from './android-capture-preview.mjs';
import {applyAvailabilityFirst} from './android-availability-first.mjs';
const rc3=process.argv.includes('--candidate-rc3');
const candidate=rc3||process.argv.includes('--candidate-rc2');
const number=rc3?3:candidate?2:1;
const url=process.env.MEDIFIND_CLOUD_API_URL||'',u=new URL(url);
if(u.protocol!=='https:'||!u.hostname.endsWith('.onrender.com')||u.username||u.password||u.search||u.hash||u.pathname!=='/')throw Error('Use the verified Render URL ending in /');
const health=await fetch(url+'api/health',{signal:AbortSignal.timeout(240000)});
if(health.status!==200||(await health.text())!=='MediLink API funcionando')throw Error('Cloud backend did not pass its real health check; no APK built');
const stage=resolve('.local-cloud/android');mkdirSync(stage,{recursive:true});
for(const name of ['app','gradle','gradlew','gradlew.bat','build.gradle.kts','settings.gradle.kts','gradle.properties','local.properties'])if(existsSync(`android/${name}`))cpSync(`android/${name}`,`${stage}/${name}`,{recursive:true,filter:p=>!p.split(/[\\/]/).some(x=>['build','.gradle'].includes(x))});
const repository=`${stage}/app/src/main/java/ar/edu/uade/medilink/android/data/MediLinkRepository.kt`;
let code=readFileSync(repository,'utf8');if(!code.includes('.readTimeout(20, TimeUnit.SECONDS)'))throw Error('Unexpected Android source; review connectivity adaptation');
code=code.replace('.connectTimeout(15, TimeUnit.SECONDS)','.connectTimeout(30, TimeUnit.SECONDS)').replace('.readTimeout(20, TimeUnit.SECONDS)','.readTimeout(240, TimeUnit.SECONDS)');writeFileSync(repository,code);
if(candidate){
 applyCapturePreview(stage);
 if(rc3)applyAvailabilityFirst(stage);
 const testRoot=`${stage}/app/src/androidTest/java/ar/edu/uade/medilink/android`;
 mkdirSync(testRoot+'/cloudtest',{recursive:true});
 for(const name of ['CloudSmokeTest.kt','CloudMapTest.kt','CloudCaptureTest.kt','CloudLocationTest.kt','CloudRequestProbe.kt','CloudUpgradeTest.kt',...(rc3?['CloudAvailabilityFirstTest.kt']:[])]) {
   // Remove only the previous staging copy of this exact file; original Android is never edited.
   if(existsSync(testRoot+'/'+name))rmSync(testRoot+'/'+name);
   cpSync(`deploy/cloud/${name}`,testRoot+'/cloudtest/'+name);
 }
}
mkdirSync('.local-cloud/signing',{recursive:true});const keyFile=resolve('.local-cloud/signing/medifind-cloud.p12'),secretFile='.local-cloud/signing/private.json';
let password;if(existsSync(secretFile)){password=JSON.parse(readFileSync(secretFile,'utf8')).password;}else{password=randomBytes(32).toString('base64url');writeFileSync(secretFile,JSON.stringify({password}),{flag:'wx'});}
const env={...process.env,MEDIFIND_CLOUD_API_URL:url,MEDIFIND_CLOUD_KEYSTORE:keyFile,MEDIFIND_CLOUD_KEY_PASSWORD:password,MEDIFIND_CLOUD_CANDIDATE:rc3?'rc3':candidate?'rc2':''};
if(!existsSync(keyFile))execFileSync('keytool',['-genkeypair','-keystore',keyFile,'-storetype','PKCS12','-storepass:env','MEDIFIND_CLOUD_KEY_PASSWORD','-keypass:env','MEDIFIND_CLOUD_KEY_PASSWORD','-alias','medifind-cloud','-keyalg','RSA','-keysize','3072','-validity','3650','-dname','CN=MediFind Cloud, O=Proyecto Academico, C=AR','-noprompt'],{env,stdio:'pipe'});
const result=spawnSync('cmd.exe',['/d','/c','gradlew.bat','-I',resolve('deploy/cloud/android-cloud.init.gradle'),'--no-daemon',':app:assembleCloud',':app:testCloudUnitTest',...(candidate?[':app:assembleCloudAndroidTest']:[])],{cwd:stage,env,stdio:'inherit'});if(result.status)throw Error('Android cloud build/tests failed');
const apk=readFileSync(`${stage}/app/build/outputs/apk/cloud/app-cloud.apk`),sha256=createHash('sha256').update(apk).digest('hex');
mkdirSync('.local-cloud/artifacts',{recursive:true});const path=`.local-cloud/artifacts/MediFind-cloud-1.0.0-rc.${number}-${sha256.slice(0,12)}.apk`;writeFileSync(path,apk);
writeFileSync(candidate?`.local-cloud/android-build-rc${number}.json`:'.local-cloud/android-build.json',JSON.stringify({url,path,sha256,applicationId:'ar.edu.uade.medilink.android.cloud',version:`1.0.0-cloud.${number}`,versionCode:number+2,localSourceUntouched:true,published:false,cloudOnlyChanges:['connectTimeout 15→30s','readTimeout 20→240s','cloud build identity and private signing',...(candidate?['local capture/selection preview while external analysis is disabled; upload remains blocked','reuse approximate location permission']:[]),...(rc3?['availability before prescription validation; reservation remains gated']:[])]},null,2));console.log(`Built ${path}; signature/install validation and release publication remain mandatory.`);
