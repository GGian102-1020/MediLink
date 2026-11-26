import { execFileSync } from 'node:child_process';
export const docker = args => execFileSync('docker',args,{encoding:'utf8',maxBuffer:32*1024*1024}).trim();
export const inspect = name => JSON.parse(docker(['inspect',name]))[0];
export const envOf = obj => Object.fromEntries(obj.Config.Env.map(s=>{const i=s.indexOf('=');return [s.slice(0,i),s.slice(i+1)];}));
export function query(container,user,database,sql){
 return docker(['exec',container,'psql','-X','-U',user,'-d',database,'-At','-v','ON_ERROR_STOP=1','-c',`BEGIN READ ONLY; SET LOCAL TIME ZONE 'UTC'; ${sql}; COMMIT;`]).split('\n').filter(s=>!['BEGIN','COMMIT','SET'].includes(s)).join('\n');
}
export const rows=(c,u,d,sql)=>JSON.parse(query(c,u,d,`SELECT coalesce(json_agg(t),'[]'::json) FROM (${sql}) t`));
export function snapshot(c,u,d){
 const tables=rows(c,u,d,"SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename");
 return tables.map(({tablename:t})=>{if(!/^[a-z_]+$/.test(t))throw Error('Unexpected table');return {table:t,...JSON.parse(query(c,u,d,`SELECT json_build_object('count',count(*),'hash',md5(coalesce(string_agg(to_jsonb(t)::text,E'\\n' ORDER BY to_jsonb(t)::text),''))) FROM "${t}" t`))};});
}
