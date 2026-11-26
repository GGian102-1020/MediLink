import {writeFileSync} from 'node:fs';
import assert from 'node:assert/strict';
const base=process.env.MEDIFIND_CLOUD_API_URL?.replace(/\/$/,'');
assert.equal(base,'https://medifind-api-ggian102-mey9.onrender.com');
const send=async(headers={})=>{
    const response=await fetch(base+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json',...headers},body:JSON.stringify({correo:'nobody@demo.medifind.test',contrasena:'invalid-test'}),signal:AbortSignal.timeout(120000)});
    await response.text();return response.status;
};
const second=new Date().getUTCSeconds();
if(second>45)await new Promise(resolve=>setTimeout(resolve,(61-second)*1000));
const attempts=await Promise.all(Array.from({length:12},()=>send()));
assert.ok(attempts.includes(429),'Rate limit was not reached');
const xForwarded=await send({'X-Forwarded-For':'192.0.2.123'});
const forwarded=await send({Forwarded:'for=192.0.2.124;proto=https'});
const report={at:new Date().toISOString(),rateLimitReached:true,xForwardedStatus:xForwarded,forwardedStatus:forwarded,passed:xForwarded===429&&forwarded===429,lastRequestAt:new Date().toISOString()};
writeFileSync('.local-cloud/auth-proxy-test.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));
assert.ok(report.passed,'Forwarded-header handling needs review before publication is declared secure');
