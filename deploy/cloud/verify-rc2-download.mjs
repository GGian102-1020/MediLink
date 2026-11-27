import {readFileSync,writeFileSync,copyFileSync,existsSync,statSync} from 'node:fs';
import {basename} from 'node:path';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
const build=JSON.parse(readFileSync('.local-cloud/android-build-rc2.json','utf8'));
assert.equal(build.applicationId,'ar.edu.uade.medilink.android.cloud');assert.equal(build.versionCode,4);
const asset=basename(build.path);assert.match(asset,/^MediFind-cloud-1\.0\.0-rc\.2-[a-f0-9]{12}\.apk$/);
const url='https://github.com/GGian102-1020/MediLink/releases/download/cloud-v1.0.0-rc.2/'+asset;
const response=await fetch(url,{signal:AbortSignal.timeout(180000)});assert.equal(response.status,200);
const bytes=Buffer.from(await response.arrayBuffer());const sha256=createHash('sha256').update(bytes).digest('hex');
assert.equal(sha256,build.sha256);assert.equal(bytes.length,statSync(build.path).size);
const checksum=await fetch(url+'.sha256',{signal:AbortSignal.timeout(60000)});assert.equal(checksum.status,200);
assert.equal((await checksum.text()).trim(),sha256+'  '+asset);
if(existsSync('.local-cloud/release-download-check.json')&&!existsSync('.local-cloud/release-download-rc1.json')){
    const old=JSON.parse(readFileSync('.local-cloud/release-download-check.json','utf8'));
    if(old.url.includes('/cloud-v1.0.0-rc.1/'))copyFileSync('.local-cloud/release-download-check.json','.local-cloud/release-download-rc1.json');
}
writeFileSync('.local-cloud/downloaded-cloud-rc2.apk',bytes);
const report={at:new Date().toISOString(),url,bytes:bytes.length,sha256,anonymousDownloadVerified:true,publishedChecksumVerified:true};
writeFileSync('.local-cloud/release-download-check.json',JSON.stringify(report,null,2));
console.log(JSON.stringify(report,null,2));
