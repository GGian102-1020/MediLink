package ar.edu.uade.medilink.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.medilink.android.ui.viewmodel.CatalogoVm
import ar.edu.uade.medilink.android.ui.viewmodel.PresentacionesVm
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun CatalogoScreen(vm: CatalogoVm, onAbrir: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var consulta by rememberSaveable { mutableStateOf("") }
    val teclado = LocalSoftwareKeyboardController.current
    val buscar = { vm.buscar(consulta); teclado?.hide() }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = screenPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Encabezado("Buscar medicamentos", "Escribí el nombre y elegí la presentación que necesitás.") }
        item {
            CampoTexto(
                value = consulta, onValueChange = { consulta = it; vm.buscar(it, demora = 350) },
                placeholder = { Text("Ej.: ibuprofeno") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (consulta.isNotEmpty()) { {
                    IconButton(onClick = { consulta = ""; vm.buscar("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Borrar búsqueda")
                    }
                } } else null,
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { buscar() }),
            )
        }
        item { AvisoError(state.error) }
        if (state.cargando && state.lista.isEmpty()) item { IndicadorCargando() }
        if (state.lista.isNotEmpty()) {
            val total = maxOf(state.total, state.lista.size.toLong())
            val detalle = if (total > state.lista.size)
                "Mostrando ${state.lista.size} de $total. Escribí el nombre para encontrar el tuyo."
            else plural(total, "medicamento", "medicamentos")
            item { SeccionTitulo("Resultados", detalle) }
        }
        items(state.lista, key = { it.id }) { medicamento ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onAbrir(medicamento.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(modifier = Modifier.size(52.dp).background(MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Medication, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(medicamento.nombre, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text("Ver presentaciones y stock", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Ver presentaciones",
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (state.lista.isEmpty() && !state.cargando && state.error == null) {
            item { VacioEstado("Sin resultados", "Probá buscar con otro nombre o revisar la escritura.") }
        }
        if (state.cargando && state.lista.isNotEmpty()) item { IndicadorCargando() }
    }
}

@Composable
fun PresentacionesScreen(vm: PresentacionesVm, onAbrir: (ar.edu.uade.medilink.android.data.PresentacionDto) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = screenPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Encabezado(state.lista.firstOrNull()?.medicamentoNombre ?: "Presentaciones", "Elegí la presentación para ver qué farmacias la tienen.") }
        item { AvisoError(state.error) }
        items(state.lista, key = { it.id }) { item ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onAbrir(item) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Medication, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(item.medicamentoNombre, style = MaterialTheme.typography.titleMedium)
                        Text("${item.concentracionTexto} · ${item.formaFarmaceutica}",
                            style = MaterialTheme.typography.bodyMedium)
                        Text(envaseLegible(item.unidadExpendio, item.cantidadEnvase),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        EstadoPildora(item.condicionDispensa)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Consultar disponibilidad",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (state.lista.isEmpty() && !state.cargando && state.error == null) {
            item { VacioEstado("Sin presentaciones", "No hay presentaciones disponibles por ahora.") }
        }
        if (state.cargando) item { IndicadorCargando() }
        if (state.error != null && !state.cargando) {
            item { Button(onClick = vm::reintentar) { Text("Reintentar") } }
        }
        if (!state.ultimaPagina && !state.cargando) {
            item { Button(onClick = vm::mas, modifier = Modifier.fillMaxWidth()) { Text("Mostrar más") } }
        }
    }
}
