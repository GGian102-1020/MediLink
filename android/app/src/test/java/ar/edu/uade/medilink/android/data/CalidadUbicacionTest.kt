package ar.edu.uade.medilink.android.data

import org.junit.Assert.*
import org.junit.Test

class CalidadUbicacionTest {
    private val gps = MedicionUbicacion(-34.619, -58.438, 8f, 100)
    @Test fun gpsRecienteGanaAUnaPosicionDeRedMasAmplia() {
        val red = gps.copy(latitud = -34.65, precisionMetros = 2_000f, edadMs = 0)
        assertEquals(gps, mejorUbicacion(listOf(red, gps)))
    }
    @Test fun descartaGpsViejoAunqueAsegureSerPreciso() {
        val red = gps.copy(precisionMetros = 80f)
        assertEquals(red, mejorUbicacion(listOf(gps.copy(edadMs = 60_000), red)))
        assertNull(mejorUbicacion(listOf(gps.copy(edadMs = 31_000))))
    }
    @Test fun aceptaAproximadaRecienteSinPresentarlaComoGps() {
        assertEquals(2_500f, mejorUbicacion(listOf(gps.copy(precisionMetros = 2_500f)))!!.precisionMetros)
    }
    @Test fun descartaCoordenadasEdadYPrecisionInvalidas() {
        listOf(gps.copy(latitud = 91.0), gps.copy(longitud = Double.NaN), gps.copy(edadMs = -1),
            gps.copy(precisionMetros = Float.NaN), gps.copy(precisionMetros = 0f),
            gps.copy(precisionMetros = 50_000f)).forEach { assertNull(mejorUbicacion(listOf(it))) }
    }
    @Test fun noInvierteLatitudYLongitudYAnteEmpatePrefiereLaMasReciente() {
        val elegida = mejorUbicacion(listOf(gps.copy(edadMs = 1_000), gps))!!
        assertEquals(-34.619, elegida.latitud, 0.000001)
        assertEquals(-58.438, elegida.longitud, 0.000001)
        assertEquals(100L, elegida.edadMs)
    }
}
