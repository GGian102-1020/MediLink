package ar.edu.uade.medilink.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.VisualTransformation
import kotlin.math.cos
import kotlin.math.sin
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

/** Pares de círculos que se cruzan; la intersección queda "calada" (estilo del login). */
@Composable
fun CirculosMarca(modifier: Modifier = Modifier, escala: Float = 1f) {
    val pares = remember {
        // x, y (fracción del ancho), radio (fracción del ancho), ángulo del segundo círculo.
        listOf(
            floatArrayOf(0.02f, 0.05f, 0.11f, 20f), floatArrayOf(0.40f, 0.02f, 0.10f, 110f),
            floatArrayOf(0.88f, 0.00f, 0.12f, 200f), floatArrayOf(0.20f, 0.36f, 0.10f, 300f),
            floatArrayOf(0.62f, 0.28f, 0.09f, 40f), floatArrayOf(0.95f, 0.30f, 0.10f, 150f),
            floatArrayOf(0.05f, 0.62f, 0.11f, 250f), floatArrayOf(0.48f, 0.62f, 0.10f, 330f),
            floatArrayOf(0.84f, 0.66f, 0.11f, 70f), floatArrayOf(0.26f, 0.90f, 0.10f, 180f),
            floatArrayOf(0.70f, 0.95f, 0.09f, 280f), floatArrayOf(1.00f, 1.00f, 0.10f, 10f),
        )
    }
    val colores = listOf(Color(0xFF26457A), Color(0xFF1B5470), Color(0xFF2B4F8F))
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val w = size.width
        pares.forEachIndexed { i, p ->
            val radio = p[2] * w * escala
            val c1 = Offset(p[0] * w, p[1] * w * 1.6f * escala)
            val rad = Math.toRadians(p[3].toDouble())
            val c2 = c1 + Offset((cos(rad) * radio * 1.2f).toFloat(), (sin(rad) * radio * 1.2f).toFloat())
            val color = colores[i % colores.size]
            drawCircle(color, radio, c1, blendMode = BlendMode.Xor)
            drawCircle(color, radio, c2, blendMode = BlendMode.Xor)
        }
    }
}

/** Panel destacado de cada sección: azul noche con los círculos de la marca. */
@Composable
fun HeroPanel(
    etiqueta: String,
    titulo: String,
    descripcion: String,
    icono: ImageVector,
    modifier: Modifier = Modifier,
    contenido: (@Composable () -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(topEnd = 56.dp, topStart = 24.dp,
        bottomStart = 24.dp, bottomEnd = 24.dp)).background(MediColors.Noche)) {
        CirculosMarca(Modifier.matchParentSize().alpha(0.55f), escala = 0.8f)
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) { Icon(icono, contentDescription = null, tint = MediColors.Teal) }
                Text(etiqueta.uppercase(), color = MediColors.Aqua,
                    style = MaterialTheme.typography.labelMedium, letterSpacing = 1.6.sp)
            }
            Text(titulo, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp, lineHeight = 32.sp)
            Text(descripcion, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
            contenido?.invoke()
        }
    }
}

/**
 * Campo de texto con el estilo del login: relleno gris, esquinas redondeadas y sin línea.
 * Acepta los mismos parámetros que se usaban de OutlinedTextField.
 */
@Composable
fun CampoTexto(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = RoundedCornerShape(18.dp),
) {
    val campo = if (MaterialTheme.colorScheme.surface.red < 0.3f) MaterialTheme.colorScheme.surfaceVariant
        else MediColors.CampoGris
    TextField(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled,
        readOnly = readOnly, label = label, placeholder = placeholder, leadingIcon = leadingIcon,
        trailingIcon = trailingIcon, supportingText = supportingText, isError = isError,
        visualTransformation = visualTransformation, keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions, singleLine = singleLine, maxLines = maxLines, minLines = minLines,
        shape = shape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = campo, unfocusedContainerColor = campo, errorContainerColor = campo,
            disabledContainerColor = campo.copy(alpha = 0.6f),
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
            focusedLabelColor = MediColors.Teal,
        ),
    )
}

@Composable
fun SeccionTitulo(titulo: String, descripcion: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
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
                .background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(titulo, style = MaterialTheme.typography.titleMedium)
                Text(descripcion, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ArrowForward, contentDescription = null,
                tint = MediColors.Teal, modifier = Modifier.size(20.dp))
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
