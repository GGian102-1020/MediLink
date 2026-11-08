package ar.edu.uade.medilink.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import ar.edu.uade.medilink.android.data.AjusteStockRequest
import ar.edu.uade.medilink.android.data.CancelacionOperativaRequest
import ar.edu.uade.medilink.android.data.MediLinkRepository
import ar.edu.uade.medilink.android.data.MovimientoStockDto
import ar.edu.uade.medilink.android.data.PageDto
import ar.edu.uade.medilink.android.data.ReservaDto
import ar.edu.uade.medilink.android.data.StockDto
import ar.edu.uade.medilink.android.data.mensajeDeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val estadosReserva = listOf("", "ACTIVA", "COMPLETADA", "CANCELADA", "VENCIDA")
private val causasOperacion = listOf("STOCK_INCONSISTENTE", "PRODUCTO_NO_DISPONIBLE",
    "PRODUCTO_NO_APTO_PARA_ENTREGA", "IMPEDIMENTO_OPERATIVO_FARMACIA", "OTRA_CAUSA", "ERROR_ADMINISTRATIVO")

@Composable
fun ReservasFarmaciaScreen(repository: MediLinkRepository, farmaciaId: Long, rol: String) {
    val scope = rememberCoroutineScope()
    var pagina by remember(farmaciaId) { mutableStateOf(0) }
    var estado by remember(farmaciaId) { mutableStateOf("") }
    var listado by remember(farmaciaId) { mutableStateOf<PageDto<ReservaDto>?>(null) }
    var seleccionado by remember(farmaciaId) { mutableStateOf("") }
    var codigoManual by remember { mutableStateOf("") }
    var detalle by remember(farmaciaId) { mutableStateOf<ReservaDto?>(null) }
    var causa by remember { mutableStateOf("PRODUCTO_NO_DISPONIBLE") }
    var motivo by remember { mutableStateOf("") }
    var restitucion by remember { mutableStateOf("RESTITUIR") }
    var stockReal by remember { mutableStateOf("0") }
    var confirmando by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableStateOf(0) }

    LaunchedEffect(farmaciaId, pagina, estado, revision) {
        try {
            listado = repository.parity.reservasFarmacia(farmaciaId, estado.ifBlank { null }, pagina)
            error = null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = mensajeDeError(e) }
    }
    LaunchedEffect(farmaciaId, seleccionado, revision) {
        detalle = null
        if (seleccionado.isNotBlank()) {
            try { detalle = repository.parity.detalleFarmacia(farmaciaId, seleccionado) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = mensajeDeError(e) }
        }
    }

    val confirmar = confirmando
    if (confirmar != null) {
        AlertDialog(
            onDismissRequest = { if (!ocupado) confirmando = null },
            title = { Text(if (confirmar == "retiro") "Confirmar retiro" else "Cancelación operativa") },
            text = { Text(if (confirmar == "retiro")
                "Se marcará la reserva ${detalle?.codigoVisible} como COMPLETADA."
                else "El servidor aplicará la política de stock correspondiente a $causa.") },
            confirmButton = { TextButton(enabled = !ocupado && detalle != null, onClick = {
                val r = detalle ?: return@TextButton
                scope.launch {
                    ocupado = true; error = null; aviso = null
                    try {
                        if (confirmar == "retiro") repository.parity.retirar(farmaciaId, r.codigoVisible)
                        else {
                            val real = if (causa == "STOCK_INCONSISTENTE")
                                stockReal.toIntOrNull()?.takeIf { it >= 0 }
                                    ?: throw IllegalArgumentException("Ingresá stock real válido.")
                                else null
                            if (causa != "STOCK_INCONSISTENTE" && motivo.isBlank())
                                throw IllegalArgumentException("Ingresá un motivo de cancelación.")
                            repository.parity.cancelarOperativa(farmaciaId, r.codigoVisible,
                                CancelacionOperativaRequest(
                                    causa = causa,
                                    motivo = if (causa == "STOCK_INCONSISTENTE") null else motivo.trim(),
                                    restitucionStock = if (causa == "OTRA_CAUSA" || causa == "ERROR_ADMINISTRATIVO")
                                        restitucion else null,
                                    cantidadStockReal = real,
                                ))
                        }
                        confirmando = null
                        aviso = "Operación registrada correctamente."
                        revision++
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = mensajeDeError(e); confirmando = null; revision++ }
                    finally { ocupado = false }
                }
            }) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = { confirmando = null }) { Text("Volver") } },
        )
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Encabezado("Reservas de farmacia #$farmaciaId", "Consulta, retiro y cancelación operativa")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { revision++ }) { Text("Actualizar") }
            OutlinedButton(onClick = { if (pagina > 0) pagina-- }, enabled = pagina > 0) { Text("Anterior") }
            OutlinedButton(onClick = { pagina++ }, enabled = listado?.last == false) { Text("Siguiente") }
        }
        Text("Página ${pagina + 1} · Total ${listado?.totalElements ?: 0}")
        ComboSimple("Estado", estadosReserva, estado) { estado = it; pagina = 0 }
        AvisoError(error)
        aviso?.let { Text(it) }
        listado?.content?.forEach { r ->
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(r.codigoVisible, style = MaterialTheme.typography.titleMedium)
                    EstadoPildora(r.estado)
                    Text("${r.medicamentoNombreSnapshot} · ${r.presentacionDescripcionSnapshot}")
                    Text("Usuario: ${r.usuarioNombre.orEmpty()} ${r.usuarioApellido.orEmpty()}")
                    Text("Cantidad: ${r.cantidad} · ${r.fechaHoraCreacion}")
                    OutlinedButton(onClick = { seleccionado = r.codigoVisible }) { Text("Ver y gestionar") }
                }
            }
        }
        OutlinedTextField(value = codigoManual, onValueChange = { codigoManual = it },
            label = { Text("Buscar reserva por código") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { seleccionado = codigoManual.trim() }, enabled = codigoManual.isNotBlank()) {
            Text("Buscar código")
        }
        detalle?.let { r ->
            Encabezado("Detalle ${r.codigoVisible}", r.estado)
            Text("Medicamento: ${r.medicamentoNombreSnapshot} · ${r.presentacionDescripcionSnapshot}")
            Text("Usuario: ${r.usuarioNombre.orEmpty()} ${r.usuarioApellido.orEmpty()}")
            Text("Creada: ${r.fechaHoraCreacion} · Vence: ${r.fechaHoraVencimiento}")
            r.fechaHoraRetiro?.let { Text("Retirada: $it") }
            r.causaCancelacion?.let { Text("Causa: $it") }
            r.motivoCancelacion?.let { Text("Motivo: $it") }
            if (r.estado == "ACTIVA") {
                if (rol == "OPERADOR") Button(enabled = !ocupado,
                    onClick = { confirmando = "retiro" }) { Text("Confirmar retiro") }
                ComboSimple("Causa", causasOperacion.filter { it != "ERROR_ADMINISTRATIVO" || rol == "ADMINISTRADOR" },
                    causa) { causa = it }
                if (causa == "STOCK_INCONSISTENTE") {
                    OutlinedTextField(stockReal, { stockReal = it },
                        label = { Text("Stock libre real") }, modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(motivo, { motivo = it }, label = { Text("Motivo") },
                        modifier = Modifier.fillMaxWidth())
                }
                if (causa == "OTRA_CAUSA" || causa == "ERROR_ADMINISTRATIVO") {
                    ComboSimple("Restitución de stock", listOf("RESTITUIR", "NO_RESTITUIR"), restitucion) {
                        restitucion = it
                    }
                }
                OutlinedButton(enabled = !ocupado,
                    onClick = { confirmando = "cancelacion" }) { Text("Cancelación operativa") }
            }
            OutlinedButton(onClick = { seleccionado = "" }) { Text("Cerrar detalle") }
        }
    }
}

@Composable
fun StockFarmaciaScreen(repository: MediLinkRepository, farmaciaId: Long) {
    val scope = rememberCoroutineScope()
    var pagina by remember(farmaciaId) { mutableStateOf(0) }
    var listado by remember(farmaciaId) { mutableStateOf<PageDto<StockDto>?>(null) }
    var revision by remember { mutableStateOf(0) }
    var presentacionId by remember { mutableStateOf("") }
    var cantidad by remember { mutableStateOf("0") }
    var motivo by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf(false) }
    var ocupado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(farmaciaId, pagina, revision) {
        try { listado = repository.parity.stock(farmaciaId, pagina); error = null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = mensajeDeError(e) }
    }
    if (confirmar) AlertDialog(
        onDismissRequest = { if (!ocupado) confirmar = false },
        title = { Text("Confirmar ajuste de stock") },
        text = { Text("Presentación #$presentacionId: saldo final $cantidad. El movimiento será auditado.") },
        confirmButton = { TextButton(enabled = !ocupado, onClick = {
            scope.launch {
                ocupado = true
                try {
                    val id = presentacionId.toLongOrNull()?.takeIf { it > 0 }
                        ?: throw IllegalArgumentException("ID de presentación inválido")
                    val n = cantidad.toIntOrNull()?.takeIf { it >= 0 }
                        ?: throw IllegalArgumentException("Cantidad inválida")
                    require(motivo.isNotBlank()) { "El motivo es obligatorio" }
                    repository.parity.ajustarStock(farmaciaId, id, AjusteStockRequest(n, motivo.trim()))
                    confirmar = false; presentacionId = ""; motivo = ""; mensaje = "Stock actualizado"
                    revision++
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = mensajeDeError(e); confirmar = false }
                finally { ocupado = false }
            }
        }) { Text("Actualizar stock") } },
        dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Volver") } },
    )
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Encabezado("Stock de farmacia #$farmaciaId", "Saldo disponible y ajustes auditados")
        AvisoError(error)
        mensaje?.let { Text(it) }
        OutlinedButton(onClick = { revision++ }) { Text("Actualizar") }
        listado?.content?.forEach { s ->
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Presentación #${s.presentacionId}: ${s.cantidadDisponible} unidades")
                    Text("Actualización: ${s.fechaHoraActualizacion}")
                    OutlinedButton(onClick = {
                        presentacionId = s.presentacionId.toString(); cantidad = s.cantidadDisponible.toString()
                    }) { Text("Ajustar este stock") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (pagina > 0) pagina-- }, enabled = pagina > 0) { Text("Anterior") }
            OutlinedButton(onClick = { pagina++ }, enabled = listado?.last == false) { Text("Siguiente") }
        }
        Text("Página ${pagina + 1} · Total ${listado?.totalElements ?: 0}")
        Encabezado("Nuevo ajuste", "Se permite crear el saldo de una presentación no registrada")
        OutlinedTextField(presentacionId, { presentacionId = it }, label = { Text("ID de presentación") },
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(cantidad, { cantidad = it }, label = { Text("Cantidad disponible") },
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(motivo, { motivo = it }, label = { Text("Motivo de ajuste") },
            modifier = Modifier.fillMaxWidth())
        Button(enabled = !ocupado && presentacionId.toLongOrNull()?.let { it > 0 } == true &&
            cantidad.toIntOrNull()?.let { it >= 0 } == true && motivo.isNotBlank(),
            onClick = { confirmar = true }) { Text("Revisar ajuste") }
    }
}

@Composable
fun MovimientosFarmaciaScreen(repository: MediLinkRepository, farmaciaId: Long) {
    var pagina by remember(farmaciaId) { mutableStateOf(0) }
    var filtro by remember(farmaciaId) { mutableStateOf("") }
    var aplicado by remember(farmaciaId) { mutableStateOf<Long?>(null) }
    var movimientos by remember { mutableStateOf<PageDto<MovimientoStockDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableStateOf(0) }
    LaunchedEffect(farmaciaId, pagina, aplicado, revision) {
        try { movimientos = repository.parity.movimientos(farmaciaId, aplicado, pagina); error = null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = mensajeDeError(e) }
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Encabezado("Movimientos #$farmaciaId", "Auditoría de cambios de stock")
        AvisoError(error)
        OutlinedTextField(filtro, { filtro = it }, label = { Text("ID de presentación (opcional)") },
            modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (filtro.isBlank() || filtro.toLongOrNull()?.let { it > 0 } == true) {
                    aplicado = filtro.toLongOrNull(); pagina = 0; revision++
                } else error = "ID de presentación inválido"
            }) { Text("Filtrar") }
            OutlinedButton(onClick = { revision++ }) { Text("Actualizar") }
        }
        movimientos?.content?.forEach { m ->
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${m.fechaHora} · ${m.tipo}")
                    Text("Saldo: ${m.cantidadAnterior} → ${m.cantidadNueva} (${m.delta})")
                    m.reservaCodigo?.let { Text("Reserva: $it") }
                    m.motivo?.let { Text("Motivo: $it") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (pagina > 0) pagina-- }, enabled = pagina > 0) { Text("Anterior") }
            OutlinedButton(onClick = { pagina++ }, enabled = movimientos?.last == false) { Text("Siguiente") }
        }
        Text("Página ${pagina + 1} · Total ${movimientos?.totalElements ?: 0}")
    }
}

@Composable
fun ComboSimple(label: String, opciones: List<String>, seleccionado: String,
    cambiar: (String) -> Unit) {
    // Elección simple sin dependencias experimentales de menús desplegables.
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label)
        opciones.chunked(3).forEach { chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                chunk.forEach { opcion ->
                    OutlinedButton(onClick = { cambiar(opcion) }, enabled = seleccionado != opcion,
                        modifier = Modifier.weight(1f)) {
                        Text(if (opcion.isBlank()) "Todas" else opcion.replace('_', ' ').lowercase(),
                            maxLines = 2)
                    }
                }
            }
        }
    }
}
