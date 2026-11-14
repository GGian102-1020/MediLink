package ar.edu.uade.medilink.android.ui.screens

import ar.edu.uade.medilink.android.data.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PuntoGeoTest {
    @Test fun redondeoEvitaExponerPrecisionInnecesaria() {
        val p = PuntoGeo(-34.6123456, -58.4145678).redondeado()
        assertEquals(-34.6123, p.lat, 0.00001)
        assertEquals(-58.4146, p.lng, 0.00001)
    }

    @Test fun rechazaCoordenadasImposibles() {
        assertThrows(IllegalArgumentException::class.java) { PuntoGeo(95.0, -58.0) }
        assertThrows(IllegalArgumentException::class.java) { PuntoGeo(-34.0, Double.NaN) }
    }
}
