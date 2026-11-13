package ar.edu.uade.medilink.android.ui.screens

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ZONA_ARGENTINA: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
private val FORMATO_FECHA_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private val FORMATO_FECHA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * Convierte las fechas ISO del backend (por ejemplo "2026-10-14T15:04:28.528-03:00")
 * en texto legible en horario de Argentina: "14/10/2026 15:04". Las fechas sin hora
 * quedan como "14/10/2026". Si el texto no se puede interpretar, se muestra tal cual.
 */
fun fechaLegible(valor: String?): String {
    val texto = valor?.trim().orEmpty()
    if (texto.isEmpty()) return "—"
    return runCatching {
        OffsetDateTime.parse(texto).atZoneSameInstant(ZONA_ARGENTINA).format(FORMATO_FECHA_HORA)
    }.recoverCatching {
        Instant.parse(texto).atZone(ZONA_ARGENTINA).format(FORMATO_FECHA_HORA)
    }.recoverCatching {
        LocalDateTime.parse(texto).format(FORMATO_FECHA_HORA)
    }.recoverCatching {
        LocalDate.parse(texto).format(FORMATO_FECHA)
    }.getOrDefault(texto)
}
