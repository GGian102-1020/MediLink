package ar.edu.uade.medilink.android.data

/** Aproximación voluntaria: máximo cuatro decimales antes de una solicitud de ruta. */
data class PuntoGeo(val lat: Double, val lng: Double) {
    init {
        require(lat in -90.0..90.0 && lng in -180.0..180.0 && lat.isFinite() && lng.isFinite())
    }
    fun redondeado() = PuntoGeo(kotlin.math.round(lat * 10000) / 10000,
        kotlin.math.round(lng * 10000) / 10000)
}
