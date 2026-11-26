// Resolve existing street/number pairs; never guess coordinates or change records.
import {writeFileSync} from 'node:fs';
import assert from 'node:assert/strict';
const addresses=[
    {id:1,address:'Av. Santa Fe 3200, Palermo, CABA',query:'Santa Fe 3200',height:3200},
    {id:2,address:'Av. Rivadavia 5100, Caballito, CABA',query:'Rivadavia 5100',height:5100},
    {id:3,address:'Av. Corrientes 1200, San Nicolás, CABA',query:'Corrientes 1200',height:1200},
    {id:4,address:'Av. Cabildo 2100, Belgrano, CABA',query:'Cabildo 2100',height:2100},
    {id:5,address:'Av. Rivadavia 7000, Flores, CABA',query:'Rivadavia 7000',height:7000},
];
const results=await Promise.all(addresses.map(async item=>{
    const url=new URL('https://apis.datos.gob.ar/georef/api/direcciones');
    url.search=new URLSearchParams({direccion:item.query,provincia:'02',max:'10'});
    const response=await fetch(url,{signal:AbortSignal.timeout(30000)});
    assert.equal(response.status,200);const body=await response.json();
    const unique=body.total===1&&body.direcciones?.length===1;
    const match=unique?body.direcciones[0]:null;
    if(match){assert.equal(match.altura.valor,item.height);assert.equal(match.provincia.id,'02');
        assert.ok(match.ubicacion.lat>-34.72&&match.ubicacion.lat<-34.52&&match.ubicacion.lon>-58.55&&match.ubicacion.lon<-58.32);}
    return {...item,source:url.href,unique,match,response:body};
}));
writeFileSync('.local-cloud/pharmacy-geocoding.json',JSON.stringify({at:new Date().toISOString(),provider:'Georef, Argentina.gob.ar',results},null,2));
console.log(JSON.stringify(results.map(({id,address,unique,match,source})=>({id,address,unique,normalized:match?.nomenclatura,latitude:match?.ubicacion.lat,longitude:match?.ubicacion.lon,source})),null,2));
assert.ok(results.every(x=>x.unique),'An address has no unique match; do not assign it automatically');
