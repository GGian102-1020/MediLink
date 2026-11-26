import {readFileSync,writeFileSync} from 'node:fs';
import assert from 'node:assert/strict';
const base=process.env.MEDIFIND_CLOUD_API_URL?.replace(/\/$/,'');
assert.equal(base,'https://medifind-api-ggian102-mey9.onrender.com');
const accounts=JSON.parse(readFileSync('.local-cloud/credentials.private.json','utf8'));
const checks=[];
async function request(path,token,options={}) {
    const response=await fetch(base+path,{...options,headers:{...(token?{Authorization:'Bearer '+token}:{}),...options.headers},signal:AbortSignal.timeout(240000)});
    const text=await response.text();assert.equal(response.status,options.expect??200,path);
    if(path==='/api/health')assert.equal(text,'MediLink API funcionando');
    try{return JSON.parse(text);}catch{return text;}
}
const tokens={};
for(const role of ['USUARIO','OPERADOR','ADMINISTRADOR']) {
    const account=accounts.find(x=>x.rol===role);
    const result=await request('/api/auth/login',null,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({correo:account.email,contrasena:account.password})});
    tokens[role]=result.accessToken;assert.ok(tokens[role]);
}
await request('/api/health');checks.push('real application health body');
assert.equal((await request('/api/medicamentos')).totalElements,12);checks.push('catalog from Neon');
assert.equal((await request('/api/admin/farmacias',tokens.ADMINISTRADOR)).totalElements,5);
await request('/api/admin/farmacias',null,{expect:401});
await request('/api/admin/farmacias',tokens.USUARIO,{expect:403});
await request('/api/admin/farmacias',tokens.OPERADOR,{expect:403});checks.push('three roles and admin protection');
await request('/api/farmacias/2/stock',tokens.OPERADOR);
await request('/api/farmacias/1/stock',tokens.OPERADOR,{expect:403});checks.push('pharmacy isolation');
assert.equal((await request('/api/me/recetas-demo/configuracion',tokens.USUARIO)).escaneoHabilitado,false);checks.push('external AI remains disabled');
writeFileSync('.local-cloud/cold-session.private.json',JSON.stringify({patient:tokens.USUARIO}));
const report={at:new Date().toISOString(),url:base,success:true,checks,lastRequestAt:new Date().toISOString()};
writeFileSync('.local-cloud/public-readiness-tests.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));
