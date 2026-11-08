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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.edu.uade.medilink.android.data.AdminPage
import ar.edu.uade.medilink.android.data.CambioEstadoRequest
import ar.edu.uade.medilink.android.data.MediLinkRepository
import ar.edu.uade.medilink.android.data.PageDto
import ar.edu.uade.medilink.android.data.PrescripcionCreateRequest
import ar.edu.uade.medilink.android.data.PrescripcionItemRequest
import ar.edu.uade.medilink.android.data.ReservaDto
import ar.edu.uade.medilink.android.data.SuspensionDto
import ar.edu.uade.medilink.android.data.mensajeDeError
import ar.edu.uade.medilink.android.data.number
import ar.edu.uade.medilink.android.data.string
import com.google.gson.JsonObject
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AdminUsuariosScreen(repository: MediLinkRepository, onDetalle: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    var correo by remember { mutableStateOf("") }
    var estado by remember { mutableStateOf("") }
    var correoAplicado by remember { mutableStateOf("") }
    var estadoAplicado by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(0) }
    var revision by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<AdminPage?>(null) }
    var pendiente by remember { mutableStateOf<JsonObject?>(null) }
    var motivo by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    LaunchedEffect(correoAplicado, estadoAplicado, page, revision) {
        try {
            result = repository.parity.adminList("api/admin/usuarios",
                mapOf("page" to page.toString(), "size" to "20") +
                    mapOf("correo" to correoAplicado, "estadoCuenta" to estadoAplicado).filterValues { it.isNotBlank() })
            error = null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = mensajeDeError(e) }
    }
    pendiente?.let { usuario ->
        val activa = usuario.string("estadoCuenta") == "ACTIVA"
        AlertDialog(
            onDismissRequest = { if (!ocupado) pendiente = null },
            title = { Text(if (activa) "Deshabilitar cuenta" else "Reactivar cuenta") },
            text = { Column {
                Text("Cuenta #${usuario.number("cuentaId")}. Se exige motivo registrado en auditoría.")
                OutlinedTextField(motivo, { motivo = it }, label = { Text("Motivo") })
            } },
            confirmButton = { TextButton(enabled = !ocupado && motivo.isNotBlank(), onClick = {
                scope.launch {
                    ocupado = true
                    try {
                        repository.parity.adminChangeState(
                            "api/admin/cuentas/${usuario.number("cuentaId")}/${if (activa) "deshabilitacion" else "reactivacion"}",
                            CambioEstadoRequest(motivo.trim()))
                        pendiente = null; revision++
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = mensajeDeError(e); pendiente = null }
                    finally { ocupado = false }
                }
            }) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = { pendiente = null }) { Text("Volver") } },
        )
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Encabezado("Usuarios", "Cuentas, historial y suspensiones")
        AvisoError(error)
        OutlinedTextField(correo, { correo = it }, label = { Text("Filtrar por correo") },
            modifier = Modifier.fillMaxWidth())
        ComboSimple("Estado", listOf("", "ACTIVA", "DESHABILITADA"), estado) { estado = it }
        Button(onClick = { correoAplicado = correo.trim(); estadoAplicado = estado; page = 0; revision++ }) {
            Text("Aplicar filtros")
        }
        result?.content?.forEach { row ->
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${row.string("nombre")} ${row.string("apellido")}", style = MaterialTheme.typography.titleMedium)
                    Text("${row.string("correo")} · ${row.string("estadoCuenta")}")
                    OutlinedButton(onClick = { onDetalle(row.number("usuarioId")) }) {
                        Text("Reservas y suspensiones")
                    }
                    OutlinedButton(onClick = { pendiente = row; motivo = "" }) {
                        Text(if (row.string("estadoCuenta") == "ACTIVA") "Deshabilitar" else "Reactivar")
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Text("Anterior") }
            OutlinedButton(onClick = { page++ }, enabled = result?.last == false) { Text("Siguiente") }
        }
        Text("Página ${page + 1} · Total ${result?.totalElements ?: 0}")
    }
}

@Composable
fun AdminUsuarioDetalleScreen(repository: MediLinkRepository, usuarioId: Long) {
    var tab by remember(usuarioId) { mutableStateOf("reservas") }
    var filtro by remember(usuarioId) { mutableStateOf("") }
    var page by remember(usuarioId, tab) { mutableStateOf(0) }
    var revision by remember { mutableStateOf(0) }
    var reservas by remember { mutableStateOf<PageDto<ReservaDto>?>(null) }
    var suspensiones by remember { mutableStateOf<PageDto<SuspensionDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(usuarioId, tab, page, filtro, revision) {
        try {
            val base = "api/admin/usuarios/$usuarioId"
            if (tab == "reservas") reservas = repository.parity.adminReservations("$base/reservas",
                mapOf("page" to page.toString(), "size" to "20") +
                    (if (filtro.isBlank()) emptyMap() else mapOf("estado" to filtro)))
            else suspensiones = repository.parity.adminSuspensions("$base/suspensiones",
                mapOf("page" to page.toString(), "size" to "20"))
            error = null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = mensajeDeError(e) }
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Encabezado("Usuario #$usuarioId", "Historial administrativo")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { tab = "reservas" }) { Text("Reservas") }
            OutlinedButton(onClick = { tab = "suspensiones" }) { Text("Suspensiones") }
        }
        AvisoError(error)
        if (tab == "reservas") {
            ComboSimple("Estado", listOf("", "ACTIVA", "COMPLETADA", "CANCELADA", "VENCIDA"), filtro) {
                filtro = it; page = 0
            }
            reservas?.content?.forEach { r ->
                Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${r.codigoVisible} · ${r.estado}")
                        Text("${r.medicamentoNombreSnapshot} · ${r.farmaciaNombreSnapshot}")
                        Text("${r.fechaHoraCreacion} · ${r.cantidad} unidades")
                    }
                }
            }
        } else suspensiones?.content?.forEach { s ->
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Suspensión #${s.id} · ${if (s.vigente) "Vigente" else "Histórica"}")
                    Text("${s.fechaHoraInicio} → ${s.fechaHoraFin}")
                    Text(s.motivo)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Text("Anterior") }
            OutlinedButton(onClick = { page++ }, enabled = if (tab == "reservas") reservas?.last == false
                else suspensiones?.last == false) { Text("Siguiente") }
            OutlinedButton(onClick = { revision++ }) { Text("Actualizar") }
        }
    }
}

@Composable
fun AdminPrescripcionesScreen(repository: MediLinkRepository) {
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(0) }
    var revision by remember { mutableStateOf(0) }
    val filtros = remember { mutableStateMapOf<String, String>() }
    var filtrosAplicados by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var lista by remember { mutableStateOf<AdminPage?>(null) }
    var detalle by remember { mutableStateOf<JsonObject?>(null) }
    var mostrarForm by remember { mutableStateOf(false) }
    var codigo by remember { mutableStateOf("") }
    var usuarioId by remember { mutableStateOf("") }
    var emision by remember { mutableStateOf("") }
    var vencimiento by remember { mutableStateOf("") }
    val items = remember { mutableStateListOf("" to "1") }
    var pendienteAnular by remember { mutableStateOf<JsonObject?>(null) }
    var motivo by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(page, revision, filtrosAplicados) {
        try {
            lista = repository.parity.adminList("api/admin/prescripciones",
                filtrosAplicados + mapOf("page" to page.toString(), "size" to "20"))
            error = null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = mensajeDeError(e) }
    }
    pendienteAnular?.let { row ->
        AlertDialog(
            onDismissRequest = { if (!ocupado) pendienteAnular = null },
            title = { Text("Anular prescripción") },
            text = { Column {
                Text("Código ${row.string("codigoExterno")}. Esta acción quedará auditada.")
                OutlinedTextField(motivo, { motivo = it }, label = { Text("Motivo") })
            } },
            confirmButton = { TextButton(enabled = !ocupado && motivo.isNotBlank(), onClick = {
                scope.launch {
                    ocupado = true
                    try {
                        repository.parity.adminChangeState(
                            "api/admin/prescripciones/${row.number("id")}/anulacion", CambioEstadoRequest(motivo.trim()))
                        pendienteAnular = null; aviso = "Prescripción anulada"; revision++
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = mensajeDeError(e); pendienteAnular = null }
                    finally { ocupado = false }
                }
            }) { Text("Anular") } },
            dismissButton = { TextButton(onClick = { pendienteAnular = null }) { Text("Volver") } },
        )
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Encabezado("Prescripciones simuladas", "Administración de recetas de demostración")
        AvisoError(error)
        aviso?.let { Text(it) }
        listOf("usuarioId" to "ID usuario", "codigoExterno" to "Código", "estado" to "Estado").forEach { (k, label) ->
            OutlinedTextField(filtros[k].orEmpty(), { filtros[k] = it }, label = { Text(label) },
                modifier = Modifier.fillMaxWidth())
        }
        Button(onClick = {
            filtrosAplicados = filtros.filterValues { it.isNotBlank() }.toMap(); page = 0; revision++
        }) { Text("Filtrar") }
        OutlinedButton(onClick = { mostrarForm = !mostrarForm }) { Text("Crear prescripción") }
        if (mostrarForm) {
            OutlinedTextField(codigo, { codigo = it }, label = { Text("Código externo") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(usuarioId, { usuarioId = it }, label = { Text("ID usuario") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(emision, { emision = it }, label = { Text("Emisión (AAAA-MM-DD)") },
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(vencimiento, { vencimiento = it }, label = { Text("Vencimiento (AAAA-MM-DD)") },
                modifier = Modifier.fillMaxWidth())
            items.forEachIndexed { index, item ->
                Text("Ítem ${index + 1}")
                OutlinedTextField(item.first, { items[index] = it to item.second },
                    label = { Text("ID de presentación") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(item.second, { items[index] = item.first to it },
                    label = { Text("Cantidad autorizada") }, modifier = Modifier.fillMaxWidth())
                if (items.size > 1) OutlinedButton(onClick = { items.removeAt(index) }) { Text("Quitar ítem") }
            }
            OutlinedButton(onClick = { items.add("" to "1") }) { Text("Agregar ítem") }
            Button(enabled = !ocupado, onClick = {
                scope.launch {
                    ocupado = true
                    try {
                        require(codigo.isNotBlank() && codigo.length <= 80) { "Código inválido" }
                        val usuario = usuarioId.toLongOrNull()?.takeIf { it > 0 }
                            ?: throw IllegalArgumentException("ID de usuario inválido")
                        val desde = LocalDate.parse(emision)
                        val hasta = LocalDate.parse(vencimiento)
                        require(!hasta.isBefore(desde)) { "El vencimiento precede a la emisión" }
                        val comandos = items.map { (id, count) ->
                            PrescripcionItemRequest(id.toLongOrNull()?.takeIf { it > 0 }
                                ?: throw IllegalArgumentException("ID de presentación inválido"),
                                count.toIntOrNull()?.takeIf { it > 0 }
                                    ?: throw IllegalArgumentException("Cantidad inválida"))
                        }
                        repository.parity.adminCreate("api/admin/prescripciones",
                            PrescripcionCreateRequest(codigo.trim(), usuario, emision, vencimiento, comandos))
                        mostrarForm = false; aviso = "Prescripción registrada"; revision++
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = mensajeDeError(e) }
                    finally { ocupado = false }
                }
            }) { Text("Guardar prescripción") }
        }
        lista?.content?.forEach { row ->
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${row.string("codigoExterno")} · ${row.string("estado")}")
                    Text("ID usuario: ${row.number("usuarioId")}")
                    Text("${row.string("fechaEmision")} → ${row.string("fechaVencimiento")}")
                    OutlinedButton(onClick = { detalle = if (detalle?.number("id") == row.number("id")) null else row }) {
                        Text("Ver ítems")
                    }
                    if (row.string("estado") == "ACTIVA") OutlinedButton(onClick = {
                        pendienteAnular = row; motivo = ""
                    }) { Text("Anular") }
                }
            }
        }
        detalle?.let { row ->
            Encabezado("Detalle ${row.string("codigoExterno")}", "Cantidades autorizadas y consumidas")
            row.getAsJsonArray("items")?.forEach { elem ->
                val i = elem.asJsonObject
                Text("Presentación #${i.number("presentacionId")}: ${i.string("descripcionPresentacion")}")
                Text("Autorizada ${i.number("cantidadAutorizada")} · Comprometida ${i.number("cantidadComprometida")}")
                Text("Utilizada ${i.number("cantidadUtilizada")} · Disponible ${i.number("cantidadDisponible")}")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Text("Anterior") }
            OutlinedButton(onClick = { page++ }, enabled = lista?.last == false) { Text("Siguiente") }
        }
        Text("Página ${page + 1} · Total ${lista?.totalElements ?: 0}")
    }
}

@Composable
fun AdminAuditoriaScreen(repository: MediLinkRepository) {
    var page by remember { mutableStateOf(0) }
    var revision by remember { mutableStateOf(0) }
    val campos = listOf("usuarioId", "actorCuentaId", "rol", "accion", "tipoEntidad", "entidadId", "desde", "hasta")
    val filtros = remember { mutableStateMapOf<String, String>() }
    var aplicados by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var lista by remember { mutableStateOf<AdminPage?>(null) }
    var seleccionado by remember { mutableStateOf<JsonObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(aplicados, page, revision) {
        try {
            lista = repository.parity.adminList("api/admin/auditoria",
                aplicados + mapOf("page" to page.toString(), "size" to "20"))
            error = null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = mensajeDeError(e) }
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Encabezado("Auditoría", "Trazabilidad de operaciones y cambios del sistema")
        AvisoError(error)
        campos.forEach { clave ->
            OutlinedTextField(filtros[clave].orEmpty(), { filtros[clave] = it },
                label = { Text(if (clave in listOf("desde", "hasta")) "$clave (ISO 8601 con zona)" else clave) },
                modifier = Modifier.fillMaxWidth())
        }
        Button(onClick = {
            try {
                val desde = filtros["desde"].orEmpty().takeIf { it.isNotBlank() }?.let(OffsetDateTime::parse)
                val hasta = filtros["hasta"].orEmpty().takeIf { it.isNotBlank() }?.let(OffsetDateTime::parse)
                require(desde == null || hasta == null || desde.isBefore(hasta)) {
                    "El inicio debe ser anterior al fin" }
                listOf("usuarioId", "actorCuentaId", "entidadId").forEach { key ->
                    val value = filtros[key].orEmpty()
                    if (value.isNotBlank()) require(value.toLongOrNull()?.let { it > 0 } == true) {
                        "ID inválido en $key" }
                }
                aplicados = filtros.filterValues { it.isNotBlank() }.toMap(); page = 0; revision++
            } catch (e: Exception) { error = "Filtros inválidos: ${e.message}" }
        }) { Text("Aplicar filtros") }
        OutlinedButton(onClick = { filtros.clear(); aplicados = emptyMap(); page = 0; revision++ }) {
            Text("Limpiar filtros")
        }
        lista?.content?.forEach { row ->
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${row.string("fechaHora")} · ${row.string("accion")}")
                    Text("${row.string("tipoEntidad")} #${row.string("entidadId")}")
                    Text("Actor ${row.string("actorNombre")} · Rol ${row.string("rol")}")
                    OutlinedButton(onClick = { seleccionado = row }) { Text("Detalle de auditoría") }
                }
            }
        }
        seleccionado?.let { row ->
            Encabezado("Auditoría #${row.number("id")}", "Datos de trazabilidad")
            listOf("fechaHora", "actorCuentaId", "actorNombre", "rol", "accion", "tipoEntidad",
                "entidadId", "usuarioRelacionadoId", "motivo", "correlationId").forEach { clave ->
                Text("$clave: ${row.string(clave)}")
            }
            Text("Anterior: ${row.get("resumenAnterior")?.toString() ?: "—"}")
            Text("Posterior: ${row.get("resumenPosterior")?.toString() ?: "—"}")
            OutlinedButton(onClick = { seleccionado = null }) { Text("Cerrar detalle") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Text("Anterior") }
            OutlinedButton(onClick = { page++ }, enabled = lista?.last == false) { Text("Siguiente") }
        }
        Text("Página ${page + 1} · Total ${lista?.totalElements ?: 0}")
    }
}
