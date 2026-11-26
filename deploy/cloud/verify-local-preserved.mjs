import {readFileSync,writeFileSync} from 'node:fs';
import assert from 'node:assert/strict';
import {inspect,snapshot} from '../../scripts/demo-local/Database-Tools.mjs';

const original=JSON.parse(readFileSync('.local-demo/original-database-backup.json','utf8'));
assert.equal(original.source,'medlink-db');
assert.equal(original.volume,'medilink_pgdata');
assert.ok(inspect(original.source).Mounts.some(x=>x.Name===original.volume));
const current=snapshot(original.source,'medlink',original.database);
const comparisons=original.tables.map(before=>{
    const after=current.find(x=>x.table===before.table);
    return {table:before.table,rows:after?.count,matched:before.count===after?.count&&before.hash===after?.hash};
});
const result={at:new Date().toISOString(),source:original.source,readOnly:true,tablesCompared:comparisons.length,allPreserved:comparisons.every(x=>x.matched),comparisons};
writeFileSync('.local-cloud/local-preservation.json',JSON.stringify(result,null,2));
console.log(JSON.stringify({readOnly:true,tablesCompared:result.tablesCompared,allPreserved:result.allPreserved}));
assert.ok(result.allPreserved,'Local data changed since the reference backup; inspect before claiming preservation');
