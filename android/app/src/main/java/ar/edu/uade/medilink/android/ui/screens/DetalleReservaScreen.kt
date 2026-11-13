package ar.edu.uade.medilink.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.edu.uade.medilink.android.data.MediLinkRepository
import ar.edu.uade.medilink.android.data.ReservaDto
import ar.edu.uade.medilink.android.data.mensajeDeError
import ar.edu.uade.medilink.android.ui.theme.MediColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Verde = Color(0xFF2FBF71)
private val Rojo = Color(0xFFE5484D)
private val Ambar = Color(0xFFF5A524)

/** Detalle de una reserva del paciente, presentado como un comprobante de retiro. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun DetalleReservaPaciente(repository: MediLinkRepository, codigo: String, onVolver: () -> Unit) {
    val scope = rememberCoroutineScope()
    var reserva by remember(codigo) { mutableStateOf<ReservaDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableStateOf(0) }
    var confirmacion by remember { mutableStateOf(false) }
    var procesando by remember { mutableStateOf(false) }
    LaunchedEffect(codigo, revision) {
        try { reserva = repository.detalleReserva(codigo); error = null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = mensajeDeError(e) }
    }

    if (confirmacion) AlertDialog(
        onDismissRequest = { if (!procesando) confirmacion = false },
        title = { Text("¿Cancelar la reserva?") },
        text = {
            Text("Vas a liberar lo que reservaste en ${reserva?.farmaciaNombreSnapshot ?: "la farmacia"}. " +
                "Esta acción no se puede deshacer.")
        },
        confirmButton = {
            TextButton(enabled = !procesando, onClick = {
                scope.launch {
                    procesando = true
                    try { repository.cancelar(codigo); confirmacion = false; revision++ }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = mensajeDeError(e); confirmacion = false }
                    finally { procesando = false }
                }
            }) { Text(if (procesando) "Cancelando..." else "Sí, cancelar", color = Rojo, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { confirmacion = false }) { Text("No, mantener") } },
    )

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (reserva == null && error == null) IndicadorCargando()
        AvisoError(error)
        reserva?.let { r ->
            ComprobanteReserva(r, onActualizar = { revision++ })
            TarjetaDetalle("Medicamento") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconoCaja(Icons.Default.Medication)
                    Column(Modifier.weight(1f)) {
                        Text(r.medicamentoNombreSnapshot, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold)
                        Text(r.presentacionDescripcionSnapshot, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Etiqueta(if (r.cantidad == 1) "1 unidad" else "${r.cantidad} unidades")
                    Etiqueta(if (r.condicionDispensa == "BAJO_RECETA") "Bajo receta" else "Venta libre")
                }
                r.prescripcionCodigo?.let { DatoDetalle("Receta asociada", it) }
            }
            TarjetaDetalle("Farmacia") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconoCaja(Icons.Default.LocalPharmacy)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(r.farmaciaNombreSnapshot, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(" ${r.farmaciaDireccionSnapshot}", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                BotonComoLlegar(r)
            }
            TarjetaDetalle("Seguimiento") { LineaDeTiempo(pasosDe(r)) }
            if (r.estado == "ACTIVA") {
                OutlinedButton(
                    onClick = { confirmacion = true }, enabled = !procesando,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    border = BorderStroke(1.5.dp, Rojo),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Rojo),
                ) { Text("Cancelar reserva", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** Bloque oscuro con el código de retiro, el estado y lo que tiene que hacer el paciente. */
@Composable
private fun ComprobanteReserva(r: ReservaDto, onActualizar: () -> Unit) {
    // Cuenta regresiva local a partir de los segundos que informa el servidor.
    var restantes by remember(r) { mutableLongStateOf(r.segundosRestantes) }
    LaunchedEffect(r) {
        while (r.estado == "ACTIVA" && restantes > 0) { delay(30_000); restantes = (restantes - 30).coerceAtLeast(0) }
    }
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 56.dp, bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(MediColors.Noche),
    ) {
        CirculosMarca(Modifier.matchParentSize().alpha(0.45f), escala = 0.8f)
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CÓDIGO DE RETIRO", color = MediColors.Aqua, style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.8.sp, modifier = Modifier.weight(1f))
                ChipEstado(r.estado)
            }
            Text(r.codigoVisible, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace, letterSpacing = 3.sp)
            Canvas(Modifier.fillMaxWidth().height(1.dp)) {
                drawLine(Color.White.copy(alpha = 0.3f), Offset.Zero, Offset(size.width, 0f), strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val (titulo, detalle) = mensajeEstado(r, restantes)
                    Text(titulo, color = Color.White, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text(detalle, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onActualizar) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = Color.White)
                }
            }
        }
    }
}

private fun mensajeEstado(r: ReservaDto, restantes: Long): Pair<String, String> = when (r.estado) {
    "ACTIVA" -> {
        val h = restantes / 3600
        val m = (restantes % 3600) / 60
        val tiempo = when {
            restantes <= 0 -> "Está por vencer"
            h > 0 -> "Te quedan ${h} h ${m} min para retirarlo"
            else -> "Te quedan $m min para retirarlo"
        }
        tiempo to "Mostrá este código en la farmacia antes del ${fechaLegible(r.fechaHoraVencimiento)}."
    }
    "COMPLETADA" -> "Ya retiraste tu medicamento" to "Retirado el ${fechaLegible(r.fechaHoraRetiro)}."
    "CANCELADA" -> "Esta reserva fue cancelada" to "Cancelada el ${fechaLegible(r.fechaHoraCancelacion)}."
    "VENCIDA" -> "Esta reserva venció" to "No se retiró antes del ${fechaLegible(r.fechaHoraVencimiento)}."
    else -> r.estado to ""
}

@Composable
private fun ChipEstado(estado: String) {
    val (fondo, texto, nombre) = when (estado) {
        "ACTIVA" -> Triple(MediColors.Aqua, MediColors.Noche, "Activa")
        "COMPLETADA" -> Triple(Verde, Color.White, "Retirada")
        "CANCELADA" -> Triple(Rojo, Color.White, "Cancelada")
        "VENCIDA" -> Triple(Ambar, MediColors.Noche, "Vencida")
        else -> Triple(Color.White, MediColors.Noche, estado)
    }
    Surface(color = fondo, shape = RoundedCornerShape(50)) {
        Text(nombre, color = texto, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
    }
}

@Composable
private fun TarjetaDetalle(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(titulo.uppercase(), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            contenido()
        }
    }
}

@Composable
private fun IconoCaja(icono: ImageVector) {
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(icono, contentDescription = null, tint = MediColors.Teal) }
}

@Composable
private fun Etiqueta(texto: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50)) {
        Text(texto, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
private fun DatoDetalle(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BotonComoLlegar(r: ReservaDto) {
    val contexto = LocalContext.current
    OutlinedButton(
        onClick = {
            val destino = Uri.encode("${r.farmaciaNombreSnapshot}, ${r.farmaciaDireccionSnapshot}")
            runCatching { contexto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$destino"))) }
                .recoverCatching {
                    contexto.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/maps/search/?api=1&query=$destino")))
                }
        },
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Cómo llegar", fontWeight = FontWeight.SemiBold)
    }
}

private data class Paso(val titulo: String, val detalle: String, val color: Color, val pendiente: Boolean = false)

private fun pasosDe(r: ReservaDto): List<Paso> = buildList {
    add(Paso("Reserva creada", fechaLegible(r.fechaHoraCreacion), MediColors.Teal))
    when (r.estado) {
        "ACTIVA" -> add(Paso("Pendiente de retiro", "Vence el ${fechaLegible(r.fechaHoraVencimiento)}",
            MediColors.Teal, pendiente = true))
        "COMPLETADA" -> add(Paso("Medicamento retirado", fechaLegible(r.fechaHoraRetiro), Verde))
        "VENCIDA" -> add(Paso("Reserva vencida", "Venció el ${fechaLegible(r.fechaHoraVencimiento)} sin retirarse", Ambar))
        "CANCELADA" -> {
            val detalle = listOfNotNull(
                fechaLegible(r.fechaHoraCancelacion),
                r.causaCancelacion?.let { causaLegible(it) },
                r.origenCancelacion?.takeIf { r.causaCancelacion != "CANCELACION_USUARIO" }
                    ?.let { "Cancelada por ${origenLegible(it)}" },
                r.motivoCancelacion?.takeIf { it.isNotBlank() }?.let { "“$it”" },
            ).joinToString("\n")
            add(Paso("Reserva cancelada", detalle, Rojo))
        }
    }
}

@Composable
private fun LineaDeTiempo(pasos: List<Paso>) {
    Column {
        pasos.forEachIndexed { i, paso ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Column(Modifier.width(26.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.padding(top = 3.dp).size(14.dp).clip(CircleShape)
                            .then(
                                if (paso.pendiente) Modifier.border(2.5.dp, paso.color, CircleShape)
                                else Modifier.background(paso.color)
                            ),
                    )
                    if (i < pasos.lastIndex) {
                        Box(Modifier.padding(vertical = 3.dp).width(2.dp).weight(1f)
                            .background(MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                Column(Modifier.weight(1f).padding(start = 10.dp, bottom = if (i < pasos.lastIndex) 18.dp else 0.dp)) {
                    Text(paso.titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(paso.detalle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun causaLegible(causa: String) = when (causa) {
    "CANCELACION_USUARIO" -> "La cancelaste vos"
    "STOCK_INCONSISTENTE" -> "Diferencia de stock en la farmacia"
    "PRODUCTO_NO_DISPONIBLE" -> "El producto no estaba disponible"
    "PRODUCTO_NO_APTO_PARA_ENTREGA" -> "El producto no estaba apto para entregar"
    "IMPEDIMENTO_OPERATIVO_FARMACIA" -> "Problema operativo de la farmacia"
    "ERROR_ADMINISTRATIVO" -> "Error administrativo"
    "CUENTA_DESHABILITADA" -> "La cuenta fue deshabilitada"
    "OTRA_CAUSA" -> "Otra causa"
    else -> causa.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

private fun origenLegible(origen: String) = when (origen) {
    "USUARIO" -> "vos"
    "OPERADOR" -> "la farmacia"
    "ADMINISTRADOR" -> "administración"
    "SISTEMA" -> "el sistema"
    else -> origen.lowercase()
}
