// Applied only to an isolated cloud candidate. Original Android/LAN sources stay intact.
import {readFileSync,writeFileSync} from 'node:fs';
import {join} from 'node:path';
import assert from 'node:assert/strict';
export function applyCapturePreview(stage) {
    const screens=join(stage,'app/src/main/java/ar/edu/uade/medilink/android/ui/screens');
    const edit=(name,changes)=>{
        const file=join(screens,name);let text=readFileSync(file,'utf8');
        for(const [before,after]of changes){assert.equal(text.split(before).length-1,1,`Unexpected cloud patch context: ${name}`);text=text.replace(before,after);}
        writeFileSync(file,text);
    };
    edit('MisRecetasScreen.kt',[
        ['enabled = !estado.ocupado && estado.escaneoHabilitado == true,','enabled = !estado.ocupado,'],
        ['El análisis automático todavía no está disponible. Podés reintentar con Actualizar listado o vincular el código de una receta de demostración.','El análisis automático está desactivado. Podés tomar una foto o elegir un archivo para revisarlo en este teléfono. No se envía ni se verifica hasta que el análisis esté disponible. También podés vincular una receta por código.'],
        ['Button(onClick = vm::escanear, enabled = !estado.ocupado,','Button(onClick = vm::escanear, enabled = !estado.ocupado && estado.escaneoHabilitado == true,'],
    ]);
    edit('ReservaConRecetaScreen.kt',[
        ['val puedeAdjuntar = !ocupado && state.escaneoHabilitado == true','val puedeAdjuntar = !ocupado'],
        ['El análisis automático todavía no está disponible. Podés reintentar aquí o usar el código de una receta de demostración.','El análisis automático está desactivado. Podés tomar una foto o elegir un archivo para revisarlo aquí, sin enviarlo. Para continuar la reserva, usá una receta ya vinculada o su código.'],
    ]);
    // Approximate location is supported by obtenerUbicacion; reuse an existing coarse grant.
    const fine='context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED';
    const either=`(${fine} || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)`;
    for(const name of ['MapasScreen.kt','DisponibilidadScreen.kt'])edit(name,[[fine,either]]);
}
