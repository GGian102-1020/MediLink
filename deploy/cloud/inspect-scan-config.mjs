// Render GET requests only; print non-secret configuration and presence booleans.
import {readFileSync,writeFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import assert from 'node:assert/strict';
const require=createRequire(resolve('.local-cloud/schema-tools/package.json'));
const cli=require('yaml').parse(readFileSync('.local-cloud/render-cli-config/cli.yaml','utf8'));
async function get(path){const r=await fetch('https://api.render.com/v1'+path,{headers:{Authorization:'Bearer '+cli.api.key},signal:AbortSignal.timeout(30000)});assert.ok(r.ok,'Render GET failed');return r.json();}
try{
    const id='srv-dc47mhajnfac73bpn770',service=await get('/services/'+id),raw=await get('/services/'+id+'/env-vars?limit=100');
    const env=Object.fromEntries(raw.map(x=>[x.envVar.key,x.envVar.value]));
    assert.equal(service.ownerId,'tea-dc459mk9v7es73dtm7j0');assert.equal(service.serviceDetails.plan,'free');
    assert.equal(env.MEDLINK_RECETAS_ESCANEO_ENABLED,'false');assert.ok(!env.OPENAI_API_KEY);
    const report={at:new Date().toISOString(),serviceId:id,plan:'free',instances:service.serviceDetails.numInstances,scanEnabled:false,openAiKeyPresent:false,model:env.OPENAI_MODEL,timeoutSeconds:Number(env.MEDLINK_RECETAS_ESCANEO_TIMEOUT_SECONDS??25),dailyAttemptLimit:Number(env.MEDIFIND_SCAN_DAILY_LIMIT??2),singleAuthorizedAccountConfigured:/^[1-9][0-9]{0,17}$/.test(env.MEDIFIND_SCAN_ALLOWED_ACCOUNT_IDS??''),scanMaxBytes:Number(env.MEDLINK_RECETAS_ESCANEO_MAX_BYTES??6291456),scanMaxBytesExplicitlyConfigured:env.MEDLINK_RECETAS_ESCANEO_MAX_BYTES!==undefined,readOnly:true};
    writeFileSync('.local-cloud/scan-config-inspection.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));
}catch{console.error('Read-only scan configuration inspection failed; secrets were not displayed.');process.exitCode=1;}
