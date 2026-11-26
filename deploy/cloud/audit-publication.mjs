import {execFileSync} from 'node:child_process';
import {readFileSync,writeFileSync,existsSync,readdirSync} from 'node:fs';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import assert from 'node:assert/strict';

const git=args=>execFileSync('git',args,{encoding:'utf8',maxBuffer:64*1024*1024}).trim();
const knownSecrets=new Set();
function collect(value,key='') {
    if (typeof value==='string'&&/password|secret|token|connection|api.?key/i.test(key)&&value.length>=20) knownSecrets.add(value);
    else if(Array.isArray(value))value.forEach(x=>collect(x));
    else if(value&&typeof value==='object')for(const [k,v] of Object.entries(value))collect(v,k);
}
for(const file of readdirSync('.local-cloud').filter(x=>x.endsWith('.private.json'))) {
    try{collect(JSON.parse(readFileSync(`.local-cloud/${file}`,'utf8')));}catch{}
}
if(existsSync('.local-cloud/signing/private.json'))collect(JSON.parse(readFileSync('.local-cloud/signing/private.json','utf8')));
if(existsSync('.local-cloud/render-cli-config/cli.yaml')) {
    const require=createRequire(resolve('.local-cloud/schema-tools/package.json'));
    const cli=require('yaml').parse(readFileSync('.local-cloud/render-cli-config/cli.yaml','utf8'));
    collect(cli.api?.key,'api_key');collect(cli.api?.refresh_token,'refresh_token');
}
for(const file of ['.env','backend/.env'])if(existsSync(file)) {
    for(const line of readFileSync(file,'utf8').split(/\r?\n/)) {
        const match=line.match(/^\s*([A-Z_]+)\s*=\s*(.*?)\s*$/);
        if(match&&/PASSWORD|SECRET|TOKEN|API_KEY/.test(match[1]))collect(match[2].replace(/^['"]|['"]$/g,''),match[1]);
    }
}
const patterns=[/sk-(?:proj-)?[A-Za-z0-9_-]{30,}/,/npg_[A-Za-z0-9]{12,}/,/-----BEGIN (?:[A-Z ]+)?PRIVATE KEY-----/,/postgres(?:ql)?:\/\/[^\s:/]+:[^\s@]{12,}@/];
const forbidden=/\.(?:dump|backup|p12|pfx|jks|keystore|key)$/i;
const findings=[];
const incremental=process.argv.includes('--changed-only');
const paths=git(incremental?['diff','--name-only','--diff-filter=ACMR','-z','origin/deploy/medifind-cloud','HEAD']:['ls-files','-z']).split('\0').filter(Boolean);
let filesChecked=0;
for(const file of paths) {
    if(forbidden.test(file)||/(^|\/)\.env(?:\.[^/]+)?$/.test(file)&&!/(?:example|sample|template)$/.test(file))findings.push({file,reason:'private-file-type'});
    const content=execFileSync('git',['show',`HEAD:${file}`],{maxBuffer:64*1024*1024}).toString('utf8');
    filesChecked++;
    if(patterns.some(p=>p.test(content))||[...knownSecrets].some(value=>content.includes(value)))findings.push({file,reason:'secret-pattern-or-known-private-value'});
}
// Inspect every blob in commits which have not been published to origin.
const objects=git(['rev-list','--objects','HEAD','--not','--remotes=origin']).split(/\r?\n/).filter(Boolean);
let unpublishedBlobsChecked=0;
for(const line of objects) {
    const [id,...parts]=line.split(' '),file=parts.join(' ');
    if(git(['cat-file','-t',id])!=='blob')continue;
    unpublishedBlobsChecked++;
    const content=execFileSync('git',['cat-file','blob',id],{maxBuffer:64*1024*1024}).toString('utf8');
    if(forbidden.test(file)||patterns.some(p=>p.test(content))||[...knownSecrets].some(value=>content.includes(value)))findings.push({file,reason:'unpublished-history-sensitive-content'});
}
const report={at:new Date().toISOString(),commit:git(['rev-parse','HEAD']),incremental,filesChecked,unpublishedBlobsChecked,knownPrivateValuesChecked:knownSecrets.size,findings,passed:findings.length===0};
writeFileSync('.local-cloud/publication-audit.json',JSON.stringify(report,null,2));
console.log(JSON.stringify(report,null,2));
assert.ok(report.passed,'Publication audit failed; no push is allowed');
