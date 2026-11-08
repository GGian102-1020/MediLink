package ar.edu.uade.medilink.android.data

import com.google.gson.JsonObject

data class SuspensionDto(
    val id: Long, val fechaHoraInicio: String, val fechaHoraFin: String,
    val vigente: Boolean, val motivo: String,
)
data class MeDto(val perfil: PerfilDto, val suspensionVigente: SuspensionDto?)
data class CambiarClaveRequest(val contrasenaActual: String, val nuevaContrasena: String)
data class StockDto(val farmaciaId: Long, val presentacionId: Long, val cantidadDisponible: Int,
    val fechaHoraActualizacion: String)
data class MovimientoStockDto(
    val id: Long, val stockId: Long, val tipo: String, val cantidadAnterior: Int,
    val cantidadNueva: Int, val delta: Int, val fechaHora: String,
    val reservaCodigo: String?, val actorCuentaId: Long?, val motivo: String?,
)
data class AjusteStockRequest(val cantidadDisponible: Int, val motivo: String)
data class CancelacionOperativaRequest(
    val causa: String,
    val motivo: String? = null,
    val restitucionStock: String? = null,
    val cantidadStockReal: Int? = null,
)
data class CambioEstadoRequest(val motivo: String)

/** Representación tipada de los formularios de negocio publicados por Spring Boot. */
data class FarmaciaRequest(
    val nombre: String, val direccion: String, val telefono: String?,
    val emailContacto: String?, val latitud: Double?, val longitud: Double?,
)
data class MedicamentoRequest(val nombre: String)
data class OperadorCreateRequest(val nombre: String, val apellido: String, val correo: String,
    val contrasenaInicial: String, val farmaciaId: Long)
data class OperadorUpdateRequest(val nombre: String, val apellido: String)
data class PresentacionCreateRequest(
    val medicamentoId: Long, val concentracionTexto: String, val formaFarmaceutica: String,
    val cantidadEnvase: Int, val unidadExpendio: String, val condicionDispensa: String,
)
data class PrescripcionItemRequest(val presentacionId: Long, val cantidadAutorizada: Int)
data class PrescripcionCreateRequest(
    val codigoExterno: String, val usuarioId: Long,
    val fechaEmision: String, val fechaVencimiento: String,
    val items: List<PrescripcionItemRequest>,
)

/** Usamos JsonObject para recursos administrativos heterogéneos, no para los comandos tipados. */
data class AdminPage(val content: List<JsonObject>, val page: Int, val size: Int,
    val totalElements: Long, val totalPages: Int, val first: Boolean, val last: Boolean)

fun JsonObject.string(key: String): String = get(key)?.takeIf { !it.isJsonNull }?.asString.orEmpty()
fun JsonObject.number(key: String): Long = get(key)?.takeIf { !it.isJsonNull }?.asLong ?: 0L
