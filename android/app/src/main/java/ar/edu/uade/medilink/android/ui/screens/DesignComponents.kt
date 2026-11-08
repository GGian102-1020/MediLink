package ar.edu.uade.medilink.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.edu.uade.medilink.android.ui.theme.MediColors

/** Piezas visuales reutilizables para pacientes, operadores y administración. */
@Composable
fun HeroPanel(
    etiqueta: String,
    titulo: String,
    descripcion: String,
    icono: ImageVector,
    modifier: Modifier = Modifier,
    contenido: (@Composable () -> Unit)? = null,
) {
    val oscuro = MaterialTheme.colorScheme.background.red < 0.2f
    val fondo = if (oscuro) Color(0xFF225F55) else MediColors.Forest
    Box(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(fondo, if (oscuro) Color(0xFF146D61) else MediColors.Teal)))
            .padding(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icono, contentDescription = null, tint = Color.White) }
                Text(etiqueta.uppercase(), color = Color(0xFFD4F5E9),
                    style = MaterialTheme.typography.labelMedium, letterSpacing = 1.3.sp)
            }
            Text(titulo, color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Text(descripcion, color = Color(0xFFE2F5EF), style = MaterialTheme.typography.bodyMedium)
            contenido?.invoke()
        }
    }
}

@Composable
fun SeccionTitulo(titulo: String, descripcion: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleLarge)
        if (!descripcion.isNullOrBlank()) {
            Text(descripcion, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun MetricaTarjeta(valor: String, etiqueta: String, icono: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(valor, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
            Text(etiqueta, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
fun AccesoTarjeta(
    titulo: String,
    descripcion: String,
    icono: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(titulo, style = MaterialTheme.typography.titleMedium)
                Text(descripcion, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ArrowForward, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun EstadoPildora(estado: String) {
    val positivo = estado in listOf("ACTIVA", "ACTIVO", "COMPLETADA", "HABILITADA")
    val color = if (positivo) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Surface(color = color, shape = RoundedCornerShape(50), modifier = Modifier) {
        Text(estado.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (positivo) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DatoFila(etiqueta: String, valor: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun NotaInformativa(texto: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Info, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            Text(texto, color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun VacioEstado(titulo: String, descripcion: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.CheckCircle, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            Text(descripcion, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
