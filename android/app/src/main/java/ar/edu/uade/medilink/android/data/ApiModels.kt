package ar.edu.uade.medilink.android.data

/** Estos modelos reflejan los records JSON del backend Java, no modelos inventados. */
data class PageDto<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
)

data class MedicamentoDto(val id: Long, val nombre: String, val estado: String)

data class PresentacionDto(
    val id: Long,
    val medicamentoId: Long,
    val medicamentoNombre: String,
    val concentracionTexto: String,
    val formaFarmaceutica: String,
    val cantidadEnvase: Int,
    val unidadExpendio: String,
    val condicionDispensa: String,
    val estado: String,
)

data class DisponibilidadDto(
    val farmaciaId: Long,
    val nombre: String,
    val direccion: String,
    val cantidadDisponible: Int,
    val latitud: Double?,
    val longitud: Double?,
    val distanciaKm: Double?,
)

data class PerfilDto(
    val cuentaId: Long,
    val perfilId: Long,
    val rol: String,
    val correo: String,
    val estadoCuenta: String,
    val nombre: String,
    val apellido: String,
    val farmaciaId: Long?,
)

data class LoginDto(
    val accessToken: String,
    val tokenType: String,
    val expiresInSeconds: Long,
    val perfil: PerfilDto,
)

data class RegistroDto(
    val usuarioId: Long,
    val cuentaId: Long,
    val nombre: String,
    val apellido: String,
    val correo: String,
    val estadoCuenta: String,
    val rol: String,
)

data class LoginRequest(val correo: String, val contrasena: String)
data class RegisterRequest(
    val nombre: String,
    val apellido: String,
    val correo: String,
    val contrasena: String,
    val dni: String? = null,
    /** Formato ISO yyyy-MM-dd. */
    val fechaNacimiento: String? = null,
    val telefono: String? = null,
    val obraSocial: String? = null,
    val numeroAfiliado: String? = null,
)

/** Datos personales del paciente que se piden en el registro. */
data class DatosPersonales(
    val dni: String,
    val fechaNacimiento: java.time.LocalDate,
    val telefono: String? = null,
    val obraSocial: String? = null,
    val numeroAfiliado: String? = null,
)

data class CrearReservaRequest(
    val farmaciaId: Long,
    val presentacionId: Long,
    val cantidad: Int,
    val codigoPrescripcion: String? = null,
)

data class ReservaDto(
    val id: Long,
    val codigoVisible: String,
    val estado: String,
    val cantidad: Int,
    val fechaHoraCreacion: String,
    val fechaHoraVencimiento: String,
    val segundosRestantes: Long,
    val fechaHoraCancelacion: String?,
    val fechaHoraRetiro: String?,
    val farmaciaId: Long,
    val farmaciaNombreSnapshot: String,
    val farmaciaDireccionSnapshot: String,
    val presentacionId: Long,
    val medicamentoNombreSnapshot: String,
    val presentacionDescripcionSnapshot: String,
    val condicionDispensa: String,
    val usuarioNombre: String?,
    val usuarioApellido: String?,
    val origenCancelacion: String?,
    val causaCancelacion: String?,
    val motivoCancelacion: String?,
    val prescripcionCodigo: String?,
)
