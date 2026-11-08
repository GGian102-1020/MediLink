package ar.edu.uade.medilink.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IntentoReservaTest {
    private val solicitud = CrearReservaRequest(1, 2, 1)

    @Test fun mismaSolicitudReutilizaClave() {
        val intento = IntentoReserva()
        assertEquals(intento.clavePara(solicitud), intento.clavePara(solicitud))
    }

    @Test fun cambioDeCantidadCreaOtraClave() {
        val intento = IntentoReserva()
        val anterior = intento.clavePara(solicitud)
        assertNotEquals(anterior, intento.clavePara(solicitud.copy(cantidad = 2)))
    }

    @Test fun confirmacionLiberaClaveParaOtraReserva() {
        val intento = IntentoReserva()
        val anterior = intento.clavePara(solicitud)
        intento.confirmar()
        assertNotEquals(anterior, intento.clavePara(solicitud))
    }
}
