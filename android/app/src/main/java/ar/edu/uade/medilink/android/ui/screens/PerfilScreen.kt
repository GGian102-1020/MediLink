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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ar.edu.uade.medilink.android.data.LoginDto
import ar.edu.uade.medilink.android.data.MeDto
import ar.edu.uade.medilink.android.data.MediLinkRepository
import ar.edu.uade.medilink.android.data.mensajeDeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PerfilScreen(repository: MediLinkRepository, sesion: LoginDto?, onLogin: () -> Unit) {
    val scope = rememberCoroutineScope()
    var perfil by remember { mutableStateOf<MeDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var cerrando by remember { mutableStateOf(false) }
    var editandoClave by remember { mutableStateOf(false) }
    var actual by remember { mutableStateOf("") }
    var nueva by remember { mutableStateOf("") }
    var repetida by remember { mutableStateOf("") }
    var procesando by remember { mutableStateOf(false) }

    LaunchedEffect(sesion?.accessToken) {
        perfil = null
        if (sesion != null) {
            try { perfil = repository.miPerfil() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = mensajeDeError(e) }
        }
    }

    if (editandoClave) {
        AlertDialog(
            onDismissRequest = { if (!procesando) editandoClave = false },
            title = { Text("Cambiar contraseña") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(actual, { actual = it }, label = { Text("Contraseña actual") },
                        visualTransformation = PasswordVisualTransformation())
                    OutlinedTextField(nueva, { nueva = it }, label = { Text("Nueva contraseña") },
                        visualTransformation = PasswordVisualTransformation())
                    OutlinedTextField(repetida, { repetida = it }, label = { Text("Repetir contraseña") },
                        visualTransformation = PasswordVisualTransformation())
                    AvisoError(error)
                }
            },
            confirmButton = {
                TextButton(enabled = !procesando && actual.isNotBlank() && nueva.isNotBlank()
                    && nueva == repetida, onClick = {
                    scope.launch {
                        procesando = true; error = null
                        try {
                            repository.cambiarClave(actual, nueva)
                            editandoClave = false; actual = ""; nueva = ""; repetida = ""
                            mensaje = "Contraseña actualizada. La sesión puede requerir un nuevo ingreso."
                            perfil = repository.miPerfil()
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = mensajeDeError(e) }
                        finally { procesando = false }
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editandoClave = false }) { Text("Volver") } },
        )
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (sesion == null) {
            Encabezado("Mi cuenta", "Iniciá sesión para ver tus datos.")
            Button(onClick = onLogin) { Text("Iniciar sesión") }
        } else {
            Encabezado("Mi perfil", "Cuenta y estado actualizados por el servidor.")
            val p = perfil?.perfil ?: sesion.perfil
            Text("Nombre: ${p.nombre} ${p.apellido}")
            Text("Correo: ${p.correo}")
            Text("Rol: ${p.rol}")
            Text("Estado de cuenta: ${p.estadoCuenta}")
            p.farmaciaId?.let { Text("Farmacia asignada: #$it") }
            perfil?.suspensionVigente?.takeIf { it.vigente }?.let {
                Text("Suspensión hasta ${it.fechaHoraFin}: ${it.motivo}")
            }
            AvisoError(error)
            mensaje?.let { Text(it) }
            OutlinedButton(onClick = {
                scope.launch {
                    try { perfil = repository.miPerfil(); error = null }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = mensajeDeError(e) }
                }
            }) { Text("Actualizar perfil") }
            Button(onClick = { error = null; editandoClave = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Cambiar contraseña")
            }
            OutlinedButton(enabled = !cerrando, onClick = {
                cerrando = true
                scope.launch {
                    try { repository.logout() } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { /* se descarta el JWT local */ }
                    finally { cerrando = false }
                }
            }) { Text("Cerrar sesión") }
            Text("La sesión está en memoria. Al cerrar por completo la app, se requiere nuevo ingreso.")
        }
    }
}
