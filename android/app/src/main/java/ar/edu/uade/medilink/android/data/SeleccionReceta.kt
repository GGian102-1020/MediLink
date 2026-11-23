package ar.edu.uade.medilink.android.data

import java.time.LocalDate
import java.time.ZoneId

/** Ayuda de la interfaz; el servidor vuelve a comprobar titularidad, vigencia y saldo. */
internal fun PrescripcionDto.impedimentoParaReserva(
    presentacionId: Long, cantidad: Int,
    hoy: LocalDate = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")),
): String? {
    if (estado != "ACTIVA") return "Receta inactiva"
    val emision = runCatching { LocalDate.parse(fechaEmision) }.getOrNull()
    val vencimiento = runCatching { LocalDate.parse(fechaVencimiento) }.getOrNull()
    if (emision == null || vencimiento == null) return "No se pudo comprobar la vigencia"
    if (hoy.isBefore(emision)) return "Todavía no está vigente"
    if (hoy.isAfter(vencimiento)) return "Receta vencida"
    val item = items.firstOrNull { it.presentacionId == presentacionId }
        ?: return "Corresponde a otro medicamento o presentación"
    if (cantidad !in 1..10) return "Ingresá una cantidad entre 1 y 10"
    if (item.cantidadDisponible < cantidad) return "Disponibles: ${item.cantidadDisponible}; solicitadas: $cantidad"
    return null
}
