package ar.edu.uade.medilink.android.ui.screens

import ar.edu.uade.medilink.android.data.FarmaciaRequest
import ar.edu.uade.medilink.android.data.OperadorUpdateRequest
import ar.edu.uade.medilink.android.data.PresentacionCreateRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ComandosAdminTest {
    @Test fun farmaciaRequiereAmbasCoordenadas() {
        assertThrows(IllegalArgumentException::class.java) {
            comandoAdmin(AdminSection.FARMACIAS,
                mapOf("nombre" to "A", "direccion" to "Calle 123", "latitud" to "-34.6"), true)
        }
    }

    @Test fun farmaciaTransformaCoordenadas() {
        val cmd = comandoAdmin(AdminSection.FARMACIAS,
            mapOf("nombre" to "Central", "direccion" to "Calle 123",
                "latitud" to "-34.6", "longitud" to "-58.4"), true) as FarmaciaRequest
        assertEquals(-34.6, cmd.latitud!!, 0.001)
        assertEquals(-58.4, cmd.longitud!!, 0.001)
    }

    @Test fun presentacionUtilizaTiposNumericos() {
        val cmd = comandoAdmin(AdminSection.PRESENTACIONES, mapOf(
            "medicamentoId" to "12", "concentracionTexto" to "400 mg",
            "formaFarmaceutica" to "Comprimido", "cantidadEnvase" to "20",
            "unidadExpendio" to "comprimidos", "condicionDispensa" to "VENTA_LIBRE"), true)
            as PresentacionCreateRequest
        assertEquals(12L, cmd.medicamentoId)
        assertEquals(20, cmd.cantidadEnvase)
    }

    @Test fun noPermiteCantidadNegativa() {
        assertThrows(IllegalArgumentException::class.java) {
            comandoAdmin(AdminSection.PRESENTACIONES, mapOf(
                "medicamentoId" to "12", "concentracionTexto" to "400 mg",
                "formaFarmaceutica" to "Comprimido", "cantidadEnvase" to "-1",
                "unidadExpendio" to "comprimidos", "condicionDispensa" to "VENTA_LIBRE"), true)
        }
    }

    @Test fun operadorEditaSoloNombreYApellido() {
        val cmd = comandoAdmin(AdminSection.OPERADORES,
            mapOf("nombre" to "Ana", "apellido" to "Perez"), false) as OperadorUpdateRequest
        assertEquals("Ana", cmd.nombre)
        assertEquals("Perez", cmd.apellido)
    }
}
