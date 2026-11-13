package ar.edu.uade.medilink.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class FechasTest {
    @Test fun formateaFechaConZonaDelBackend() =
        assertEquals("14/10/2026 15:04", fechaLegible("2026-10-14T15:04:28.528-03:00"))

    @Test fun convierteUtcAHorarioArgentino() =
        assertEquals("14/10/2026 12:04", fechaLegible("2026-10-14T15:04:28Z"))

    @Test fun fechaSinHora() = assertEquals("20/11/1996", fechaLegible("1996-11-20"))

    @Test fun vacioOTextoInvalido() {
        assertEquals("—", fechaLegible(null))
        assertEquals("—", fechaLegible("  "))
        assertEquals("pendiente", fechaLegible("pendiente"))
    }
}
