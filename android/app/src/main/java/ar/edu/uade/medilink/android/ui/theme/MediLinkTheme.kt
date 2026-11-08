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

/** Sistema visual compartido; no cambia endpoints, sesión ni reglas de negocio. */
object MediColors {
    val Forest = Color(0xFF113F3D)
    val Teal = Color(0xFF08786D)
    val Mint = Color(0xFFD8F0E8)
    val Ink = Color(0xFF172C2B)
    val Muted = Color(0xFF617572)
    val Canvas = Color(0xFFF6F8F5)
    val Gold = Color(0xFFF6C779)
}

private val Light = lightColorScheme(
    primary = MediColors.Teal,
    onPrimary = Color.White,
    primaryContainer = MediColors.Mint,
    onPrimaryContainer = MediColors.Forest,
    secondary = MediColors.Forest,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7F0EC),
    onSecondaryContainer = MediColors.Forest,
    tertiary = Color(0xFF8C562B),
    background = MediColors.Canvas,
    onBackground = MediColors.Ink,
    surface = Color.White,
    onSurface = MediColors.Ink,
    surfaceVariant = Color(0xFFEDF2EF),
    onSurfaceVariant = MediColors.Muted,
    outline = Color(0xFFB6C9C2),
    outlineVariant = Color(0xFFDDE7E1),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF81D9C3),
    onPrimary = Color(0xFF003F37),
    primaryContainer = Color(0xFF125B51),
    onPrimaryContainer = Color(0xFFCEF2E3),
    secondary = Color(0xFFAAD4C8),
    onSecondary = Color(0xFF193D36),
    background = Color(0xFF101D1C),
    onBackground = Color(0xFFE7F0EC),
    surface = Color(0xFF192927),
    onSurface = Color(0xFFE7F0EC),
    surfaceVariant = Color(0xFF2B403C),
    onSurfaceVariant = Color(0xFFB5C7C1),
    outline = Color(0xFF728A83),
    outlineVariant = Color(0xFF354D46),
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
