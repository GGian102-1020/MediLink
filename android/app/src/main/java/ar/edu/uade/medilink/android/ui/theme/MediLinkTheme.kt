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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Paleta tomada del logo MediFarm: azul marino de "Medi", la cruz azul→celeste
 * y el verde agua de "link". No cambia endpoints, sesión ni reglas de negocio.
 */
object MediColors {
    val Navy = Color(0xFF004EA1)      // "Medi"
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
    val CampoGris = Color(0xFFF0F3F7) // campos de texto (gris muy suave)
    /** Acento principal del rediseño claro: teal de la marca con contraste AA sobre blanco. */
    val Primario = Color(0xFF00857D)
    val PrimarioSuave = Color(0xFFE3F5F3)
    val Fondo = Color(0xFFF6F8FA)
    val Borde = Color(0xFFE6EBF0)
}

/**
 * Rediseño "clara y limpia" (regla 60/30/10): 60% fondo neutro claro, 30% texto tinta,
 * 10% acento teal reservado para acciones y estados clave.
 */
private val Light = lightColorScheme(
    primary = MediColors.Primario,
    onPrimary = Color.White,
    primaryContainer = MediColors.PrimarioSuave,
    onPrimaryContainer = Color(0xFF00504B),
    secondary = MediColors.Navy,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEF3F8),
    onSecondaryContainer = Color(0xFF3D4B5C),
    tertiary = MediColors.Navy,
    background = MediColors.Fondo,
    onBackground = MediColors.Ink,
    surface = Color.White,
    onSurface = MediColors.Ink,
    surfaceVariant = Color(0xFFF0F3F7),
    onSurfaceVariant = MediColors.Muted,
    outline = Color(0xFFC5CFDA),
    outlineVariant = MediColors.Borde,
    // Sin estos tonos Material 3 usa sus contenedores violáceos por defecto (Card, diálogos, menús).
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFBFC),
    surfaceContainer = Color(0xFFF4F6F9),
    surfaceContainerHigh = Color(0xFFEEF1F5),
    surfaceContainerHighest = Color(0xFFE8ECF1),
    error = Color(0xFFC62828),
    errorContainer = Color(0xFFFDECEC),
    onErrorContainer = Color(0xFF8C1D18),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF4FD8C8),
    onPrimary = Color(0xFF003732),
    primaryContainer = Color(0xFF0E3B3A),
    onPrimaryContainer = Color(0xFFB8F1EA),
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
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFF0B1320),
    surfaceContainerLow = Color(0xFF131D2D),
    surfaceContainer = Color(0xFF172234),
    surfaceContainerHigh = Color(0xFF1D2A3E),
    surfaceContainerHighest = Color(0xFF243248),
)

private val MediShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val BaseTypography = Typography()
/** Jerarquía corta: títulos 24/20/16, cuerpo 14, apoyo 12; pesos Regular y SemiBold/Bold. */
private val MediTypography = Typography(
        headlineLarge = BaseTypography.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp),
        headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
        titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        titleSmall = BaseTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = BaseTypography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = BaseTypography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = BaseTypography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
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
