import {createRequire} from 'node:module';
import {readFileSync,writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
import assert from 'node:assert/strict';

// Local-only validation. Install ajv@8, ajv-formats@3 and yaml@2 under
// .local-cloud/schema-tools and download the official schema before running.
const require=createRequire(resolve('.local-cloud/schema-tools/package.json'));
const Ajv=require('ajv/dist/2020').default;
const addFormats=require('ajv-formats');
const yaml=require('yaml');
const schema=JSON.parse(readFileSync('.local-cloud/render-schema.json','utf8'));
assert.equal(schema.$id,'https://render.com/schema/render.yaml.json');
const config=yaml.parse(readFileSync('deploy/cloud/render.yaml','utf8'));
const ajv=new Ajv({allErrors:true,strict:false});addFormats(ajv);
const validate=ajv.compile(schema);
assert.ok(validate(config),JSON.stringify(validate.errors));
assert.equal(config.services.length,1);
const service=config.services[0];
assert.equal(service.plan,'free');
assert.equal(service.runtime,'docker');
assert.equal(service.autoDeployTrigger,'off');
assert.equal(config.previews.generation,'off');
assert.equal(service.healthCheckPath,'/api/health');
assert.ok(!config.databases&&!config.envVarGroups&&!service.disk&&!service.scaling);
const result={at:new Date().toISOString(),officialSchema:schema.$id,schemaValid:true,service:service.name,plan:service.plan,autoDeploy:false,previews:false,additionalPaidResources:false,healthCheckPath:service.healthCheckPath,published:false};
writeFileSync('.local-cloud/render-config-validation.json',JSON.stringify(result,null,2));
console.log(JSON.stringify(result,null,2));
