import {readFileSync,readdirSync,statSync,writeFileSync,existsSync} from 'node:fs';
import {resolve,join} from 'node:path';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const secrets=[];
function collect(value,key='') {if(typeof value==='string'&&/password|secret|token|connection|api.?key/i.test(key)&&value.length>=20)secrets.push(value);else if(Array.isArray(value))value.forEach(x=>collect(x));else if(value&&typeof value==='object')for(const[k,v]of Object.entries(value))collect(v,k);}
for(const f of readdirSync('.local-cloud').filter(x=>x.endsWith('.private.json')))collect(JSON.parse(readFileSync('.local-cloud/'+f,'utf8')));
collect(JSON.parse(readFileSync('.local-cloud/signing/private.json','utf8')));
for(const f of ['.env','backend/.env'])if(existsSync(f))for(const line of readFileSync(f,'utf8').split(/\r?\n/)){const m=line.match(/^\s*([A-Z_]+)\s*=\s*(.*?)\s*$/);if(m&&/PASSWORD|SECRET|TOKEN|API_KEY/.test(m[1]))collect(m[2].replace(/^['"]|['"]$/g,''),m[1]);}
const findings=[];let files=0;
function scanData(path,data) {files++;const text=data.toString('utf8');if(secrets.some(x=>data.includes(Buffer.from(x)))||/sk-(?:proj-)?[A-Za-z0-9_-]{30,}|npg_[A-Za-z0-9]{12,}|-----BEGIN (?:[A-Z ]+)?PRIVATE KEY-----/.test(text))findings.push({file:path,reason:'private-value-or-secret-pattern'});if(/(?:\.env(?:\.|$)|\.p12$|\.jks$|\.dump$|_worker\.js$|[/\\]functions[/\\])/.test(path))findings.push({file:path,reason:'private-or-compute-file'});}
function scan(path) {if(statSync(path).isDirectory()){for(const f of readdirSync(path))scan(join(path,f));return;}if(path.endsWith('.apk')){const require=createRequire(resolve('.local-cloud/browser-tests/package.json'));const Zip=require('adm-zip');const entries=new Zip(path).getEntries().filter(x=>!x.isDirectory);for(const entry of entries)scanData(path+'!/'+entry.entryName,entry.getData());assert.ok(entries.some(x=>x.getData().includes(Buffer.from('https://medifind-api-ggian102-mey9.onrender.com/'))),'APK does not contain the genuine Render URL');assert.ok(!entries.some(x=>/http:\/\/localhost:18081|https:\/\/api\.example\.invalid|http:\/\/192\.168\.1\.40:5174/.test(x.getData().toString('utf8'))),'APK contains a local or placeholder API URL');return;}scanData(path,readFileSync(path));}
const paths=process.argv.slice(2);assert.ok(paths.length);
for(const path of paths)scan(path);
const report={at:new Date().toISOString(),paths,files,findings,passed:findings.length===0};
writeFileSync('.local-cloud/artifact-audit.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));assert.ok(report.passed,'Do not publish artifacts with findings');
