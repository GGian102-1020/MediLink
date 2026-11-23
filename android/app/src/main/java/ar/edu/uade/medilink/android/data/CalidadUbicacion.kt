package ar.edu.uade.medilink.android.data

/** Edad monotónica: cambiar la hora del teléfono no vuelve reciente una posición vieja. */
internal data class MedicionUbicacion(val latitud: Double, val longitud: Double, val precisionMetros: Float, val edadMs: Long) {
    fun esUtilizable(): Boolean = latitud.isFinite() && longitud.isFinite() &&
        latitud in -90.0..90.0 && longitud in -180.0..180.0 &&
        precisionMetros.isFinite() && precisionMetros > 0 && precisionMetros <= 5_000 && edadMs in 0..30_000
}

internal fun mejorUbicacion(mediciones: List<MedicionUbicacion>): MedicionUbicacion? =
    mediciones.filter { it.esUtilizable() }.minWithOrNull(compareBy<MedicionUbicacion> { it.precisionMetros }.thenBy { it.edadMs })
