package ar.edu.uade.medilink.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.edu.uade.medilink.android.data.MeDto
import ar.edu.uade.medilink.android.data.MediLinkRepository
import ar.edu.uade.medilink.android.data.ReservaDto
import ar.edu.uade.medilink.android.data.mensajeDeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PanelPaciente(repository: MediLinkRepository, onCatalogo: () -> Unit,
    onReservas: () -> Unit) {
    var perfil by remember { mutableStateOf<MeDto?>(null) }
    var reservas by remember { mutableStateOf<List<ReservaDto>>(emptyList()) }
    var cantidadCatalogo by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableStateOf(0) }
    LaunchedEffect(repository.session.token, revision) {
        try {
            perfil = repository.miPerfil()
            reservas = repository.misReservas(0).content
            cantidadCatalogo = repository.medicamentos("", 0).totalElements
            error = null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = mensajeDeError(e) }
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        HeroPanel("MI MEDILINK", "Hola, ${perfil?.perfil?.nombre ?: "bienvenido"}",
            "Consultá medicamentos, encontrá una farmacia y seguí tus reservas.",
            Icons.Default.LocalPharmacy)
        perfil?.suspensionVigente?.takeIf { it.vigente }?.let {
            AvisoError("Tu cuenta está suspendida hasta ${fechaLegible(it.fechaHoraFin)}: ${it.motivo}")
        }
        SeccionTitulo("Tu actividad", "Reservas de la primera página consultada")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricaTarjeta("${reservas.count { it.estado == "ACTIVA" }}", "Reservas activas",
                Icons.Default.ReceiptLong, Modifier.weight(1f))
            MetricaTarjeta((cantidadCatalogo ?: "—").toString(), "Medicamentos",
                Icons.Default.Medication, Modifier.weight(1f))
        }
        SeccionTitulo("Accesos rápidos")
        AccesoTarjeta("Buscar medicamentos", "Encontrá disponibilidad cerca tuyo",
            Icons.Default.Search, onCatalogo)
        AccesoTarjeta("Mis reservas", "Consultá estados y códigos de retiro",
            Icons.Default.ReceiptLong, onReservas)
        AvisoError(error)
        SeccionTitulo("Reservas recientes")
        if (reservas.isEmpty() && error == null) {
            VacioEstado("Aún sin reservas", "Cuando hagas una reserva, vas a poder seguirla desde acá.")
        }
        reservas.take(3).forEach { r ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.codigoVisible, style = MaterialTheme.typography.titleMedium)
                        EstadoPildora(r.estado)
                    }
                    Text(r.medicamentoNombreSnapshot, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium)
                    Text(r.farmaciaNombreSnapshot, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        OutlinedButton(onClick = { revision++ }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Text("  Actualizar panel")
        }
    }
}
