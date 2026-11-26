// Reuse the user's existing Git credential only with GitHub's official CLI.
// No credential is printed, written to the repository, or sent elsewhere.
import{execFileSync,spawnSync}from'node:child_process';
import{resolve}from'node:path';
const args=process.argv.slice(2),gh=resolve('.local-cloud/tools/gh/bin/gh.exe');
const env={...process.env,GIT_TERMINAL_PROMPT:'0',GH_PROMPT_DISABLED:'1'};
if(!env.GH_TOKEN){
 try{const output=execFileSync('git',['-c','credential.interactive=never','credential','fill'],{input:'protocol=https\nhost=github.com\n\n',encoding:'utf8',stdio:['pipe','pipe','pipe']});const found=output.split('\n').find(x=>x.startsWith('password='));if(found)env.GH_TOKEN=found.slice(9).trim();}catch{ /* CLI may already have its own authenticated session. */ }
}
const result=spawnSync(gh,args,{env,stdio:'inherit'});process.exitCode=result.status??1;
