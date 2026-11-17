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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/** Panel destacado: tarjeta blanca con borde suave, ícono en teal y título claro (sin fondos oscuros). */
@Composable
fun HeroPanel(
    etiqueta: String,
    titulo: String,
    descripcion: String,
    icono: ImageVector,
    modifier: Modifier = Modifier,
    contenido: (@Composable () -> Unit)? = null,
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)) }
                Text(etiqueta.uppercase(), color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(titulo, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineSmall)
            Text(descripcion, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            contenido?.invoke()
        }
    }
}

/** Buscador de entrada: se ve como un campo, pero al tocarlo abre la pantalla de búsqueda. */
@Composable
fun BuscadorEntrada(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp), color = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(texto, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f))
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
        Text(titulo, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        if (!descripcion.isNullOrBlank()) {
            Text(descripcion, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun MetricaTarjeta(valor: String, etiqueta: String, icono: ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center) {
                Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            // El valor manda; la etiqueta acompaña.
            Column {
                Text(valor, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(etiqueta, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(titulo, style = MaterialTheme.typography.titleMedium)
                Text(descripcion, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
    }
}

/** Tonos semánticos de las píldoras de estado (fondo, texto). */
enum class TonoPildora(val fondo: Color, val texto: Color) {
    EXITO(Color(0xFFDDF6EC), Color(0xFF0B6B47)),
    INFO(Color(0xFFE4F0FD), Color(0xFF004EA1)),
    ALERTA(Color(0xFFFFF1D6), Color(0xFF8A5300)),
    PELIGRO(Color(0xFFFDE8E8), Color(0xFF9B1C1C)),
    NEUTRO(Color(0xFFEDF2F8), Color(0xFF4A5768)),
}

private val ETIQUETAS_ESTADO = mapOf(
    "USUARIO" to "Paciente", "OPERADOR" to "Operador", "ADMINISTRADOR" to "Administrador",
    "BAJO_RECETA" to "Bajo receta", "VENTA_LIBRE" to "Venta libre",
)

fun tonoDeEstado(estado: String): TonoPildora = when (estado.uppercase()) {
    "ACTIVA", "ACTIVO", "HABILITADA", "VIGENTE", "VENTA LIBRE", "VENTA_LIBRE" -> TonoPildora.EXITO
    "COMPLETADA", "UTILIZADA", "USUARIO", "OPERADOR", "ADMINISTRADOR" -> TonoPildora.INFO
    "VENCIDA", "BAJO RECETA", "BAJO_RECETA", "SUSPENDIDA", "STOCK BAJO" -> TonoPildora.ALERTA
    "CANCELADA", "DESHABILITADA", "INACTIVA", "ANULADA", "SIN STOCK" -> TonoPildora.PELIGRO
    else -> TonoPildora.NEUTRO
}

fun etiquetaEstado(estado: String): String = ETIQUETAS_ESTADO[estado.uppercase()]
    ?: estado.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

@Composable
fun EstadoPildora(estado: String, tono: TonoPildora = tonoDeEstado(estado)) {
    Surface(color = tono.fondo, shape = RoundedCornerShape(50), modifier = Modifier) {
        Text(etiquetaEstado(estado),
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
            color = tono.texto)
    }
}

/** "Caja x 20" a partir de los datos de la presentación. */
fun envaseLegible(unidad: String, cantidad: Int) = "$unidad x $cantidad"

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
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
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
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Info, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
            Text(titulo, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(descripcion, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

fun plural(n: Number, uno: String, varios: String) = "$n ${if (n.toLong() == 1L) uno else varios}"

