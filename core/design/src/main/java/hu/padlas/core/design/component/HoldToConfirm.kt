package hu.padlas.core.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.haptics
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TickStartMs = 400f
private const val TickEndMs = 80f

/**
 * The one animation in the app that is not meant to feel good. Deleting four thousand files
 * is not a tap, it is a hold - so the arc is drawn linearly and never with a spring: a
 * spring would blur where you actually are, and here that is the only thing that matters.
 */
@Composable
fun HoldToConfirm(
    label: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    holdMillis: Int = 2000,
    enabled: Boolean = true,
) {
    val progress = remember { Animatable(0f) }
    val padlasHaptics = haptics()
    val reduced = LocalReducedMotion.current
    val confirm = rememberUpdatedState(onConfirm)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 56.dp)
            .raisedSurface(
                shape = Shapes.card,
                background = if (enabled) Ink.Rust else Ink.Raised,
            )
            .drawWithCache {
                val outline = Shapes.card.createOutline(size, layoutDirection, this)
                val ring = Path().apply { addOutline(outline) }
                val measure = PathMeasure().apply { setPath(ring, true) }
                val length = measure.length
                val drawn = Path()
                val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                onDrawWithContent {
                    drawContent()
                    val fraction = progress.value
                    if (fraction > 0f) {
                        drawn.reset()
                        measure.getSegment(0f, length * fraction, drawn, true)
                        drawPath(drawn, color = Ink.Brass, style = stroke)
                    }
                }
            }
            .pointerInput(enabled, holdMillis) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        var completed = false
                        coroutineScope {
                            val release = launch { tryAwaitRelease() }
                            val ticks = launch {
                                var elapsed = 0f
                                while (isActive) {
                                    val fraction = (elapsed / holdMillis).coerceIn(0f, 1f)
                                    val interval = TickStartMs + (TickEndMs - TickStartMs) * fraction
                                    delay(interval.toLong())
                                    elapsed += interval
                                    padlasHaptics?.holdTick(fraction)
                                }
                            }
                            val arc = launch {
                                val remaining = ((1f - progress.value) * holdMillis).toInt()
                                progress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = remaining.coerceAtLeast(1),
                                        easing = LinearEasing,
                                    ),
                                )
                                completed = true
                                // The arc closed under the finger; there is nothing left to wait for.
                                release.cancel()
                            }
                            release.join()
                            ticks.cancel()
                            arc.cancel()
                            if (completed) {
                                padlasHaptics?.commit()
                                confirm.value()
                            } else {
                                // Nothing happened, and it has to look like nothing happened.
                                padlasHaptics?.tick(0.2f)
                                progress.animateTo(
                                    targetValue = 0f,
                                    animationSpec = if (reduced) snap() else Motion.snapback(),
                                )
                            }
                        }
                    },
                )
            }
            .padding(horizontal = Space.xl, vertical = Space.m),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) Ink.Paper else Ink.Dust,
            style = PadlasTheme.type.label,
            maxLines = 1,
        )
    }
}
