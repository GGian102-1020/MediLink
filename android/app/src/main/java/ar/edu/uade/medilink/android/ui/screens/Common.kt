package ar.edu.uade.medilink.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val screenPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)

@Composable
fun Encabezado(titulo: String, subtitulo: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 5.dp)) {
        Text("MEDILINK", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp)
        Text(titulo, style = MaterialTheme.typography.headlineMedium)
        Text(subtitulo, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AvisoError(error: String?) {
    if (!error.isNullOrBlank()) {
        Surface(color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(error, color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun IndicadorCargando() {
    Row(modifier = Modifier.fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator()
        Text("  Cargando...", color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun RequiereSesion(onLogin: () -> Unit) {
    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HeroPanel("ACCESO SEGURO", "Ingresá a tu cuenta",
            "Iniciá sesión para gestionar tus reservas y acceder a tu información.", Icons.Default.Lock)
        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Iniciar sesión") }
    }
}
