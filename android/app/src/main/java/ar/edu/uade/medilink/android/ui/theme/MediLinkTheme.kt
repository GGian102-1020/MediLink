package ar.edu.uade.medilink.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Paleta tomada del logo MediLink: azul marino de "Med", la cruz azul→celeste
 * y el verde agua de "link". No cambia endpoints, sesión ni reglas de negocio.
 */
object MediColors {
    val Navy = Color(0xFF004EA1)      // "Med"
    val Blue = Color(0xFF0675DD)      // brazo izquierdo de la cruz
    val Sky = Color(0xFF14ADFB)       // brazo superior
    val Teal = Color(0xFF00A39B)      // "link" (oscurecido apenas para contraste AA)
    val Aqua = Color(0xFF04D7BD)      // brazo derecho
    val IceBlue = Color(0xFFE4F0FD)
    val Ink = Color(0xFF14233A)
    val Muted = Color(0xFF5E6B7D)
    val Canvas = Color(0xFFF5F8FC)
    val Field = Color(0xFFF1F4F8)     // inputs tipo píldora del template
    val Noche = Color(0xFF0D1B33)     // fondo de encabezados y barras (estilo del login)
    val CampoGris = Color(0xFFE5E8ED) // campos de texto grises del login
}

private val Light = lightColorScheme(
    // Botones y acentos principales en azul noche, como el botón del login.
    primary = Color(0xFF13294B),
    onPrimary = Color.White,
    primaryContainer = MediColors.IceBlue,
    onPrimaryContainer = MediColors.Navy,
    secondary = MediColors.Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF6F3),
    onSecondaryContainer = Color(0xFF00504B),
    tertiary = MediColors.Navy,
    background = MediColors.Canvas,
    onBackground = MediColors.Ink,
    surface = Color.White,
    onSurface = MediColors.Ink,
    surfaceVariant = Color(0xFFEDF2F8),
    onSurfaceVariant = MediColors.Muted,
    outline = Color(0xFFB9C6D6),
    outlineVariant = Color(0xFFDDE5EF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8CC4FF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF0B4A8C),
    onPrimaryContainer = Color(0xFFD6E8FF),
    secondary = Color(0xFF5EE0CF),
    onSecondary = Color(0xFF003732),
    tertiary = Color(0xFFA9C8F5),
    background = Color(0xFF0E1726),
    onBackground = Color(0xFFE4ECF7),
    surface = Color(0xFF172234),
    onSurface = Color(0xFFE4ECF7),
    surfaceVariant = Color(0xFF26344A),
    onSurfaceVariant = Color(0xFFB4C2D4),
    outline = Color(0xFF6F8299),
    outlineVariant = Color(0xFF33445C),
)

private val MediShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private val BaseTypography = Typography()
private val MediTypography = Typography(
        headlineLarge = BaseTypography.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )

@Composable
fun MediLinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = MediTypography,
        shapes = MediShapes,
        content = content,
    )
}
