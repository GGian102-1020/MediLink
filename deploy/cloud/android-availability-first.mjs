// Applied only to the isolated cloud candidate. Server-side reservation rules are unchanged.
import {readFileSync,writeFileSync} from 'node:fs';
import {join} from 'node:path';
import assert from 'node:assert/strict';
export function applyAvailabilityFirst(stage) {
    const path=join(stage,'app/src/main/java/ar/edu/uade/medilink/android/MainActivity.kt');
    let text=readFileSync(path,'utf8').replace(/\r\n/g,'\n');
    const before='if (p.condicionDispensa == "BAJO_RECETA" && rol == "USUARIO") nav.navigate("receta/${p.id}")\n                        else nav.navigate("disponibilidad/${p.id}")';
    assert.equal(text.split(before).length-1,1,'Unexpected cloud navigation source');
    text=text.replace(before,'nav.navigate("disponibilidad/${p.id}")');
    text=text.replace('// BAJO_RECETA con sesión: primero se adjunta la receta y después se eligen farmacias.',
        '// Todos pueden consultar disponibilidad; BAJO_RECETA exige validación antes de reservar.');
    function replace(before,after) {
        assert.equal(text.split(before).length-1,1,'Unexpected cloud pharmacy-selection context');
        text=text.replace(before,after);
    }
    replace('composable("receta/{presentacionId}", arguments = listOf(navArgument("presentacionId") { type = NavType.LongType }))',
        'composable("receta/{presentacionId}?farmacia={farmacia}", arguments = listOf(navArgument("presentacionId") { type = NavType.LongType }, navArgument("farmacia") { type = NavType.LongType; defaultValue = -1L }))');
    replace('val presentacionId = e.arguments!!.getLong("presentacionId")',
        'val presentacionId = e.arguments!!.getLong("presentacionId")\n                        val farmaciaId = e.arguments!!.getLong("farmacia")');
    replace('cantidad=${r.cantidad}")','cantidad=${r.cantidad}&farmacia=$farmaciaId")');
    replace('composable("disponibilidad/{id}?receta={receta}&cantidad={cantidad}"',
        'composable("disponibilidad/{id}?receta={receta}&cantidad={cantidad}&farmacia={farmacia}"');
    replace('navArgument("cantidad") { type = NavType.IntType; defaultValue = 1 },',
        'navArgument("cantidad") { type = NavType.IntType; defaultValue = 1 },\n                    navArgument("farmacia") { type = NavType.LongType; defaultValue = -1L },');
    replace('getStateFlow<Long?>("farmaciaElegida", null)',
        'getStateFlow<Long?>("farmaciaElegida", e.arguments?.getLong("farmacia")?.takeIf { it > 0 })');
    replace('onAdjuntarReceta = { nav.navigate("receta/$id") }',
        'onAdjuntarReceta = { farmacia -> nav.navigate("receta/$id?farmacia=$farmacia") }');
    writeFileSync(path,text);
    const screen=join(stage,'app/src/main/java/ar/edu/uade/medilink/android/ui/screens/DisponibilidadScreen.kt');
    let view=readFileSync(screen,'utf8');
    for(const [before,after] of [
        ['onAdjuntarReceta: () -> Unit = {},','onAdjuntarReceta: (Long) -> Unit = {},'],
        ['Button(onClick = onAdjuntarReceta,','Button(onClick = { onAdjuntarReceta(farmaciaElegida) },'],
    ]) { assert.equal(view.split(before).length-1,1,'Unexpected cloud availability screen');view=view.replace(before,after); }
    writeFileSync(screen,view);
}
