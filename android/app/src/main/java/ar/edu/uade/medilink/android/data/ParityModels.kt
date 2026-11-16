package ar.edu.uade.medilink.android.data

data class SuspensionDto(
    val id: Long, val fechaHoraInicio: String, val fechaHoraFin: String,
    val vigente: Boolean, val motivo: String,
)
data class MeDto(val perfil: PerfilDto, val suspensionVigente: SuspensionDto?)
data class CambiarClaveRequest(val contrasenaActual: String, val nuevaContrasena: String)
