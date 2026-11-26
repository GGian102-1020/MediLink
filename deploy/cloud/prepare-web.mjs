import {cpSync,existsSync,mkdirSync,readFileSync,writeFileSync,symlinkSync} from 'node:fs';
import {resolve} from 'node:path';
import {spawnSync} from 'node:child_process';
const origin=process.env.MEDIFIND_CLOUD_API_URL?.replace(/\/$/,'');
const apk=process.env.MEDIFIND_CLOUD_APK_URL;
const validate=process.argv.includes('--validate-only');
if(!validate){
 const u=new URL(origin||'');if(u.protocol!=='https:'||!u.hostname.endsWith('.onrender.com')||u.username||u.password||u.pathname!=='/')throw Error('A verified Render origin is required');
 const a=new URL(apk||'');if(a.origin!=='https://github.com'||!/^\/GGian102-1020\/MediLink\/releases\/download\/[^/]+\/[^/]+\.apk$/.test(a.pathname))throw Error('An explicit, verified release asset is required');
}
const run=(cwd,args,env={})=>{const r=spawnSync(process.platform==='win32'?'npm.cmd':'npm',args,{cwd,env:{...process.env,...env},stdio:'inherit',shell:process.platform==='win32'});if(r.status)throw Error(`Web check failed in ${cwd}`);};
for(const app of ['frontend','admin-web','landing']){
 const out=resolve(`.local-cloud/web/${app}`);mkdirSync(out,{recursive:true});
 for(const path of ['src','public','tests','index.html','package.json','package-lock.json','tsconfig.json','vite.config.ts','vite-env.d.ts'])if(existsSync(`${app}/${path}`))cpSync(`${app}/${path}`,`${out}/${path}`,{recursive:true});
 if(!existsSync(`${out}/node_modules`))symlinkSync(resolve(`${app}/node_modules`),`${out}/node_modules`,'junction');
 if(app!=='landing'){
  const p=`${out}/src/lib/api.ts`;let code=readFileSync(p,'utf8');
  code=`/// <reference types="vite/client" />\nconst CLOUD_API_ORIGIN=(import.meta.env.VITE_API_ORIGIN||'').replace(/\\/$/,'');\n`+code.replaceAll('fetch(path,','fetch(CLOUD_API_ORIGIN+path,');
  writeFileSync(p,code);
 }
 run(out,['test'],{VITE_API_ORIGIN:''});
 if(!validate){run(out,['run','build'],{VITE_API_ORIGIN:origin,VITE_ANDROID_DOWNLOAD_URL:apk});writeFileSync(`${out}/dist/_headers`,'/*\n  X-Content-Type-Options: nosniff\n  Referrer-Policy: no-referrer\n  Permissions-Policy: camera=(self), geolocation=(self)\n');}
}
console.log(validate?'Cloud web adaptations passed existing suites; production URL still required.':'Static production artifacts ready in .local-cloud/web/*/dist; nothing published.');
