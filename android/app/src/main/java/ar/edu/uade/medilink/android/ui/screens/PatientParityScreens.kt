package ar.edu.uade.medilink.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.edu.uade.medilink.android.data.MeDto
import ar.edu.uade.medilink.android.data.MediLinkRepository
import ar.edu.uade.medilink.android.data.ReservaDto
import ar.edu.uade.medilink.android.data.mensajeDeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PanelPaciente(repository: MediLinkRepository, onCatalogo: () -> Unit,
    onReservas: () -> Unit) {
    var perfil by remember { mutableStateOf<MeDto?>(null) }
    var reservas by remember { mutableStateOf<List<ReservaDto>>(emptyList()) }
    var cantidadCatalogo by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableStateOf(0) }
    LaunchedEffect(repository.session.token, revision) {
        try {
            perfil = repository.miPerfil()
            reservas = repository.misReservas(0).content
            cantidadCatalogo = repository.medicamentos("", 0).totalElements
            error = null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = mensajeDeError(e) }
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Encabezado("Hola, ${perfil?.perfil?.nombre ?: "bienvenido"}", "Tu panel personal de MediLink")
        perfil?.suspensionVigente?.takeIf { it.vigente }?.let {
            AvisoError("Tu cuenta está suspendida hasta ${it.fechaHoraFin}: ${it.motivo}")
        }
        Text("Reservas en la página: ${reservas.size}")
        Text("Reservas activas en la página: ${reservas.count { it.estado == "ACTIVA" }}")
        cantidadCatalogo?.let { Text("Medicamentos del catálogo: $it") }
        Button(onClick = onCatalogo) { Text("Buscar medicamentos") }
        OutlinedButton(onClick = onReservas) { Text("Ver todas mis reservas") }
        OutlinedButton(onClick = { revision++ }) { Text("Actualizar panel") }
        AvisoError(error)
        reservas.take(5).forEach { r ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${r.codigoVisible} · ${r.estado}")
                    Text("${r.medicamentoNombreSnapshot} · ${r.farmaciaNombreSnapshot}")
                }
            }
        }
    }
}

@Composable
fun DetalleReservaPaciente(repository: MediLinkRepository, codigo: String, onVolver: () -> Unit) {
    val scope = rememberCoroutineScope()
    var reserva by remember(codigo) { mutableStateOf<ReservaDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableStateOf(0) }
    var confirmacion by remember { mutableStateOf(false) }
    var procesando by remember { mutableStateOf(false) }
    LaunchedEffect(codigo, revision) {
        try { reserva = repository.detalleReserva(codigo); error = null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = mensajeDeError(e) }
    }
    if (confirmacion) AlertDialog(
        onDismissRequest = { if (!procesando) confirmacion = false },
        title = { Text("Cancelar reserva") },
        text = { Text("La reserva $codigo dejará de estar activa. El servidor gestiona el stock.") },
        confirmButton = { TextButton(enabled = !procesando, onClick = {
            scope.launch {
                procesando = true
                try { repository.cancelar(codigo); confirmacion = false; revision++ }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = mensajeDeError(e); confirmacion = false }
                finally { procesando = false }
            }
        }) { Text("Confirmar cancelación") } },
        dismissButton = { TextButton(onClick = { confirmacion = false }) { Text("Volver") } },
    )
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Encabezado("Reserva $codigo", "Información actualizada por el servidor")
        AvisoError(error)
        reserva?.let { r ->
            Text("Estado: ${r.estado}")
            Text("Medicamento: ${r.medicamentoNombreSnapshot}")
            Text("Presentación: ${r.presentacionDescripcionSnapshot}")
            Text("Cantidad: ${r.cantidad} · ${r.condicionDispensa}")
            Text("Farmacia: ${r.farmaciaNombreSnapshot}")
            Text("Dirección: ${r.farmaciaDireccionSnapshot}")
            Text("Creación: ${r.fechaHoraCreacion}")
            Text("Vencimiento: ${r.fechaHoraVencimiento}")
            if (r.estado == "ACTIVA") Text("Tiempo restante informado: ${r.segundosRestantes / 60} minutos")
            r.fechaHoraCancelacion?.let { Text("Cancelación: $it") }
            r.fechaHoraRetiro?.let { Text("Retiro: $it") }
            r.causaCancelacion?.let { Text("Causa: $it") }
            r.motivoCancelacion?.let { Text("Motivo: $it") }
            r.prescripcionCodigo?.let { Text("Prescripción: $it") }
            if (r.estado == "ACTIVA") Button(onClick = { confirmacion = true }) { Text("Cancelar reserva") }
        }
        OutlinedButton(onClick = { revision++ }) { Text("Actualizar") }
        OutlinedButton(onClick = onVolver) { Text("Volver a mis reservas") }
    }
}
