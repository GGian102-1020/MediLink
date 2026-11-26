import {readFileSync,writeFileSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
import assert from 'node:assert/strict';

const base=process.env.MEDIFIND_CLOUD_API_URL?.replace(/\/$/,'');
const origin=new URL(base||'');
assert.equal(origin.protocol,'https:');assert.ok(origin.hostname.endsWith('.onrender.com'));
assert.ok(!origin.username&&!origin.password&&!origin.search&&!origin.hash&&origin.pathname==='/');
const accounts=JSON.parse(readFileSync('.local-cloud/credentials.private.json','utf8'));
const fixture=JSON.parse(readFileSync('.local-cloud/public-test-fixture.json','utf8'));
const report={at:new Date().toISOString(),url:base,externalAiCalls:0,checks:[],success:false};
const note=(name,data={})=>{report.checks.push({name,passed:true,...data});writeFileSync('.local-cloud/public-api-tests.json',JSON.stringify(report,null,2));console.log(name,JSON.stringify(data));};
async function request(path,token,{method='GET',body,form,expect=200,headers={}}={}) {
    const response=await fetch(base+path,{method,headers:{...(token?{Authorization:`Bearer ${token}`} : {}),...(body?{'Content-Type':'application/json'}:{}),...headers},body:form??(body?JSON.stringify(body):undefined),signal:AbortSignal.timeout(240000)});
    const text=await response.text();let data={};try{data=JSON.parse(text);}catch{}
    assert.equal(response.status,expect,`${path}: HTTP ${response.status}; expected ${expect}`);
    assert.ok(!/password_hash|jdbc:postgresql|npg_|BEGIN PRIVATE KEY|stackTrace/.test(text),'Sensitive diagnostic in API response');
    return {data,response};
}
async function login(role) {
    const account=accounts.find(x=>x.rol===role);assert.ok(account);
    const {data}=await request('/api/auth/login',null,{method:'POST',body:{correo:account.email,contrasena:account.password}});
    assert.ok(data.accessToken);return data.accessToken;
}
try {
    await request('/api/health');note('Public HTTPS health');
    const patient=await login('USUARIO'),operator=await login('OPERADOR'),admin=await login('ADMINISTRADOR');
    note('Private rotated credentials: patient, operator and administrator');
    const {data:catalog}=await request('/api/medicamentos');assert.equal(catalog.totalElements,12);
    const {data:pharmacies}=await request('/api/admin/farmacias',admin);assert.equal(pharmacies.totalElements,5);
    await request('/api/presentaciones/9/disponibilidad');
    note('Catalog, five pharmacies and public stock availability',{activeMedicines:12});
    for(const path of ['/api/admin/usuarios','/api/admin/farmacias','/api/admin/recetas-demo/19/pdf','/api/me'])await request(path,null,{expect:401});
    await request('/api/admin/usuarios',patient,{expect:403});await request('/api/admin/usuarios',operator,{expect:403});
    await request('/api/admin/farmacias','invalid.jwt.token',{expect:401});
    await request('/api/farmacias/1/stock',operator,{expect:403});await request('/api/farmacias/2/stock',operator);
    note('401, malformed JWT, admin role boundaries and pharmacy isolation');
    await request('/api/health',null,{headers:{Origin:'https://untrusted.test'},expect:403});
    note('Unapproved browser origins rejected');
    await request('/api/admin/recetas-demo/19/pdf-alterado',admin,{expect:404});
    const {data:scanConfig}=await request('/api/me/recetas-demo/configuracion',patient);assert.equal(scanConfig.escaneoHabilitado,false);
    const form=new FormData();form.append('archivo',new Blob(['%PDF-test'],{type:'application/pdf'}),'academic-test.pdf');
    await request('/api/me/recetas-demo/escaneo',patient,{method:'POST',form,expect:503});
    note('External AI disabled and altered-document generator closed',{externalAiCalls:0});
    const selection={codigoReceta:fixture.codigo,presentacionId:fixture.id_presentacion,cantidad:1};
    const {data:claim}=await request('/api/me/recetas-demo/vinculacion',patient,{method:'POST',body:selection});
    assert.ok(claim.prescripcion);
    const key=randomUUID(),body={farmaciaId:2,presentacionId:fixture.id_presentacion,cantidad:1,codigoPrescripcion:fixture.codigo};
    let reservation;
    try {
        ({data:reservation}=await request('/api/reservas',patient,{method:'POST',body,headers:{'Idempotency-Key':key},expect:201}));
        // Persist a private cleanup handle before further assertions or requests.
        writeFileSync('.local-cloud/public-test-reservation.private.json',JSON.stringify({id:reservation.id,code:reservation.codigoVisible,key}));
        assert.equal(reservation.estado,'PENDIENTE');
        const {data:repeated}=await request('/api/reservas',patient,{method:'POST',body,headers:{'Idempotency-Key':key},expect:201});
        assert.equal(repeated.id,reservation.id);
        const {data:visible}=await request(`/api/farmacias/2/reservas/${reservation.codigoVisible}`,operator);assert.equal(visible.id,reservation.id);
        await request(`/api/farmacias/1/reservas/${reservation.codigoVisible}`,operator,{expect:403});
    } finally {
        if(reservation?.codigoVisible)await request(`/api/reservas/${reservation.codigoVisible}/cancelacion`,patient,{method:'POST'});
    }
    const {data:cancelled}=await request(`/api/reservas/mias/${reservation.codigoVisible}`,patient);assert.equal(cancelled.estado,'CANCELADA');
    report.testReservationId=reservation.id;
    note('Academic prescription, reservation, idempotency, operator visibility and cancellation',{testReservationId:reservation.id});
    const attempts=await Promise.all(Array.from({length:12},()=>fetch(base+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({correo:'nobody@demo.medifind.test',contrasena:'invalid-test'}),signal:AbortSignal.timeout(180000)})));
    assert.ok(attempts.some(x=>x.status===429));
    assert.ok(attempts.every(x=>[401,429].includes(x.status)));
    for(const response of attempts)await response.text();
    note('Public authentication rate limit: HTTP 429',{attempts:attempts.length});
    report.success=true;report.lastRequestAt=new Date().toISOString();
    writeFileSync('.local-cloud/public-api-tests.json',JSON.stringify(report,null,2));
} catch(error) {
    report.error=error.message;writeFileSync('.local-cloud/public-api-tests.json',JSON.stringify(report,null,2));
    console.error(error.message);process.exitCode=1;
}
