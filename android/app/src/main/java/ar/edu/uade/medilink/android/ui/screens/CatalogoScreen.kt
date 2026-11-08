package ar.edu.uade.medilink.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.medilink.android.ui.viewmodel.CatalogoVm
import ar.edu.uade.medilink.android.ui.viewmodel.PresentacionesVm

@Composable
fun CatalogoScreen(vm: CatalogoVm, onAbrir: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var consulta by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Encabezado("Buscar medicamentos", "Consultá las presentaciones disponibles en MediLink.") }
        item {
            OutlinedTextField(
                value = consulta,
                onValueChange = { consulta = it },
                label = { Text("Nombre del medicamento") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(onClick = { vm.buscar(consulta) }, enabled = !state.cargando) {
                Text("Buscar")
            }
        }
        item { AvisoError(state.error) }
        items(state.lista, key = { it.id }) { medicamento ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onAbrir(medicamento.id) }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(medicamento.nombre)
                    Text("Ver presentaciones")
                }
            }
        }
        if (state.lista.isEmpty() && !state.cargando && state.error == null) {
            item { Text("No se encontraron medicamentos.") }
        }
        if (state.cargando) item { IndicadorCargando() }
        if (!state.ultimaPagina && !state.cargando) {
            item { Button(onClick = vm::mas) { Text("Cargar más") } }
        }
    }
}

@Composable
fun PresentacionesScreen(vm: PresentacionesVm, onAbrir: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Encabezado("Presentaciones", "Elegí una presentación para ver las farmacias con stock.") }
        item { AvisoError(state.error) }
        items(state.lista, key = { it.id }) { item ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onAbrir(item.id) }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(item.medicamentoNombre)
                    Text("${item.concentracionTexto} · ${item.formaFarmaceutica}")
                    Text("Envase: ${item.cantidadEnvase} ${item.unidadExpendio}")
                    Text(if (item.condicionDispensa == "BAJO_RECETA") "Bajo receta" else "Venta libre")
                }
            }
        }
        if (state.lista.isEmpty() && !state.cargando && state.error == null) {
            item { Text("No hay presentaciones disponibles.") }
        }
        if (state.cargando) item { IndicadorCargando() }
        if (state.error != null && !state.cargando) {
            item { Button(onClick = vm::reintentar) { Text("Reintentar") } }
        }
        if (!state.ultimaPagina && !state.cargando) {
            item { Button(onClick = vm::mas) { Text("Cargar más") } }
        }
    }
}
