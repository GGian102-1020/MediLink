import {readFileSync,writeFileSync,unlinkSync} from 'node:fs';
import assert from 'node:assert/strict';
const base=process.env.MEDIFIND_CLOUD_API_URL?.replace(/\/$/,'');
assert.equal(base,'https://medifind-api-ggian102-mey9.onrender.com');
const last=JSON.parse(readFileSync('.local-cloud/auth-proxy-test.json','utf8'));
assert.ok(last.passed,'Public forwarding-header test must pass before the idle test');
const idleMs=Date.now()-Date.parse(last.lastRequestAt);
assert.ok(idleMs>=21*60*1000,'Leave the service without HTTP traffic for at least 21 minutes first');
const session=JSON.parse(readFileSync('.local-cloud/cold-session.private.json','utf8'));
const reservation=JSON.parse(readFileSync('.local-cloud/public-test-reservation.private.json','utf8'));
const report={startedAt:new Date().toISOString(),idleSeconds:Math.round(idleMs/1000),url:base,attempts:[],success:false};
const began=Date.now();
try {
    let ready=false;
    while(Date.now()-began<8*60*1000) {
        try {
            const response=await fetch(base+'/api/medicamentos',{headers:{Accept:'application/json'},signal:AbortSignal.timeout(240000)});
            const type=response.headers.get('content-type')||'';
            const text=await response.text();let data;try{data=JSON.parse(text);}catch{}
            report.attempts.push({status:response.status,contentType:type,elapsedSeconds:Math.round((Date.now()-began)/1000)});
            if(response.status===200&&data?.totalElements===12){ready=true;break;}
        } catch(error) {report.attempts.push({networkError:error.name,elapsedSeconds:Math.round((Date.now()-began)/1000)});}
        await new Promise(resolve=>setTimeout(resolve,10000));
    }
    assert.ok(ready,'Catalog did not recover within the cold-start observation window');
    report.catalogRecoverySeconds=Math.round((Date.now()-began)/1000);
    const me=await fetch(base+'/api/me',{headers:{Authorization:'Bearer '+session.patient},signal:AbortSignal.timeout(30000)});
    await me.text();assert.equal(me.status,200,'Previously issued session did not survive the restart');
    const admin=await fetch(base+'/api/admin/farmacias',{headers:{Authorization:'Bearer '+session.patient},signal:AbortSignal.timeout(30000)});
    await admin.text();assert.equal(admin.status,403,'Administrative role protection changed after restart');
    const reserve=await fetch(base+'/api/reservas/mias/'+reservation.code,{headers:{Authorization:'Bearer '+session.patient},signal:AbortSignal.timeout(30000)});
    const data=await reserve.json();assert.equal(reserve.status,200);assert.equal(data.id,reservation.id);assert.equal(data.estado,'CANCELADA');
    report.sessionRetained=true;report.adminRoleProtectionRetained=true;report.reservationPersisted=true;report.neonReadRecovered=true;report.success=true;
    unlinkSync('.local-cloud/cold-session.private.json');
} catch(error) {report.error=error.message;process.exitCode=1;}
report.finishedAt=new Date().toISOString();
writeFileSync('.local-cloud/cold-start-test.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));
