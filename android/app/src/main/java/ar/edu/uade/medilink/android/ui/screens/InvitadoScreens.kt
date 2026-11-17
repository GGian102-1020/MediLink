package ar.edu.uade.medilink.android.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Aviso que se muestra cuando un invitado intenta una acción que requiere cuenta
 * (reservar, ver reservas, recetas, etc.). Nunca ejecuta la acción: solo ofrece ingresar.
 * Diseño propio de la app (claro, acento teal), no el diálogo del sistema.
 */
@Composable
fun DialogoRequiereSesion(
    mensaje: String,
    onIniciarSesion: () -> Unit,
    onRegistrarse: () -> Unit,
    onCerrar: () -> Unit,
) {
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Entrada suave: leve escala + fundido.
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }
        val escala by animateFloatAsState(if (visible) 1f else 0.94f, tween(200), label = "escala")
        val opacidad by animateFloatAsState(if (visible) 1f else 0f, tween(160), label = "opacidad")
        val forma = RoundedCornerShape(28.dp)
        Column(
            Modifier.padding(horizontal = 24.dp).widthIn(max = 400.dp).fillMaxWidth()
                .graphicsLayer { scaleX = escala; scaleY = escala; alpha = opacidad }
                .shadow(16.dp, forma).clip(forma).background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("Iniciá sesión para continuar", style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
            Text(mensaje, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onIniciarSesion, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Iniciar sesión", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(onClick = onRegistrarse, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Text("Crear cuenta", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            TextButton(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) {
                Text("Seguir explorando", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Inicio para el modo invitado: buscar es libre; lo que requiere cuenta muestra el aviso. */
@Composable
fun PanelInvitado(
    onCatalogo: () -> Unit,
    onIniciarSesion: () -> Unit,
    pedirSesion: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SaludoInicio("Hola", "¿Qué medicamento buscás hoy?", iniciales = null)
        BuscadorEntrada("Buscá un medicamento…", onCatalogo)
        // Invitación a crear cuenta: clara y sin bloquear la exploración.
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Estás navegando como invitado", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Podés buscar medicamentos y ver farmacias con stock. Para reservar y retirar con tu código, iniciá sesión.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Row {
                    Button(onClick = onIniciarSesion, shape = RoundedCornerShape(12.dp)) {
                        Text("Iniciar sesión", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        SeccionTitulo("Con tu cuenta")
        AccesoTarjeta("Mis reservas", "Requiere iniciar sesión", Icons.AutoMirrored.Filled.ReceiptLong,
            onClick = { pedirSesion("Para ver y gestionar tus reservas necesitás iniciar sesión.") })
        AccesoTarjeta("Mis recetas", "Requiere iniciar sesión", Icons.Default.Medication,
            onClick = { pedirSesion("Para vincular y consultar tus recetas necesitás iniciar sesión.") })
        Spacer(Modifier.height(8.dp))
    }
}
