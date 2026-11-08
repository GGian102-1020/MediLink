package ar.edu.uade.medilink.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
            try { perfil = repository.miPerfil(); error = null }
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

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (sesion == null) {
            HeroPanel("TU CUENTA", "Bienvenido a MediLink",
                "Ingresá para consultar tus datos y gestionar las reservas de tu cuenta.",
                Icons.Default.AccountCircle)
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Iniciar sesión") }
            NotaInformativa("Podés explorar medicamentos sin iniciar sesión. Para reservar necesitás una cuenta de paciente.")
        } else {
            val p = perfil?.perfil ?: sesion.perfil
            val iniciales = ((p.nombre.take(1)) + p.apellido.take(1)).uppercase().ifBlank { "ML" }
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer,
                            CircleShape), contentAlignment = Alignment.Center) {
                            Text(iniciales, style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Mi cuenta", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                            Text("${p.nombre} ${p.apellido}", style = MaterialTheme.typography.titleLarge)
                            Text(p.correo, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EstadoPildora(p.estadoCuenta)
                        EstadoPildora(p.rol)
                    }
                }
            }
            SeccionTitulo("Datos personales", "Información consultada al servidor")
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                    DatoFila("Nombre completo", "${p.nombre} ${p.apellido}")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DatoFila("Correo electrónico", p.correo)
                    p.farmaciaId?.let {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DatoFila("Farmacia asignada", "Sucursal #$it")
                    }
                }
            }
            perfil?.suspensionVigente?.takeIf { it.vigente }?.let {
                AvisoError("Suspensión hasta ${it.fechaHoraFin}: ${it.motivo}")
            }
            AvisoError(error)
            mensaje?.let { NotaInformativa(it) }
            OutlinedButton(onClick = {
                scope.launch {
                    try { perfil = repository.miPerfil(); error = null }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = mensajeDeError(e) }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Text("  Actualizar perfil")
            }
            SeccionTitulo("Seguridad y acceso")
            AccesoTarjeta("Cambiar contraseña", "Actualizá la clave de acceso a tu cuenta",
                Icons.Default.Lock, onClick = { error = null; editandoClave = true })
            TextButton(enabled = !cerrando, onClick = {
                cerrando = true
                scope.launch {
                    try { repository.logout() } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { /* se descarta el JWT local */ }
                    finally { cerrando = false }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Logout, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error)
                Text("  Cerrar sesión", color = MaterialTheme.colorScheme.error)
            }
            NotaInformativa("Tu sesión se mantiene únicamente en memoria. Si cerrás por completo la app, tendrás que volver a ingresar.")
        }
    }
}
