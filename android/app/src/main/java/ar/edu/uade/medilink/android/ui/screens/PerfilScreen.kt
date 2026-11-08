package ar.edu.uade.medilink.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.edu.uade.medilink.android.data.LoginDto
import ar.edu.uade.medilink.android.data.MediLinkRepository
import kotlinx.coroutines.launch

@Composable
fun PerfilScreen(repository: MediLinkRepository, sesion: LoginDto?, onLogin: () -> Unit) {
    val scope = rememberCoroutineScope()
    var cerrando by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (sesion == null) {
            Encabezado("Mi cuenta", "Iniciá sesión para ver tus datos y reservas.")
            Button(onClick = onLogin) { Text("Iniciar sesión") }
        } else {
            Encabezado("Mi perfil", "Sesión de paciente activa solo en esta ejecución de la app.")
            Text("Nombre: ${sesion.perfil.nombre} ${sesion.perfil.apellido}")
            Text("Correo: ${sesion.perfil.correo}")
            Text("Rol: ${sesion.perfil.rol}")
            Text("Duración del token al emitirlo: ${sesion.expiresInSeconds / 60} minutos")
            OutlinedButton(
                enabled = !cerrando,
                onClick = {
                    cerrando = true
                    scope.launch {
                        try { repository.logout() } catch (_: Exception) {
                            // La sesión local ya se borra en finally.
                        } finally { cerrando = false }
                    }
                },
            ) { Text("Cerrar sesión") }
        }
    }
}
