package ar.edu.uade.medilink.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ar.edu.uade.medilink.android.data.MeDto
import ar.edu.uade.medilink.android.data.MediLinkRepository
import ar.edu.uade.medilink.android.data.ReservaDto
import ar.edu.uade.medilink.android.data.mensajeDeError
import kotlinx.coroutines.CancellationException

@Composable
fun PanelPaciente(repository: MediLinkRepository, onCatalogo: () -> Unit,
    onReservas: () -> Unit, onRecetas: () -> Unit, onDetalle: (String) -> Unit) {
    var perfil by remember { mutableStateOf<MeDto?>(null) }
    var reservas by remember { mutableStateOf<List<ReservaDto>>(emptyList()) }
    var activas by remember { mutableStateOf<Long?>(null) }
    var total by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(repository.session.token) {
        try {
            perfil = repository.miPerfil()
            val pagina = repository.misReservas(0)
            reservas = pagina.content
            total = pagina.totalElements
            activas = repository.misReservas(0, "ACTIVA").totalElements
            error = null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = mensajeDeError(e) }
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        HeroPanel("MI MEDILINK", "Hola, ${perfil?.perfil?.nombre ?: "bienvenido"}",
            "Buscá un medicamento, reservalo en una farmacia y retiralo con tu código y PIN.",
            Icons.Default.LocalPharmacy)
        perfil?.suspensionVigente?.takeIf { it.vigente }?.let {
            AvisoError("Tu cuenta está suspendida hasta ${fechaLegible(it.fechaHoraFin)}: ${it.motivo}")
        }
        AvisoError(error)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricaTarjeta((activas ?: "—").toString(), "Por retirar",
                Icons.Default.ReceiptLong, Modifier.weight(1f).clickable(onClick = onReservas))
            MetricaTarjeta((total ?: "—").toString(), "Reservas en total",
                Icons.Default.History, Modifier.weight(1f).clickable(onClick = onReservas))
        }
        SeccionTitulo("Accesos rápidos")
        AccesoTarjeta("Buscar medicamentos", "Encontrá farmacias con stock",
            Icons.Default.Search, onCatalogo)
        AccesoTarjeta("Mis recetas", "Vinculá y consultá tus recetas", Icons.Default.Medication, onRecetas)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { SeccionTitulo("Reservas recientes") }
            if (reservas.isNotEmpty()) TextButton(onClick = onReservas) { Text("Ver todas") }
        }
        if (reservas.isEmpty() && error == null && total != null) {
            VacioEstado("Aún sin reservas", "Cuando hagas una reserva, vas a poder seguirla desde acá.")
        }
        reservas.take(3).forEach { r ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onDetalle(r.codigoVisible) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(r.medicamentoNombreSnapshot, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text(r.farmaciaNombreSnapshot, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                        EstadoPildora(r.estado)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Ver reserva",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
