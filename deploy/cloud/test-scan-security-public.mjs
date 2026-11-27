// Production probes: reads and rejected EMPTY scan requests only. Never uploads a document or reserves stock.
import {readFileSync,writeFileSync} from 'node:fs';
import assert from 'node:assert/strict';
const base='https://medifind-api-ggian102-mey9.onrender.com';
const accounts=JSON.parse(readFileSync('.local-cloud/credentials.private.json','utf8'));
const report={at:new Date().toISOString(),externalAiCalls:0,documentUploads:0,reservationRequests:0,checks:[],passed:false};
async function request(path,token,{method='GET',body,emptyMultipart=false,expected=200}={}) {
    const r=await fetch(base+path,{method,headers:{...(token?{Authorization:'Bearer '+token}:{}),...(body?{'Content-Type':'application/json'}:{})},body:emptyMultipart?new FormData():body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(240000)});
    const raw=await r.text();assert.equal(r.status,expected,path+' HTTP status');let data;try{data=JSON.parse(raw);}catch{}
    assert.ok(!/npg_|sk-proj-|BEGIN PRIVATE KEY|jdbc:postgresql/.test(raw));return data;
}
async function login(role) {const a=accounts.find(x=>x.rol===role);const d=await request('/api/auth/login',null,{method:'POST',body:{correo:a.email,contrasena:a.password}});assert.ok(d.accessToken);return d.accessToken;}
try {
    await request('/api/health');
    const patient=await login('USUARIO'),operator=await login('OPERADOR'),admin=await login('ADMINISTRADOR');
    const cfg=await request('/api/me/recetas-demo/configuracion',patient);assert.equal(cfg.escaneoHabilitado,false);
    await request('/api/me/recetas-demo/escaneo',null,{method:'POST',emptyMultipart:true,expected:401});
    await request('/api/me/recetas-demo/escaneo','invalid.jwt',{method:'POST',emptyMultipart:true,expected:401});
    for(const actor of [operator,admin])await request('/api/me/recetas-demo/escaneo',actor,{method:'POST',emptyMultipart:true,expected:403});
    for(let i=0;i<3;i++)await request('/api/me/recetas-demo/escaneo',patient,{method:'POST',emptyMultipart:true,expected:503});
    report.checks.push('Disabled scan stays 503 across repeated requests; anonymous/invalid JWT 401; operator/admin 403; no documents sent');
    await request('/api/admin/farmacias',patient,{expected:403});await request('/api/admin/usuarios',operator,{expected:403});
    await request('/api/farmacias/1/stock',operator,{expected:403});await request('/api/farmacias/2/stock',operator);
    const pharmacies=await request('/api/admin/farmacias',admin);assert.equal(pharmacies.totalElements,5);
    const catalog=await request('/api/medicamentos');assert.ok(catalog.totalElements>=12);
    const available=await request('/api/presentaciones/7/disponibilidad');assert.ok(Array.isArray(available));
    await request('/api/reservas/mias',patient);
    report.checks.push('Catalog, public Rx availability, reservations read, five pharmacies, admin role and operator pharmacy isolation remain functional');
    report.passed=true;
}catch {report.error='Public safety probe failed; credentials, tokens and response bodies omitted';process.exitCode=1;}
finally {writeFileSync('.local-cloud/public-scan-security-tests.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));}
