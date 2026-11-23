package ar.edu.uade.medilink.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ar.edu.uade.medilink.android.data.MediLinkRepository
import ar.edu.uade.medilink.android.data.mensajeDeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun RecuperarClaveScreen(repository: MediLinkRepository, onVolver: () -> Unit) {
    var correo by rememberSaveable { mutableStateOf("") }
    var enviado by rememberSaveable { mutableStateOf(false) }
    var terminado by rememberSaveable { mutableStateOf(false) }
    // Ni el código ni las contraseñas van al estado guardado de Android.
    var codigo by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var repetir by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Recuperar contraseña", style = MaterialTheme.typography.headlineMedium)
        if (terminado) {
            Text("Tu contraseña se actualizó. Iniciá sesión con la nueva contraseña.")
            Button(onClick = onVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver al inicio de sesión") }
        } else {
            Text(if (enviado) "Si tu cuenta está activa, recibirás un código de 8 dígitos. Revisá también el correo no deseado. Vence en 15 minutos."
                else "Ingresá el correo de tu cuenta para recibir un código de recuperación.")
            OutlinedTextField(value = correo, onValueChange = { correo = it.trim().take(254) },
                label = { Text("Correo electrónico") }, singleLine = true, enabled = !ocupado && !enviado,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            if (enviado) {
                OutlinedTextField(value = codigo, onValueChange = { codigo = it.filter(Char::isDigit).take(8) },
                    label = { Text("Código de recuperación") }, singleLine = true, enabled = !ocupado,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = clave, onValueChange = { clave = it }, label = { Text("Nueva contraseña") },
                    singleLine = true, enabled = !ocupado, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                Text("Usá al menos 8 caracteres (máximo 72 bytes).", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = repetir, onValueChange = { repetir = it }, label = { Text("Repetir contraseña") },
                    singleLine = true, enabled = !ocupado, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            }
            AvisoError(error)
            Button(enabled = !ocupado && correo.isNotBlank() && (!enviado ||
                (codigo.length == 8 && clave.length >= 8 && clave.toByteArray(Charsets.UTF_8).size <= 72 && clave == repetir)),
                modifier = Modifier.fillMaxWidth(), onClick = {
                    ocupado = true; error = null
                    scope.launch {
                        try {
                            if (enviado) {
                                repository.restablecerClave(correo, codigo, clave)
                                clave = ""; repetir = ""; codigo = ""; terminado = true
                            } else { repository.recuperarClave(correo); enviado = true }
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = mensajeDeError(e) }
                        finally { ocupado = false }
                    }
                }) { Text(if (ocupado) "Procesando…" else if (enviado) "Guardar nueva contraseña" else "Enviar código") }
            if (enviado) TextButton(enabled = !ocupado, onClick = {
                enviado = false; codigo = ""; clave = ""; repetir = ""; error = null
            }) { Text("Cambiar correo o solicitar otro código") }
            TextButton(onClick = onVolver, enabled = !ocupado) { Text("Volver al inicio de sesión") }
        }
    }
}
