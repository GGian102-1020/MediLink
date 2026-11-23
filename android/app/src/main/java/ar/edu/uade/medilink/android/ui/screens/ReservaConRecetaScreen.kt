package ar.edu.uade.medilink.android.ui.screens

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.medilink.android.data.TIPOS_RECETA
import ar.edu.uade.medilink.android.ui.viewmodel.RecetaLista
import ar.edu.uade.medilink.android.ui.viewmodel.ReservaConRecetaVm

/**
 * Paso 1 de una reserva BAJO_RECETA, antes de ver farmacias: el paciente adjunta la receta
 * (PDF o imagen) o la escanea con la cámara, indica la cantidad y toca "Continuar".
 */
@Composable
internal fun ReservaConRecetaScreen(
    vm: ReservaConRecetaVm,
    onContinuar: (RecetaLista) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var cantidad by rememberSaveable { mutableStateOf("1") }
    var usarCodigo by rememberSaveable { mutableStateOf(false) }
    var codigo by rememberSaveable { mutableStateOf("") }
    val tope = 10
    val numero = cantidad.toIntOrNull()
    val cantidadOk = numero != null && numero in 1..tope

    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), vm::finalizarCaptura)
    val documentos = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), vm::finalizarArchivo)
    val escanear = {
        try { camara.launch(vm.abrirCamara()) }
        catch (_: ActivityNotFoundException) { vm.falloSelector("No encontramos una app de cámara en el dispositivo.") }
    }
    val elegirArchivo = {
        try { documentos.launch(TIPOS_RECETA) }
        catch (_: ActivityNotFoundException) { vm.falloSelector("No encontramos un selector de archivos en el dispositivo.") }
    }
    LaunchedEffect(state.lista) { state.lista?.let { vm.navegacionHecha(); onContinuar(it) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Paso 1 de 2", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Encabezado("Adjuntá tu receta", "Este medicamento se vende bajo receta. Adjuntala y después elegí la farmacia donde retirarlo.")

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Resumen(Icons.Default.Medication, state.presentacion?.medicamentoNombre ?: "Medicamento",
                    state.presentacion?.let { "${it.concentracionTexto} · ${it.formaFarmaceutica} · ${envaseLegible(it.unidadExpendio, it.cantidadEnvase)}" })
            }
        }

        CampoTexto(value = cantidad,
            onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) cantidad = it },
            label = { Text("Cantidad (máximo $tope)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = cantidad.isNotEmpty() && !cantidadOk,
            supportingText = { Text("Debe coincidir con lo indicado en la receta.") },
            singleLine = true, modifier = Modifier.fillMaxWidth())

        if (!usarCodigo) {
            SeccionTitulo("Tu receta", "PDF o imagen (JPG, PNG o WebP), hasta 6 MB.")
            val adjunto = state.adjunto
            when {
                state.preparando -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                adjunto != null -> ArchivoAdjunto(
                    nombre = adjunto.nombre,
                    detalle = buildString {
                        append(if (adjunto.esImagen) "Imagen" else "PDF")
                        if (state.escaneado) append(" · escaneada con la cámara")
                        append(" · ${tamanoLegible(adjunto.archivo.length())}")
                    },
                    icono = if (adjunto.esImagen) Icons.Default.Image else Icons.Default.PictureAsPdf,
                    habilitado = !state.enviando,
                    onQuitar = vm::quitarAdjunto,
                )
                else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OpcionAdjuntar("Escanear", "Sacale una foto con la cámara", Icons.Default.DocumentScanner,
                        Modifier.weight(1f), escanear)
                    OpcionAdjuntar("Subir archivo", "PDF o imagen del teléfono", Icons.Default.UploadFile,
                        Modifier.weight(1f), elegirArchivo)
                }
            }
            if (adjunto != null && !state.enviando) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = escanear, modifier = Modifier.weight(1f)) { Text("Escanear otra") }
                    OutlinedButton(onClick = elegirArchivo, modifier = Modifier.weight(1f)) { Text("Elegir otro") }
                }
            }
        } else {
            SeccionTitulo("Código de receta electrónica", "Lo encontrás impreso en tu receta (por ejemplo, MLD-…).")
            CampoTexto(value = codigo, onValueChange = { codigo = it.take(80) },
                label = { Text("Código de la receta") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        TextButton(onClick = { usarCodigo = !usarCodigo }, enabled = !state.enviando,
            modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (usarCodigo) "Prefiero adjuntar la receta" else "Tengo un código de receta electrónica",
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }

        AvisoError(state.error)
        val listo = cantidadOk && !state.enviando &&
            if (usarCodigo) codigo.isNotBlank() else state.adjunto != null && !state.preparando
        Button(onClick = {
            val n = requireNotNull(numero)
            if (usarCodigo) vm.continuarConCodigo(codigo, n) else vm.continuar(n)
        }, enabled = listo, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (state.enviando) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(10.dp))
                Text("Enviando receta…")
            } else Text("Continuar", fontWeight = FontWeight.Bold)
        }
        Text("En el siguiente paso vas a ver las farmacias que tienen este medicamento. La farmacia revisa tu receta antes de entregarlo.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Resumen(icono: ImageVector, titulo: String, detalle: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center) { Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            detalle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun OpcionAdjuntar(titulo: String, detalle: String, icono: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.height(150.dp), shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = .35f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center) {
                Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun ArchivoAdjunto(nombre: String, detalle: String, icono: ImageVector, habilitado: Boolean, onQuitar: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(46.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center) { Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Receta adjunta", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Text(nombre, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onQuitar, enabled = habilitado) { Icon(Icons.Default.Close, contentDescription = "Quitar receta") }
        }
    }
}

private fun tamanoLegible(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1048576.0).replace('.', ',')
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
