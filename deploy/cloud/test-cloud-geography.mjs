import {readFileSync,writeFileSync} from 'node:fs';
import assert from 'node:assert/strict';
const api='https://medifind-api-ggian102-mey9.onrender.com';
const account=JSON.parse(readFileSync('.local-cloud/credentials.private.json','utf8')).find(x=>x.rol==='USUARIO');
const report={at:new Date().toISOString(),checks:[],externalAiCalls:0,passed:false};
async function get(path,token){const r=await fetch(api+path,{headers:token?{Authorization:'Bearer '+token}:{},signal:AbortSignal.timeout(240000)});assert.equal(r.status,200);return r.json();}
try {
    const rows=await get('/api/presentaciones/6/disponibilidad');assert.ok(rows.length);
    const sanMartin=rows.find(x=>x.farmaciaId===2);assert.ok(sanMartin);
    assert.equal(sanMartin.latitud,-34.6188733);assert.equal(sanMartin.longitud,-58.4377466);
    assert.ok(rows.every(x=>Number.isFinite(x.latitud)&&Number.isFinite(x.longitud)));
    report.checks.push('Real Render availability returns numeric latitud/longitud for eligible pharmacies');
    const located=await get('/api/presentaciones/6/disponibilidad?lat=-34.6191&lng=-58.4374');
    assert.ok(located.every(x=>Number.isFinite(x.distanciaKm)&&x.distanciaKm>=0));report.checks.push('Distance with a synthetic CABA origin is calculated; no physical location transmitted');
    const login=await fetch(api+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({correo:account.email,contrasena:account.password}),signal:AbortSignal.timeout(240000)});
    assert.equal(login.status,200);const token=(await login.json()).accessToken;
    const config=await get('/api/me/recetas-demo/configuracion',token);assert.equal(config.escaneoHabilitado,false);
    report.checks.push('Live authenticated recipe configuration still disables external scanning');
    report.returnedPharmacies=rows.map(x=>({id:x.farmaciaId,latitude:x.latitud,longitude:x.longitud}));report.passed=true;
}catch(error){report.errorType=error.name;process.exitCode=1;}
finally{writeFileSync('.local-cloud/cloud-geography-api-tests.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));}
