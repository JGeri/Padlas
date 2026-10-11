package hu.padlas.core.design.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One clock for every screen: the lights behind a screen that slides in are exactly where
 * the ones behind the screen it replaces were, so a transition never shows a seam.
 */
val LocalAuroraPhase = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(0.25f) }

@Composable
internal fun rememberAuroraPhase(live: Boolean): State<Float> {
    if (!live) return remember { mutableFloatStateOf(0.25f) }
    val transition = rememberInfiniteTransition(label = "aurora")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(AuroraPeriodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "auroraPhase",
    )
}

/**
 * The screen ground: near-black with three soft lights drifting on a long loop. Only the
 * draw phase reads the clock, so a moving background never recomposes anything.
 */
fun Modifier.auroraBackground(intensity: Float = 1f): Modifier = composed {
    val phase = LocalAuroraPhase.current
    drawBehind { drawAurora(phase.value, intensity) }
}

fun DrawScope.drawAurora(phase: Float, intensity: Float = 1f) {
    drawRect(Ink.Background)
    val t = phase * 2f * PI.toFloat()
    val w = size.width
    val h = size.height
    blob(Offset(w * (0.12f + 0.14f * sin(t)), h * (0.06f + 0.06f * cos(t * 2f))), w * 0.95f, Ink.AuroraViolet, 0.42f * intensity)
    blob(Offset(w * (0.96f - 0.14f * cos(t)), h * (0.40f + 0.08f * sin(t * 2f))), w * 0.85f, Ink.AuroraCyan, 0.24f * intensity)
    blob(Offset(w * (0.30f + 0.18f * sin(t + 1.3f)), h * (0.98f - 0.05f * cos(t))), w * 0.95f, Ink.AuroraMagenta, 0.20f * intensity)
}

private fun DrawScope.blob(center: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = alpha),
            0.5f to color.copy(alpha = alpha * 0.38f),
            1f to Color.Transparent,
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

private const val AuroraPeriodMs = 28_000
