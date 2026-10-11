package hu.padlas.feature.map

import android.os.SystemClock
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import hu.padlas.core.design.component.frosted
import hu.padlas.core.design.component.glass
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.theme.Ink
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A floating pane of glass: the list behind it blurred, then the lit rim on top. */
internal fun Modifier.frostedPane(haze: HazeState, shape: Shape): Modifier = this
    .glass(shape, Color.Transparent, Ink.HairlineStrong)
    .frosted(haze)

/**
 * Hands out entrance slots in composition order. Only what is composed while the tab
 * opens sits down one after the other; rows scrolled in later are simply there.
 */
@Stable
internal class Entrance(private val enabled: Boolean) {
    private val start = SystemClock.uptimeMillis()
    private var next = 0

    fun claim(): Int? {
        if (!enabled || SystemClock.uptimeMillis() - start > EntranceWindowMs) return null
        return next++
    }
}

@Composable
internal fun rememberEntrance(): Entrance {
    val reduced = LocalReducedMotion.current
    return remember { Entrance(enabled = !reduced) }
}

/** Rises a few dp and fades in on its own spring, delayed by its place in the queue. */
@Composable
internal fun Modifier.entrance(entrance: Entrance): Modifier {
    val slot = remember { entrance.claim() } ?: return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(minOf(slot, Motion.StaggerCap).toLong() * EntranceStepMs)
        progress.animateTo(1f, Motion.settle())
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * EntranceRise.toPx()
    }
}

/**
 * Rows arriving with a new view fade in, rows that leave fade out, and the rest closes up
 * on a spring: switching views or deleting reads as one list rearranging itself.
 */
@Composable
internal fun LazyItemScope.itemMotion(): Modifier =
    if (LocalReducedMotion.current) {
        Modifier
    } else {
        Modifier.animateItem(
            fadeInSpec = Motion.settle(),
            placementSpec = Motion.settle(),
            fadeOutSpec = Motion.snapSpring(),
        )
    }

internal fun popIn(reduced: Boolean): EnterTransition =
    scaleIn(if (reduced) snap() else Motion.bouncy(), initialScale = 0.6f) + fadeIn(finite(reduced))

internal fun <T> finite(reduced: Boolean): FiniteAnimationSpec<T> = if (reduced) snap() else Motion.settle()

/**
 * The share bar, lit: every segment grows out of zero on arrival, one after the other, and
 * sits on a faint glow of its own colour. Later changes settle on a spring, so freed space
 * visibly flows out of its segment.
 */
@Composable
internal fun GlowSegmentBar(
    segments: List<Pair<Long, Color>>,
    total: Long,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
) {
    val reduced = LocalReducedMotion.current
    val widths = remember { mutableStateListOf<Animatable<Float, AnimationVector1D>>() }
    val grown = remember { mutableStateOf(false) }
    val targets = segments.map { (bytes, _) -> if (total > 0L) (bytes.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f }

    LaunchedEffect(targets) {
        // Only the very first run staggers. A live bar gets new targets several times a
        // second, and a stagger that restarted with each of them would never finish.
        val first = !grown.value
        grown.value = true
        while (widths.size < targets.size) widths.add(Animatable(0f))
        while (widths.size > targets.size) widths.removeAt(widths.lastIndex)
        coroutineScope {
            targets.forEachIndexed { index, target ->
                launch {
                    val bar = widths[index]
                    when {
                        reduced -> bar.snapTo(target)
                        first -> {
                            delay(minOf(index, Motion.StaggerCap).toLong() * SegmentStepMs)
                            bar.animateTo(target, Motion.weight())
                        }
                        else -> bar.animateTo(target, Motion.drain())
                    }
                }
            }
        }
    }

    val glow = GlowPad
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height + glow * 2),
    ) {
        val pad = glow.toPx()
        val barHeight = size.height - pad * 2f
        val radius = CornerRadius(barHeight / 2f)
        val gap = 2.dp.toPx()
        var x = 0f
        widths.forEachIndexed { index, bar ->
            val color = segments.getOrNull(index)?.second ?: return@forEachIndexed
            val width = bar.value * size.width
            if (width >= 1f) {
                drawRoundRect(
                    color = color.copy(alpha = 0.2f),
                    topLeft = Offset(x - pad * 0.5f, pad * 0.25f),
                    size = Size(width + pad, barHeight + pad * 1.5f),
                    cornerRadius = CornerRadius((barHeight + pad) / 2f),
                )
            }
            x += width
        }
        val track = Path().apply {
            addRoundRect(RoundRect(0f, pad, size.width, pad + barHeight, radius))
        }
        clipPath(track) {
            drawRect(Ink.SurfaceHigh, topLeft = Offset(0f, pad), size = Size(size.width, barHeight))
            x = 0f
            widths.forEachIndexed { index, bar ->
                val color = segments.getOrNull(index)?.second ?: return@forEachIndexed
                val width = bar.value * size.width
                if (width >= 1f) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to lerp(color, Color.White, 0.28f),
                            1f to color,
                            startY = pad,
                            endY = pad + barHeight,
                        ),
                        topLeft = Offset(x, pad),
                        size = Size((width - gap).coerceAtLeast(1f), barHeight),
                    )
                }
                x += width
            }
        }
    }
}

private const val EntranceWindowMs = 450L
private const val EntranceStepMs = 45L
private val EntranceRise = 18.dp
private const val SegmentStepMs = 70L
private val GlowPad = 4.dp
