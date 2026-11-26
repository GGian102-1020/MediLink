// Send the existing Neon application credentials directly to Render over HTTPS.
// No credential enters command arguments, stdout, error messages or reports.
import {readFileSync,writeFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import assert from 'node:assert/strict';

const require=createRequire(resolve('.local-cloud/schema-tools/package.json'));
const yaml=require('yaml');
const plan=JSON.parse(readFileSync('.local-cloud/render-plan.json','utf8'));
const billing=JSON.parse(readFileSync('.local-cloud/render-billing-check.json','utf8'));
const credentials=JSON.parse(readFileSync('.local-cloud/neon-app.private.json','utf8'));
const cli=yaml.parse(readFileSync('.local-cloud/render-cli-config/cli.yaml','utf8'));
const config=yaml.parse(readFileSync('deploy/cloud/render.yaml','utf8'));
const workspaceId='tea-dc459mk9v7es73dtm7j0';
assert.equal(billing.workspaceId,workspaceId);assert.equal(billing.plan,'Hobby');
assert.equal(billing.cardOnFile,false);assert.equal(billing.pendingCharges,0);assert.equal(billing.pipelineMonthlySpendLimit,0);
assert.ok(Date.now()-Date.parse(billing.at)<24*3600*1000,'Billing check is stale');
assert.equal(plan.blueprintId,'exs-dc47ahe7bikc738c8vtg');assert.equal(plan.plan,'free');assert.equal(plan.servicesToCreate,1);
assert.equal(config.services.length,1);assert.equal(config.services[0].plan,'free');
assert.equal(config.services[0].envVars.find(x=>x.key==='MEDLINK_RECETAS_ESCANEO_ENABLED').value,'false');
assert.equal(credentials.projectId,'flat-shadow-76171117');assert.equal(credentials.user,'medifind_app');
assert.match(credentials.password,/^[A-Za-z0-9_-]{32,80}$/);
assert.match(credentials.jdbc,/^jdbc:postgresql:\/\/[^/:]+\.neon\.tech:5432\/neondb\?sslmode=verify-full&sslfactory=org\.postgresql\.ssl\.DefaultJavaSSLFactory$/);
assert.ok(cli.api?.key,'Authorize the official Render CLI first');
async function request(path,body) {
    const response=await fetch('https://api.render.com/v1'+path,{
        method:body?'POST':'GET',headers:{Authorization:'Bearer '+cli.api.key,'Content-Type':'application/json'},
        body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(60000)
    });
    if(!response.ok)throw new Error('Render API request failed with HTTP '+response.status);
    return response.json();
}
try {
    const blueprint=await request('/blueprints/'+plan.blueprintId);
    assert.equal(blueprint.ownerId,workspaceId);assert.equal(blueprint.branch,'deploy/medifind-cloud');
    assert.equal(blueprint.repo,'https://github.com/GGian102-1020/MediLink');
    assert.equal(blueprint.status,'created');assert.equal(blueprint.resources.length,0);
    if(!process.argv.includes('--apply')) {
        console.log(JSON.stringify({privateConnectionReady:true,cliAuthorizationVerified:true,workspaceVerified:true,blueprintUnapplied:true,billingVerified:true,servicePlan:'free',externalOpenAiEnabled:false}));
    } else {
        const values={DB_URL:credentials.jdbc,DB_USER:credentials.user,DB_PASSWORD:credentials.password};
        const result=await request('/blueprints/'+plan.blueprintId+'/approve',{
            manualEnvVars:Object.entries(values).map(([key,value])=>({serviceName:plan.serviceName,serviceType:'web',key,value}))
        });
        const report={at:new Date().toISOString(),blueprintId:plan.blueprintId,workspaceId,approved:true,privateCredentialsSubmitted:true,externalOpenAiEnabled:false,syncState:result.sync?.state??result.state??'accepted'};
        writeFileSync('.local-cloud/render-apply.json',JSON.stringify(report,null,2));
        console.log(JSON.stringify(report));
    }
} catch(error) {
    // Deliberately omit exception details and response bodies, which can echo inputs.
    console.error('Private Render operation failed; inspect resource state through the connector before retrying.');
    process.exitCode=1;
}
