import {readFileSync,writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {createRequire} from 'node:module';
import assert from 'node:assert/strict';
const require=createRequire(resolve('.local-cloud/browser-tests/package.json'));
const {chromium}=require('playwright');
const api='https://medifind-api-ggian102-mey9.onrender.com';
const operatorUrl='https://medifind-operador-ggian102.pages.dev';
const adminUrl='https://medifind-admin-ggian102.pages.dev';
const accounts=JSON.parse(readFileSync('.local-cloud/credentials.private.json','utf8'));
const report={at:new Date().toISOString(),checks:[],success:false};let stage='launch';
const browser=await chromium.launch({channel:'chrome',headless:true});
const note=name=>{report.checks.push({name,passed:true});console.log(name);};
async function login(page,origin,role) {
    const account=accounts.find(x=>x.rol===role);
    await page.goto(origin+'/ingresar');await page.locator('input[type=email]').fill(account.email);
    await page.locator('input[type=password]').fill(account.password);
    const reply=page.waitForResponse(r=>r.url()===api+'/api/auth/login'&&r.request().method()==='POST',{timeout:240000});
    await page.locator('form button[type=submit]').click();
    const response=await reply;assert.equal(response.status(),200,'Web login did not succeed');
    return (await response.json()).accessToken;
}
async function status(page,token,path) {
    return page.evaluate(async({api,token,path})=>{const response=await fetch(api+path,{headers:{Authorization:'Bearer '+token}});await response.text();return response.status;},{api,token,path});
}
try {
    stage='operator login and CORS';const opContext=await browser.newContext({viewport:{width:1440,height:1000}});const op=await opContext.newPage();
    const opToken=await login(op,operatorUrl,'OPERADOR');await op.waitForURL('**/panel',{timeout:60000});
    await op.goto(operatorUrl+'/farmacias/2/stock');await op.getByRole('heading',{name:/stock/i}).first().waitFor();
    assert.equal(await status(op,opToken,'/api/farmacias/2/stock'),200);
    assert.equal(await status(op,opToken,'/api/farmacias/1/stock'),403);
    assert.equal(await status(op,opToken,'/api/admin/usuarios'),403);
    await op.screenshot({path:resolve('.local-cloud/operator-cloud.png'),fullPage:true});
    note('Operator login, real CORS, own pharmacy stock and server-side rejection of another pharmacy/admin');
    stage='operator URL tampering';await op.goto(operatorUrl+'/admin/usuarios');await op.waitForURL('**/panel');note('Operator cannot open administrative route by URL');
    stage='administrator login';const adminContext=await browser.newContext({viewport:{width:1440,height:1000}});const admin=await adminContext.newPage();
    const adminToken=await login(admin,adminUrl,'ADMINISTRADOR');await admin.waitForURL('**/panel');
    await admin.goto(adminUrl+'/admin/farmacias');await admin.getByRole('heading',{name:/farmacias/i}).first().waitFor();
    assert.equal(await status(admin,adminToken,'/api/admin/farmacias'),200);
    await admin.screenshot({path:resolve('.local-cloud/admin-cloud.png'),fullPage:true});note('Administrator login and real pharmacy administration');
    stage='patient denied by operator interface';const patientContext=await browser.newContext();const patient=await patientContext.newPage();
    await login(patient,operatorUrl,'USUARIO');await patient.getByText('Esta web es para farmacias.',{exact:false}).waitFor({timeout:60000});note('Patient account rejected by operator interface');
    stage='CORS negative test';const rejected=await fetch(api+'/api/health',{headers:{Origin:'https://untrusted.test'}});assert.equal(rejected.status,403);note('Unapproved origin remains rejected');
    report.success=true;
} catch(error) {report.failedStage=stage;report.errorType=error.name;process.exitCode=1;}
finally {await browser.close();writeFileSync('.local-cloud/pages-ui-tests.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));}
