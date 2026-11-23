package ar.edu.uade.medilink.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.medilink.android.data.ReservaDto
import ar.edu.uade.medilink.android.ui.viewmodel.ReservasVm
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun ReservasScreen(vm: ReservasVm, onDetalle: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var aCancelar by remember { mutableStateOf<ReservaDto?>(null) }
    if (aCancelar != null) {
        AlertDialog(onDismissRequest = { aCancelar = null },
            title = { Text("Cancelar reserva") },
            text = { Text("¿Querés cancelar la reserva ${aCancelar?.codigoVisible}? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    aCancelar?.let { vm.cancelar(it.codigoVisible) }
                    aCancelar = null
                }) { Text("Sí, cancelar") }
            },
            dismissButton = { TextButton(onClick = { aCancelar = null }) { Text("Volver") } })
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = screenPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Encabezado("Mis reservas", "Tocá una reserva para ver su código y PIN de retiro.") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.cargando && state.lista.isEmpty()) "Cargando…"
                    else plural(state.lista.size, "reserva", "reservas"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                IconButton(onClick = vm::actualizar, enabled = !state.cargando) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("" to "Todas", "PENDIENTE" to "Por confirmar", "ACTIVA" to "Activas", "COMPLETADA" to "Completadas",
                    "CANCELADA" to "Canceladas", "VENCIDA" to "Vencidas").forEach { (value, label) ->
                    FilterChip(selected = state.filtro == value, onClick = { vm.filtrar(value) },
                        enabled = !state.cargando, label = { Text(label) })
                }
            }
        }
        item { AvisoError(state.error) }
        items(state.lista, key = { it.id }) { reserva ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onDetalle(reserva.codigoVisible) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.EventNote, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                            Text(reserva.codigoVisible, style = MaterialTheme.typography.titleMedium)
                        }
                        EstadoPildora(reserva.estado)
                    }
                    Text(reserva.medicamentoNombreSnapshot, style = MaterialTheme.typography.titleLarge)
                    Text(reserva.presentacionDescripcionSnapshot,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(Icons.Default.LocalPharmacy, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text(reserva.farmaciaNombreSnapshot, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(if (reserva.estado == "PENDIENTE") "${plural(reserva.cantidad, "unidad", "unidades")} · Esperando que la farmacia confirme tu receta"
                        else "${plural(reserva.cantidad, "unidad", "unidades")} · ${if (reserva.estado == "ACTIVA") "Vence" else "Creada"} ${fechaLegible(if (reserva.estado == "ACTIVA") reserva.fechaHoraVencimiento else reserva.fechaHoraCreacion)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (reserva.estado == "ACTIVA" || reserva.estado == "PENDIENTE") {
                        OutlinedButton(onClick = { aCancelar = reserva },
                            enabled = state.cancelandoCodigo == null,
                            modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.cancelandoCodigo == reserva.codigoVisible) "Cancelando..." else "Cancelar reserva",
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        if (state.lista.isEmpty() && !state.cargando && state.error == null) {
            item { VacioEstado("No hay reservas para mostrar",
                if (state.filtro.isBlank()) "Cuando hagas una reserva, la vas a encontrar acá." else "Probá con otro filtro.") }
        }
        if (state.cargando) item { IndicadorCargando() }
        if (!state.ultimaPagina && !state.cargando) {
            item { Button(onClick = vm::mas, modifier = Modifier.fillMaxWidth()) { Text("Mostrar más reservas") } }
        }
    }
}
