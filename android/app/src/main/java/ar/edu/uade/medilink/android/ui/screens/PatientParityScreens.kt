package ar.edu.uade.medilink.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ar.edu.uade.medilink.android.data.MeDto
import ar.edu.uade.medilink.android.data.MediLinkRepository
import ar.edu.uade.medilink.android.data.ReservaDto
import ar.edu.uade.medilink.android.data.mensajeDeError
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** Saludo + avatar con iniciales, usado en el inicio del paciente y del invitado. */
@Composable
fun SaludoInicio(titulo: String, subtitulo: String, iniciales: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(titulo, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitulo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (iniciales != null) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center) {
                Text(iniciales, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Reserva por retirar destacada: es lo más importante que tiene que ver el paciente al entrar. */
@Composable
private fun ProximaReserva(r: ReservaDto, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Listo para retirar", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                Text(r.medicamentoNombreSnapshot, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(r.farmaciaNombreSnapshot, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Text("Ver código", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
fun FilaReserva(r: ReservaDto, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Medication, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(r.medicamentoNombreSnapshot, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(r.farmaciaNombreSnapshot, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            EstadoPildora(r.estado)
            Icon(Icons.Default.ChevronRight, contentDescription = "Ver reserva",
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun PanelPaciente(repository: MediLinkRepository, onCatalogo: () -> Unit,
    onReservas: () -> Unit, onRecetas: () -> Unit, onDetalle: (String) -> Unit) {
    var perfil by remember { mutableStateOf<MeDto?>(null) }
    var reservas by remember { mutableStateOf<List<ReservaDto>>(emptyList()) }
    var proxima by remember { mutableStateOf<ReservaDto?>(null) }
    var activas by remember { mutableStateOf<Long?>(null) }
    var total by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(repository.session.token) {
        try {
            perfil = repository.miPerfil()
            val pagina = repository.misReservas(0)
            reservas = pagina.content
            total = pagina.totalElements
            val paginaActivas = repository.misReservas(0, "ACTIVA")
            activas = paginaActivas.totalElements
            proxima = paginaActivas.content.firstOrNull()
            error = null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = mensajeDeError(e) }
    }
    val p = perfil?.perfil ?: repository.session.login.value?.perfil
    val iniciales = p?.let { (it.nombre.take(1) + it.apellido.take(1)).uppercase() }?.ifBlank { null }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SaludoInicio("Hola, ${p?.nombre ?: "bienvenido"}", "¿Qué medicamento buscás hoy?", iniciales)
        BuscadorEntrada("Buscá un medicamento…", onCatalogo)
        perfil?.suspensionVigente?.takeIf { it.vigente }?.let {
            AvisoError("Tu cuenta está suspendida hasta ${fechaLegible(it.fechaHoraFin)}: ${it.motivo}")
        }
        AvisoError(error)
        proxima?.let { r ->
            Spacer(Modifier.height(0.dp))
            ProximaReserva(r) { onDetalle(r.codigoVisible) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricaTarjeta((activas ?: "—").toString(), "Por retirar",
                Icons.AutoMirrored.Filled.ReceiptLong, Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable(onClick = onReservas))
            MetricaTarjeta((total ?: "—").toString(), "Reservas en total",
                Icons.Default.History, Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable(onClick = onReservas))
        }
        AccesoTarjeta("Mis recetas", "Vinculá y consultá tus recetas", Icons.Default.Medication, onRecetas)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { SeccionTitulo("Reservas recientes") }
            if (reservas.isNotEmpty()) TextButton(onClick = onReservas) {
                Text("Ver todas", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
        if (reservas.isEmpty() && error == null && total != null) {
            VacioEstado("Aún sin reservas", "Buscá un medicamento y reservalo en la farmacia más cercana.")
        }
        reservas.take(3).forEach { r -> FilaReserva(r) { onDetalle(r.codigoVisible) } }
        Spacer(Modifier.height(8.dp))
    }
}
