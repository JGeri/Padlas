package hu.padlas.core.design.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One clock for every screen: the lights behind a screen that slides in are exactly where
 * the ones behind the screen it replaces were, so a transition never shows a seam.
 */
val LocalAuroraPhase = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(0.25f) }

/**
 * The lights drift a couple of pixels a frame at most, so the clock ticks at about 30 Hz
 * rather than at the display rate: half the frames or fewer on a 60–120 Hz panel, with no
 * visible difference. It waits on frames, so it stops while the app is in the background.
 */
@Composable
internal fun rememberAuroraPhase(live: Boolean): State<Float> {
    val phase = remember { mutableFloatStateOf(0.25f) }
    if (live) {
        LaunchedEffect(Unit) {
            val origin = withFrameMillis { it } - (phase.floatValue * AuroraPeriodMs).toLong()
            while (true) {
                withFrameMillis { now -> phase.floatValue = ((now - origin) % AuroraPeriodMs) / AuroraPeriodMs.toFloat() }
                delay(AuroraTickMs)
            }
        }
    }
    return phase
}

/**
 * The screen ground: near-black with three soft lights drifting on a long loop. Only the
 * draw phase reads the clock, so a moving background never recomposes anything, and the
 * lights get a layer of their own above and below: a tick repaints the gradients and
 * nothing else, while the screen on top keeps its recorded drawing.
 */
fun Modifier.auroraBackground(intensity: Float = 1f): Modifier = composed {
    val phase = LocalAuroraPhase.current
    graphicsLayer {}
        .drawBehind { drawAurora(phase.value, intensity) }
        .graphicsLayer {}
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
private const val AuroraTickMs = 24L
