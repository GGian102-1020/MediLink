package ar.edu.uade.medilink.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val screenPadding = PaddingValues(16.dp)

@Composable
fun Encabezado(titulo: String, subtitulo: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(titulo, style = MaterialTheme.typography.headlineSmall)
        Text(subtitulo, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AvisoError(error: String?) {
    if (error != null) {
        Text(
            error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
}

@Composable
fun IndicadorCargando() {
    CircularProgressIndicator(modifier = Modifier.padding(12.dp))
}

@Composable
fun RequiereSesion(onLogin: () -> Unit) {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Encabezado("Iniciá sesión", "Necesitás una cuenta de paciente para gestionar tus reservas.")
        Button(onClick = onLogin) { Text("Iniciar sesión") }
    }
}
