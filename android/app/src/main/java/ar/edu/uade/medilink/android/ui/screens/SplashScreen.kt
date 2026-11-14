package ar.edu.uade.medilink.android.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.edu.uade.medilink.android.R
import ar.edu.uade.medilink.android.ui.theme.MediColors
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla de bienvenida: la cruz del logo entra con rebote, un pulso con los colores
 * de la marca, el texto "Medlink" sube y aparece; luego avisa con [onTerminado].
 * Siempre sobre blanco, igual que el logo original.
 */
@Composable
fun SplashScreen(onTerminado: () -> Unit) {
    val terminar by rememberUpdatedState(onTerminado)
    val escalaCruz = remember { Animatable(0.35f) }
    val giroCruz = remember { Animatable(-90f) }
    val alfaCruz = remember { Animatable(0f) }
    val alfaTexto = remember { Animatable(0f) }
    val subidaTexto = remember { Animatable(28f) }
    val alfaLema = remember { Animatable(0f) }
    val alfaFondo = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { alfaFondo.animateTo(1f, tween(900)) }
        listOf(
            async { alfaCruz.animateTo(1f, tween(350)) },
            async { escalaCruz.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) },
            async { giroCruz.animateTo(0f, tween(750, easing = FastOutSlowInEasing)) },
        ).awaitAll()
        listOf(
            async { alfaTexto.animateTo(1f, tween(450)) },
            async { subidaTexto.animateTo(0f, tween(550, easing = FastOutSlowInEasing)) },
        ).awaitAll()
        alfaLema.animateTo(1f, tween(400))
        delay(900)
        terminar()
    }

    val infinita = rememberInfiniteTransition(label = "splash")
    val pulso by infinita.animateFloat(0f, 1f,
        infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "pulso")

    Box(
        modifier = Modifier.fillMaxSize().background(Color.White)
            .semantics { contentDescription = "MediLink, cargando" },
        contentAlignment = Alignment.Center,
    ) {
        // Manchas suaves de color detrás del logo.
        Canvas(Modifier.fillMaxSize().alpha(alfaFondo.value)) {
            drawCircle(Brush.radialGradient(listOf(MediColors.Sky.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(size.width * 0.1f, size.height * 0.12f), radius = size.width * 0.75f),
                radius = size.width * 0.75f, center = Offset(size.width * 0.1f, size.height * 0.12f))
            drawCircle(Brush.radialGradient(listOf(MediColors.Aqua.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(size.width * 0.95f, size.height * 0.9f), radius = size.width * 0.8f),
                radius = size.width * 0.8f, center = Offset(size.width * 0.95f, size.height * 0.9f))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
                // Pulso: dos anillos que se expanden y se desvanecen.
                Canvas(Modifier.fillMaxSize().alpha(alfaCruz.value)) {
                    val base = size.minDimension * 0.30f
                    listOf(pulso, (pulso + 0.5f) % 1f).forEach { p ->
                        drawCircle(
                            brush = Brush.sweepGradient(listOf(MediColors.Blue, MediColors.Sky, MediColors.Aqua, MediColors.Blue)),
                            radius = base + (size.minDimension * 0.22f) * p,
                            alpha = (1f - p) * 0.45f,
                            style = Stroke(width = 3.dp.toPx() * (1f - p) + 1f),
                        )
                    }
                }
                Image(
                    painter = painterResource(R.drawable.logo_cruz),
                    contentDescription = null,
                    modifier = Modifier.size(132.dp).graphicsLayer {
                        scaleX = escalaCruz.value; scaleY = escalaCruz.value
                        rotationZ = giroCruz.value; alpha = alfaCruz.value
                    },
                )
            }
            Spacer(Modifier.height(4.dp))
            Image(
                painter = painterResource(R.drawable.logo_texto),
                contentDescription = null,
                modifier = Modifier.width(210.dp)
                    .offset(y = subidaTexto.value.dp).alpha(alfaTexto.value),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Encontrá y reservá tus medicamentos",
                color = MediColors.Muted, fontSize = 15.sp,
                modifier = Modifier.alpha(alfaLema.value),
            )
        }

        PuntosCarga(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(bottom = 56.dp).alpha(alfaLema.value),
        )
    }
}

/** Tres puntos que saltan en secuencia con los colores de la cruz. */
@Composable
private fun PuntosCarga(modifier: Modifier = Modifier) {
    val infinita = rememberInfiniteTransition(label = "puntos")
    val fase by infinita.animateFloat(0f, 3f,
        infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "fase")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(MediColors.Blue, MediColors.Sky, MediColors.Aqua).forEachIndexed { i, color ->
            val d = ((fase - i + 3f) % 3f)
            val salto = if (d < 1f) kotlin.math.sin(d * Math.PI).toFloat() else 0f
            Box(
                Modifier.offset(y = (-8 * salto).dp).size(10.dp).clip(CircleShape)
                    .background(color.copy(alpha = 0.55f + 0.45f * salto)),
            )
        }
    }
}
