package hu.padlas.core.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
 * Deleting thousands of files is a hold, not a tap. The fill runs linearly: a spring would
 * blur where you actually are, and here that is the only thing that matters.
 */
@Composable
fun HoldToConfirm(
    label: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    holdMillis: Int = 1500,
    enabled: Boolean = true,
) {
    val progress = remember { Animatable(0f) }
    val padlasHaptics = haptics()
    val reduced = LocalReducedMotion.current
    val confirm = rememberUpdatedState(onConfirm)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 54.dp)
            .clip(Shapes.pill)
            .background(if (enabled) Ink.Danger.copy(alpha = 0.22f) else Ink.SurfaceHigh)
            .drawWithContent {
                val fraction = progress.value
                if (fraction > 0f) drawRect(Ink.Danger, size = Size(size.width * fraction, size.height))
                drawContent()
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
                            val fill = launch {
                                val remaining = ((1f - progress.value) * holdMillis).toInt()
                                progress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(durationMillis = remaining.coerceAtLeast(1), easing = LinearEasing),
                                )
                                completed = true
                                release.cancel()
                            }
                            release.join()
                            ticks.cancel()
                            fill.cancel()
                            if (completed) {
                                padlasHaptics?.commit()
                                confirm.value()
                                progress.snapTo(0f)
                            } else {
                                padlasHaptics?.tick(0.2f)
                                progress.animateTo(0f, if (reduced) snap() else Motion.snapback())
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
            color = if (enabled) Color.White else Ink.TextTertiary,
            style = PadlasTheme.type.label,
            maxLines = 1,
        )
    }
}
