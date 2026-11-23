package ar.edu.uade.medilink.android.data

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class SeleccionRecetaTest {
    private val hoy = LocalDate.of(2026, 11, 23)
    private val receta = PrescripcionDto(1, "ADJ-propia", 10, "2026-11-23", "2026-11-24", "ACTIVA",
        listOf(ItemPrescripcionDto(1, 9, "Levotiroxina 100 mcg · Caja x 50", 4, 1, 1, 2)))

    @Test fun permiteRecetaCargadaConSaldoExactoSinImportarElPrefijoDelCodigo() {
        assertNull(receta.impedimentoParaReserva(9, 2, hoy))
        assertNull(receta.copy(codigoExterno = "MLD-propia").impedimentoParaReserva(9, 2, hoy))
    }
    @Test fun noConfundeDistintasPresentacionesDelMismoMedicamento() {
        assertEquals("Corresponde a otro medicamento o presentación", receta.impedimentoParaReserva(8, 1, hoy))
    }
    @Test fun noCuentaComoDisponibleLoReservadoNiLoRetirado() {
        assertNotNull(receta.impedimentoParaReserva(9, 3, hoy))
        assertNotNull(receta.copy(items = receta.items.map { it.copy(cantidadDisponible = 0) }).impedimentoParaReserva(9, 1, hoy))
    }
    @Test fun vigenciaIncluyeAmbosExtremosYRechazaFechasFuturasOVencidas() {
        assertNull(receta.impedimentoParaReserva(9, 1, hoy))
        assertNull(receta.impedimentoParaReserva(9, 1, hoy.plusDays(1)))
        assertEquals("Receta vencida", receta.impedimentoParaReserva(9, 1, hoy.plusDays(2)))
        assertEquals("Todavía no está vigente", receta.impedimentoParaReserva(9, 1, hoy.minusDays(1)))
    }
    @Test fun rechazaInactivasFechasInvalidasYCantidadesFueraDeRango() {
        assertNotNull(receta.copy(estado = "ANULADA").impedimentoParaReserva(9, 1, hoy))
        assertNotNull(receta.copy(fechaVencimiento = "inválida").impedimentoParaReserva(9, 1, hoy))
        assertNotNull(receta.impedimentoParaReserva(9, 0, hoy))
        assertNotNull(receta.impedimentoParaReserva(9, 11, hoy))
    }
}
