package hu.padlas.feature.audit

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.RollingNumber
import hu.padlas.core.design.component.frosted
import hu.padlas.core.design.component.hairlineBottom
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space
import hu.padlas.core.design.theme.auroraBackground
import kotlinx.coroutines.delay

private val TopBarHeight = 64.dp

@Composable
internal fun navigationBarBottom(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** Where a list under the floating top bar starts, so its first row sits below the title. */
@Composable
internal fun topBarClearance(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarHeight

/**
 * The aurora and the list are one blur source, so the top bar floating over them frosts
 * exactly what slides underneath; it only turns to glass once something is underneath.
 */
@Composable
internal fun GlassScreen(
    scrolled: Boolean,
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.(HazeState) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val haze = rememberHazeState()
    val glass by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = motionSpec(Motion.settle()),
        label = "topBarGlass",
    )
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(haze)
                .auroraBackground(),
            content = content,
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = glass }
                    .frosted(haze)
                    .hairlineBottom(),
            )
            Box(modifier = Modifier.statusBarsPadding()) { topBar() }
        }
        overlay(haze)
    }
}

/** The figure the screen exists for, set large on a lit card, with one sentence on what it means. */
@Composable
internal fun ReclaimHero(
    label: String,
    bytes: Long,
    caption: String?,
    modifier: Modifier = Modifier,
    glow: Color = Ink.Success,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    PadlasCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(Shapes.hero)
            .heroGlow(glow),
        shape = Shapes.hero,
        contentPadding = Space.xl,
    ) {
        Column {
            Text(text = label, style = PadlasTheme.type.label, color = Ink.TextSecondary, maxLines = 1)
            Spacer(Modifier.height(Space.xs))
            BigBytes(bytes = bytes)
            if (caption != null) {
                Spacer(Modifier.height(Space.s))
                Text(text = caption, style = PadlasTheme.type.body, color = Ink.TextSecondary)
            }
            content()
        }
    }
}

/** A light source in the corner of the hero card, the one place on the screen that glows. */
private fun Modifier.heroGlow(color: Color): Modifier = drawBehind {
    val radius = size.maxDimension * 0.75f
    val center = Offset(size.width * 0.92f, size.height * 0.05f)
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = 0.24f),
            0.55f to color.copy(alpha = 0.06f),
            1f to Color.Transparent,
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

@Composable
internal fun BigBytes(bytes: Long, modifier: Modifier = Modifier, color: Color = Ink.Text) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        RollingNumber(value = Format.bytesValue(bytes), style = PadlasTheme.type.display, color = color)
        Spacer(Modifier.width(Space.s))
        Text(
            text = Format.bytesUnit(bytes),
            style = PadlasTheme.type.title,
            color = Ink.TextSecondary,
            maxLines = 1,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

/**
 * Hands out entrance slots in composition order. Only what is composed while the screen
 * opens sits down one after the other; rows scrolled in later are simply there, because a
 * row that waits its turn mid-scroll reads as lag.
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

/** New entries slide in from the top and the rest of the day moves down on a spring. */
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

private const val EntranceWindowMs = 450L
private const val EntranceStepMs = 45L
private val EntranceRise = 18.dp
