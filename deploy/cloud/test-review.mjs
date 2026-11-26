import {readFileSync,writeFileSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
import {rows} from '../../scripts/demo-local/Database-Tools.mjs';
import assert from 'node:assert/strict';
const base='http://127.0.0.1:18182',accounts=JSON.parse(readFileSync('.local-cloud/credentials.private.json','utf8'));
const report={at:new Date().toISOString(),environment:'isolated local cloud review / 512 MiB / 0.1 CPU',externalAiCalls:0,checks:[]};
const note=(name,data)=>{report.checks.push({name,...data});writeFileSync('.local-cloud/runtime-tests.json',JSON.stringify(report,null,2));console.log(name,JSON.stringify(data));};
async function request(path,token,{method='GET',body,form,expect=200,headers={}}={}){
 const r=await fetch(base+path,{method,headers:{...(token?{Authorization:`Bearer ${token}`} : {}),...(body?{'Content-Type':'application/json'}:{}),...headers},body:form??(body?JSON.stringify(body):undefined),signal:AbortSignal.timeout(120000)});
 const text=await r.text();let data;try{data=JSON.parse(text);}catch{data={};}assert.equal(r.status,expect,`${path}: ${data.codigo||r.status}`);return data;
}
const login=async role=>{const a=accounts.find(x=>x.rol===role);assert.ok(a);const data=await request('/api/auth/login',null,{method:'POST',body:{correo:a.email,contrasena:a.password}});return data.accessToken;};
try{
 await request('/api/health');note('Spring Boot + PostgreSQL 18 + Flyway',{passed:true});
 const patient=await login('USUARIO'),operator=await login('OPERADOR'),admin=await login('ADMINISTRADOR');note('Rotated private credentials / three roles',{passed:true});
 const catalog=await request('/api/medicamentos');assert.equal(catalog.totalElements,12);
 const pharmacies=await request('/api/admin/farmacias',admin);assert.equal(pharmacies.totalElements,5);
 await request('/api/admin/usuarios',operator,{expect:403});await request('/api/me',null,{expect:401});await request('/api/farmacias/1/stock',operator,{expect:403});
 note('Catalog, pharmacies, role protection and pharmacy isolation',{passed:true,activeMedicines:12,pharmacies:5});
 const allowed=await fetch(base+'/api/health',{headers:{Origin:'https://medifind-operador-ggian102.pages.dev'}});assert.equal(allowed.headers.get('access-control-allow-origin'),'https://medifind-operador-ggian102.pages.dev');
 await request('/api/health',null,{headers:{Origin:'https://untrusted.test'},expect:403});note('Exact CORS allow list',{passed:true});
 await request('/api/admin/recetas-demo/19/pdf-alterado',admin,{expect:404});await request('/api/auth/password-recovery',null,{method:'POST',body:{correo:accounts.find(x=>x.rol==='USUARIO').email},expect:503});note('Altered-document generator closed / unconfigured mail fails safely',{passed:true});
 const q=sql=>rows('medifind-cloud-review-db','medlink','medlink',sql);
 const recipe=q('SELECT codigo FROM receta_demo WHERE id_receta_demo=19')[0].codigo;
 await request('/api/me/recetas-demo/vinculacion',patient,{method:'POST',body:{codigoReceta:recipe,presentacionId:9,cantidad:1}});
 const before=q('SELECT cantidad_disponible n FROM stock WHERE id_farmacia=2 AND id_presentacion=9')[0].n;
 const reserve=await request('/api/reservas',patient,{method:'POST',headers:{'Idempotency-Key':randomUUID()},body:{farmaciaId:2,presentacionId:9,cantidad:1,codigoPrescripcion:recipe},expect:201});assert.equal(reserve.estado,'PENDIENTE');
 const op=await request(`/api/farmacias/2/reservas/${reserve.codigoVisible}`,operator);assert.equal(op.id,reserve.id);
 await request(`/api/reservas/${reserve.codigoVisible}/cancelacion`,patient,{method:'POST'});assert.equal(q('SELECT cantidad_disponible n FROM stock WHERE id_farmacia=2 AND id_presentacion=9')[0].n,before);
 assert.equal(q('SELECT i.cantidad_comprometida n FROM item_prescripcion i JOIN receta_demo d USING(id_prescripcion) WHERE d.id_receta_demo=19')[0].n,0);
 note('Reservation, operator visibility, cancellation and release of stock/quantity',{passed:true,reviewOnlyReservation:reserve.codigoVisible});
 for(let i=0;i<3;i++){const f=new FormData();f.append('archivo',new Blob(['%PDF-test'],{type:'application/pdf'}),'test.pdf');await request('/api/me/recetas-demo/escaneo',patient,{method:'POST',form:f,expect:i<2?503:429});}
 const count=q('SELECT attempts FROM cloud_scan_budget WHERE usage_date=CURRENT_DATE')[0].attempts;assert.equal(count,2);note('Persistent scan budget / no provider calls',{passed:true,used:count,cap:2});
 let throttled=false;for(let i=0;i<12;i++){const r=await fetch(base+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({correo:'nobody@demo.medifind.test',contrasena:'invalid-test'})});if(r.status===429){throttled=true;break;}}assert.ok(throttled);note('Authentication attempt limit',{passed:true});
 report.success=true;writeFileSync('.local-cloud/runtime-tests.json',JSON.stringify(report,null,2));
}catch(error){report.success=false;report.error=error.message;writeFileSync('.local-cloud/runtime-tests.json',JSON.stringify(report,null,2));console.error(error.message);process.exitCode=1;}
