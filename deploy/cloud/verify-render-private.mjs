import {readFileSync,writeFileSync,unlinkSync,existsSync} from 'node:fs';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import assert from 'node:assert/strict';
const require=createRequire(resolve('.local-cloud/schema-tools/package.json'));
const cli=require('yaml').parse(readFileSync('.local-cloud/render-cli-config/cli.yaml','utf8'));
const app=JSON.parse(readFileSync('.local-cloud/neon-app.private.json','utf8'));
const id='srv-dc47mhajnfac73bpn770';
async function get(path) {
    const response=await fetch('https://api.render.com/v1'+path,{headers:{Authorization:'Bearer '+cli.api.key},signal:AbortSignal.timeout(30000)});
    assert.ok(response.ok,'Render read failed');return response.json();
}
try {
    const service=await get('/services/'+id);
    assert.equal(service.ownerId,'tea-dc459mk9v7es73dtm7j0');assert.equal(service.serviceDetails.plan,'free');
    assert.equal(service.serviceDetails.numInstances,1);assert.equal(service.serviceDetails.healthCheckPath,'/api/health');
    const raw=await get('/services/'+id+'/env-vars?limit=100');
    const env=Object.fromEntries(raw.map(x=>[x.envVar.key,x.envVar.value]));
    assert.equal(env.DB_URL,app.jdbc);assert.equal(env.DB_USER,app.user);assert.equal(env.DB_PASSWORD,app.password);
    assert.ok(Buffer.from(env.JWT_SECRET_BASE64,'base64').length>=32);
    assert.equal(env.MEDLINK_RECETAS_ESCANEO_ENABLED,'false');assert.ok(!env.OPENAI_API_KEY);assert.equal(env.MEDLINK_CORS_ALLOWED_ORIGINS,'');
    const report={at:new Date().toISOString(),serviceId:id,url:service.serviceDetails.url,plan:'free',instances:1,healthCheck:'/api/health',neonApplicationCredentialsMatch:true,privateJwtConfigured:true,externalAiDisabled:true,openAiKeyAbsent:true,browserOriginsClosed:true,success:true};
    writeFileSync('.local-cloud/render-security-config.json',JSON.stringify(report,null,2));
    if(existsSync('.local-cloud/render-runtime.private.env'))unlinkSync('.local-cloud/render-runtime.private.env');
    console.log(JSON.stringify(report,null,2));
} catch {
    console.error('Render private configuration verification failed; values were not displayed.');process.exitCode=1;
}
