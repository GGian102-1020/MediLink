package ar.edu.uade.medilink.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.medilink.android.ui.viewmodel.AuthVm

@Composable
fun AuthScreen(vm: AuthVm, onSuccess: () -> Unit, onRegistro: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var correo by rememberSaveable { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    LaunchedEffect(state.inicioExitoso) {
        if (state.inicioExitoso) onSuccess()
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Encabezado("Iniciar sesión", "Usá tu cuenta de paciente de MediLink.")
        OutlinedTextField(
            value = correo, onValueChange = { correo = it }, label = { Text("Correo") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = contrasena, onValueChange = { contrasena = it }, label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        AvisoError(state.error)
        Button(
            onClick = { vm.login(correo, contrasena) },
            enabled = !state.cargando && correo.isNotBlank() && contrasena.isNotBlank(),
        ) { Text(if (state.cargando) "Ingresando..." else "Entrar") }
        OutlinedButton(onClick = onRegistro, enabled = !state.cargando) {
            Text("Crear cuenta")
        }
    }
}

@Composable
fun RegistroScreen(vm: AuthVm, onVolver: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var nombre by rememberSaveable { mutableStateOf("") }
    var apellido by rememberSaveable { mutableStateOf("") }
    var correo by rememberSaveable { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Encabezado("Crear cuenta", "Registro de pacientes.")
        if (state.registroExitoso) {
            Text("Cuenta creada. Ya podés iniciar sesión.")
            Button(onClick = onVolver) { Text("Ir al inicio de sesión") }
        } else {
            OutlinedTextField(
                value = nombre, onValueChange = { nombre = it }, label = { Text("Nombre") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            OutlinedTextField(
                value = apellido, onValueChange = { apellido = it }, label = { Text("Apellido") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            OutlinedTextField(
                value = correo, onValueChange = { correo = it }, label = { Text("Correo") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            OutlinedTextField(
                value = clave, onValueChange = { clave = it }, label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            )
            AvisoError(state.error)
            Button(
                onClick = { vm.registrar(nombre, apellido, correo, clave) },
                enabled = !state.cargando && nombre.isNotBlank() && apellido.isNotBlank() &&
                    correo.isNotBlank() && clave.isNotBlank(),
            ) { Text(if (state.cargando) "Registrando..." else "Registrarse") }
        }
    }
}
