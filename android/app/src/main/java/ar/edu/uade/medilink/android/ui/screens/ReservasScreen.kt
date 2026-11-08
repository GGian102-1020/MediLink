package ar.edu.uade.medilink.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.medilink.android.data.ReservaDto
import ar.edu.uade.medilink.android.ui.viewmodel.ReservasVm

@Composable
fun ReservasScreen(vm: ReservasVm, onDetalle: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var aCancelar by remember { mutableStateOf<ReservaDto?>(null) }

    if (aCancelar != null) {
        AlertDialog(
            onDismissRequest = { aCancelar = null },
            title = { Text("Cancelar reserva") },
            text = { Text("¿Querés cancelar la reserva ${aCancelar?.codigoVisible}?") },
            confirmButton = {
                TextButton(onClick = {
                    aCancelar?.let { vm.cancelar(it.codigoVisible) }
                    aCancelar = null
                }) { Text("Sí, cancelar") }
            },
            dismissButton = {
                TextButton(onClick = { aCancelar = null }) { Text("Volver") }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Encabezado("Mis reservas", "Consultá tus reservas y sus estados actualizados.") }
        item { OutlinedButton(onClick = vm::actualizar, enabled = !state.cargando) { Text("Actualizar") } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("" to "Todas", "ACTIVA" to "Activas", "COMPLETADA" to "Completadas",
                    "CANCELADA" to "Canceladas", "VENCIDA" to "Vencidas").forEach { (value, label) ->
                    OutlinedButton(onClick = { vm.filtrar(value) },
                        enabled = !state.cargando && state.filtro != value) { Text(label) }
                }
            }
        }
        item { AvisoError(state.error) }
        items(state.lista, key = { it.id }) { reserva ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text("Código: ${reserva.codigoVisible}")
                    Text("${reserva.medicamentoNombreSnapshot} · ${reserva.presentacionDescripcionSnapshot}")
                    Text("Farmacia: ${reserva.farmaciaNombreSnapshot}")
                    Text(reserva.farmaciaDireccionSnapshot)
                    Text("Cantidad: ${reserva.cantidad} · Estado: ${reserva.estado}")
                    Text("Vencimiento: ${reserva.fechaHoraVencimiento}")
                    OutlinedButton(onClick = { onDetalle(reserva.codigoVisible) }) { Text("Ver detalle") }
                    if (reserva.estado == "ACTIVA") {
                        Button(
                            onClick = { aCancelar = reserva },
                            enabled = state.cancelandoCodigo == null,
                        ) {
                            Text(if (state.cancelandoCodigo == reserva.codigoVisible) "Cancelando..." else "Cancelar reserva")
                        }
                    }
                }
            }
        }
        if (state.lista.isEmpty() && !state.cargando && state.error == null) {
            item { Text("Todavía no tenés reservas.") }
        }
        if (state.cargando) item { IndicadorCargando() }
        if (!state.ultimaPagina && !state.cargando) {
            item { Button(onClick = vm::mas) { Text("Cargar más") } }
        }
    }
}
