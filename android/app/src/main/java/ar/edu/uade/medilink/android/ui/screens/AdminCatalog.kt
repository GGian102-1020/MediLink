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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.edu.uade.medilink.android.data.AdminPage
import ar.edu.uade.medilink.android.data.CambioEstadoRequest
import ar.edu.uade.medilink.android.data.FarmaciaRequest
import ar.edu.uade.medilink.android.data.MedicamentoRequest
import ar.edu.uade.medilink.android.data.MediLinkRepository
import ar.edu.uade.medilink.android.data.OperadorCreateRequest
import ar.edu.uade.medilink.android.data.OperadorUpdateRequest
import ar.edu.uade.medilink.android.data.PresentacionCreateRequest
import ar.edu.uade.medilink.android.data.mensajeDeError
import ar.edu.uade.medilink.android.data.number
import ar.edu.uade.medilink.android.data.string
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Los campos son una transliteración de los formularios de frontend/src/pages/Admin.tsx. */
data class CampoAdmin(val clave: String, val titulo: String, val obligatorio: Boolean = false,
    val tipo: String = "text", val max: Int = 0, val opciones: List<String> = emptyList())

private val camposFarmacia = listOf(
    CampoAdmin("nombre", "Nombre", true, max = 120),
    CampoAdmin("direccion", "Dirección", true, max = 200),
    CampoAdmin("telefono", "Teléfono", max = 30),
    CampoAdmin("emailContacto", "Correo de contacto", max = 254),
    CampoAdmin("latitud", "Latitud", tipo = "decimal"),
    CampoAdmin("longitud", "Longitud", tipo = "decimal"),
)
private val camposMedicamento = listOf(CampoAdmin("nombre", "Nombre", true, max = 160))
private val camposPresentacion = listOf(
    CampoAdmin("medicamentoId", "ID de medicamento", true, "entero"),
    CampoAdmin("concentracionTexto", "Concentración", true, max = 80),
    CampoAdmin("formaFarmaceutica", "Forma farmacéutica", true, max = 80),
    CampoAdmin("cantidadEnvase", "Cantidad del envase", true, "entero"),
    CampoAdmin("unidadExpendio", "Unidad de expendio", true, max = 80),
    CampoAdmin("condicionDispensa", "Condición de dispensa", true, "opcion",
        opciones = listOf("VENTA_LIBRE", "BAJO_RECETA")),
)
private val camposOperador = listOf(
    CampoAdmin("nombre", "Nombre", true, max = 80),
    CampoAdmin("apellido", "Apellido", true, max = 80),
    CampoAdmin("correo", "Correo", true),
    CampoAdmin("contrasenaInicial", "Contraseña inicial", true),
    CampoAdmin("farmaciaId", "ID de farmacia", true, "entero"),
)

enum class AdminSection(val titulo: String, val api: String, val idField: String,
    val estadoField: String, val fields: List<CampoAdmin>, val filters: List<CampoAdmin>) {
    FARMACIAS("Farmacias", "api/admin/farmacias", "id", "estado", camposFarmacia,
        listOf(CampoAdmin("nombre", "Nombre"), CampoAdmin("estado", "Estado", opciones = listOf("ACTIVA", "INACTIVA")))),
    MEDICAMENTOS("Medicamentos", "api/admin/medicamentos", "id", "estado", camposMedicamento,
        listOf(CampoAdmin("nombre", "Nombre"), CampoAdmin("estado", "Estado", opciones = listOf("ACTIVO", "INACTIVO")))),
    PRESENTACIONES("Presentaciones", "api/admin/presentaciones", "id", "estado", camposPresentacion,
        listOf(CampoAdmin("medicamentoId", "ID medicamento", tipo = "entero"),
            CampoAdmin("estado", "Estado", opciones = listOf("ACTIVA", "INACTIVA")),
            CampoAdmin("condicionDispensa", "Dispensa", opciones = listOf("VENTA_LIBRE", "BAJO_RECETA")))),
    OPERADORES("Operadores", "api/admin/operadores", "operadorId", "estadoCuenta", camposOperador,
        listOf(CampoAdmin("farmaciaId", "ID farmacia", tipo = "entero"),
            CampoAdmin("estadoCuenta", "Estado", opciones = listOf("ACTIVA", "DESHABILITADA")),
            CampoAdmin("correo", "Correo"))),
}

fun validarCampos(campos: List<CampoAdmin>, valores: Map<String, String>) {
    campos.forEach { campo ->
        val valor = valores[campo.clave].orEmpty().trim()
        if (campo.obligatorio && valor.isBlank())
            throw IllegalArgumentException("El campo ${campo.titulo} es obligatorio.")
        if (campo.max > 0 && valor.length > campo.max)
            throw IllegalArgumentException("${campo.titulo}: máximo ${campo.max} caracteres.")
        if (valor.isNotBlank() && campo.tipo == "entero" && valor.toLongOrNull()?.let { it > 0 } != true)
            throw IllegalArgumentException("${campo.titulo}: ingresá un entero positivo.")
        if (valor.isNotBlank() && campo.tipo == "decimal" && valor.toDoubleOrNull() == null)
            throw IllegalArgumentException("${campo.titulo}: número decimal inválido.")
        if (campo.opciones.isNotEmpty() && valor.isNotBlank() && valor !in campo.opciones)
            throw IllegalArgumentException("${campo.titulo}: opción inválida.")
    }
}

/** Comandos tipados; jamás se envía el Map de campos de la pantalla sin validar. */
fun comandoAdmin(section: AdminSection, values: Map<String, String>, esNuevo: Boolean): Any {
    val campos = if (section == AdminSection.OPERADORES && !esNuevo) section.fields.take(2) else section.fields
    validarCampos(campos, values)
    fun s(key: String) = values[key].orEmpty().trim()
    fun nullable(key: String) = s(key).ifBlank { null }
    fun long(key: String) = s(key).toLong()
    fun int(key: String) = s(key).toInt()
    return when (section) {
        AdminSection.FARMACIAS -> {
            val lat = nullable("latitud")?.toDouble()
            val lng = nullable("longitud")?.toDouble()
            require((lat == null) == (lng == null)) { "Latitud y longitud se completan juntas." }
            require(lat == null || lat in -90.0..90.0) { "Latitud fuera de rango." }
            require(lng == null || lng in -180.0..180.0) { "Longitud fuera de rango." }
            FarmaciaRequest(s("nombre"), s("direccion"), nullable("telefono"),
                nullable("emailContacto"), lat, lng)
        }
        AdminSection.MEDICAMENTOS -> MedicamentoRequest(s("nombre"))
        AdminSection.PRESENTACIONES -> PresentacionCreateRequest(long("medicamentoId"),
            s("concentracionTexto"), s("formaFarmaceutica"), int("cantidadEnvase"),
            s("unidadExpendio"), s("condicionDispensa"))
        AdminSection.OPERADORES -> if (esNuevo)
            OperadorCreateRequest(s("nombre"), s("apellido"), s("correo"), s("contrasenaInicial"), long("farmaciaId"))
            else OperadorUpdateRequest(s("nombre"), s("apellido"))
    }
}

@Composable
fun AdminCatalogScreen(repository: MediLinkRepository, section: AdminSection,
    abrirFarmacia: (Long) -> Unit, abrirPresentacion: (Long) -> Unit,
    abrirPresentacionesMedicamento: (Long) -> Unit, medicamentoInicial: Long? = null) {
    val scope = rememberCoroutineScope()
    var page by remember(section) { mutableStateOf(0) }
    var lista by remember(section) { mutableStateOf<AdminPage?>(null) }
    var filtersApplied by remember(section, medicamentoInicial) { mutableStateOf<Map<String, String>>(
        if (medicamentoInicial != null) mapOf("medicamentoId" to medicamentoInicial.toString()) else emptyMap()) }
    val filtros = remember(section, medicamentoInicial) { mutableStateMapOf<String, String>().apply {
        if (medicamentoInicial != null) put("medicamentoId", medicamentoInicial.toString())
    } }
    val valores = remember(section) { mutableStateMapOf<String, String>() }
    var revision by remember(section) { mutableStateOf(0) }
    var editando by remember(section) { mutableStateOf<JsonObject?>(null) }
    var formularioAbierto by remember(section) { mutableStateOf(false) }
    var creando by remember(section) { mutableStateOf(true) }
    var cambiarEstado by remember(section) { mutableStateOf<JsonObject?>(null) }
    var motivo by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var exito by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(section, filtersApplied, page, revision) {
        try {
            lista = repository.parity.adminList(section.api,
                filtersApplied + mapOf("page" to page.toString(), "size" to "20"))
            error = null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = mensajeDeError(e) }
    }

    if (cambiarEstado != null) {
        val fila = cambiarEstado!!
        val estaActivo = fila.string(section.estadoField) in listOf("ACTIVA", "ACTIVO")
        AlertDialog(
            onDismissRequest = { if (!ocupado) cambiarEstado = null },
            title = { Text(if (estaActivo) "Desactivar registro" else "Reactivar registro") },
            text = {
                Column {
                    Text("Acción auditada que afecta al registro #${fila.number(section.idField)}")
                    CampoTexto(motivo, { motivo = it }, label = { Text("Motivo obligatorio") })
                }
            },
            confirmButton = { TextButton(enabled = !ocupado && motivo.isNotBlank(), onClick = {
                scope.launch {
                    ocupado = true
                    try {
                        val id = if (section == AdminSection.OPERADORES) fila.number("cuentaId")
                            else fila.number(section.idField)
                        val url = if (section == AdminSection.OPERADORES)
                            "api/admin/cuentas/$id/${if (estaActivo) "deshabilitacion" else "reactivacion"}"
                        else "${section.api}/$id/${if (estaActivo) "desactivacion" else "reactivacion"}"
                        repository.parity.adminChangeState(url, CambioEstadoRequest(motivo.trim()))
                        exito = "Estado actualizado"; cambiarEstado = null; revision++
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = mensajeDeError(e); cambiarEstado = null }
                    finally { ocupado = false }
                }
            }) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = { cambiarEstado = null }) { Text("Volver") } },
        )
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Encabezado(section.titulo, "Administración · búsqueda, creación y estados")
        AvisoError(error)
        exito?.let { NotaInformativa(it) }
        SeccionTitulo("Buscar y filtrar", "Encontrá registros por sus datos y estado")
        section.filters.forEach { campo ->
            CampoFormulario(campo, filtros[campo.clave].orEmpty()) { filtros[campo.clave] = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                filtersApplied = filtros.filterValues { it.isNotBlank() }.toMap(); page = 0; revision++
            }) { Text("Filtrar") }
            OutlinedButton(onClick = { filtros.clear(); filtersApplied = emptyMap(); page = 0; revision++ }) {
                Text("Limpiar")
            }
        }
        OutlinedButton(onClick = {
            valores.clear(); creando = true; editando = null; formularioAbierto = true; error = null
        }) { Text("Crear ${section.titulo.lowercase()}") }

        if (formularioAbierto) {
            Encabezado(if (creando) "Crear registro" else "Editar registro", "Revisá los datos antes de guardar")
            val fields = if (section == AdminSection.OPERADORES && !creando)
                section.fields.take(2) else section.fields
            fields.forEach { campo ->
                CampoFormulario(campo, valores[campo.clave].orEmpty()) { valores[campo.clave] = it }
            }
            Button(enabled = !ocupado, onClick = {
                scope.launch {
                    ocupado = true; error = null
                    try {
                        val cmd = comandoAdmin(section, valores.toMap(), creando)
                        if (creando) repository.parity.adminCreate(section.api, cmd)
                        else {
                            require(section != AdminSection.PRESENTACIONES) { "Las presentaciones no admiten edición." }
                            val fila = requireNotNull(editando)
                            repository.parity.adminUpdate("${section.api}/${fila.number(section.idField)}", cmd)
                        }
                        formularioAbierto = false; exito = "Registro guardado"; revision++
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = mensajeDeError(e) }
                    finally { ocupado = false }
                }
            }) { Text(if (creando) "Crear" else "Guardar cambios") }
            OutlinedButton(onClick = { formularioAbierto = false }) { Text("Cancelar edición") }
        }

        SeccionTitulo("Registros", "Página ${page + 1} · Total ${lista?.totalElements ?: 0}")
        lista?.content?.forEach { row ->
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val id = row.number(section.idField)
                    val titulo = when (section) {
                        AdminSection.PRESENTACIONES -> "${row.string("medicamentoNombre")} · ${row.string("concentracionTexto")}"
                        AdminSection.OPERADORES -> "${row.string("nombre")} ${row.string("apellido")}"
                        else -> row.string("nombre")
                    }
                    Text("#$id · $titulo", style = MaterialTheme.typography.titleMedium)
                    EstadoPildora(row.string(section.estadoField))
                    when (section) {
                        AdminSection.FARMACIAS -> {
                            Text(row.string("direccion"))
                            OutlinedButton(onClick = { abrirFarmacia(id) }) { Text("Reservas y stock") }
                        }
                        AdminSection.OPERADORES -> {
                            Text(row.string("correo"))
                            Text("Farmacia: ${row.number("farmaciaId")}")
                            OutlinedButton(onClick = { abrirFarmacia(row.number("farmaciaId")) }) { Text("Ver farmacia") }
                        }
                        AdminSection.MEDICAMENTOS -> {
                            Text("Medicamento #$id")
                            OutlinedButton(onClick = { abrirPresentacionesMedicamento(id) }) {
                                Text("Ver presentaciones")
                            }
                        }
                        AdminSection.PRESENTACIONES -> {
                            Text("${row.string("formaFarmaceutica")} · ${row.string("cantidadEnvase")} ${row.string("unidadExpendio")}")
                            OutlinedButton(onClick = { abrirPresentacion(id) }) { Text("Disponibilidad") }
                        }
                    }
                    if (section != AdminSection.PRESENTACIONES) {
                        OutlinedButton(onClick = {
                            creando = false; editando = row; valores.clear()
                            (if (section == AdminSection.OPERADORES) section.fields.take(2)
                            else section.fields).forEach { campo -> valores[campo.clave] = row.string(campo.clave) }
                            formularioAbierto = true
                        }) { Text("Editar") }
                    }
                    OutlinedButton(onClick = { motivo = ""; cambiarEstado = row }) {
                        Text(if (row.string(section.estadoField) in listOf("ACTIVA", "ACTIVO")) "Desactivar" else "Reactivar")
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = page > 0, onClick = { page-- }) { Text("Anterior") }
            OutlinedButton(enabled = lista?.last == false, onClick = { page++ }) { Text("Siguiente") }
            OutlinedButton(onClick = { revision++ }) { Text("Actualizar") }
        }
    }
}

@Composable
fun CampoFormulario(campo: CampoAdmin, valor: String, onValue: (String) -> Unit) {
    if (campo.opciones.isNotEmpty()) {
        ComboSimple(campo.titulo, listOf("") + campo.opciones, valor, onValue)
    } else {
        CampoTexto(value = valor, onValueChange = onValue,
            label = { Text(campo.titulo + if (campo.obligatorio) " *" else "") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            visualTransformation = if (campo.clave == "contrasenaInicial")
                androidx.compose.ui.text.input.PasswordVisualTransformation()
                else androidx.compose.ui.text.input.VisualTransformation.None)
    }
}
