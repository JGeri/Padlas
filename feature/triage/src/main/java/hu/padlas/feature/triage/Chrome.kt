package hu.padlas.feature.triage

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.RollingNumber
import hu.padlas.core.design.component.frosted
import hu.padlas.core.design.component.glass
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

/** What the list keeps free under its last row, so the floating action never hides it. */
internal val PinnedBarClearance = 144.dp

private val TopBarHeight = 64.dp

@Composable
internal fun navigationBarBottom(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** Where a list under the floating top bar starts, so its first row sits below the title. */
@Composable
internal fun topBarClearance(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarHeight

/**
 * The frame every pushed screen of the module shares. The aurora and the list are one blur
 * source, so the bars floating over them frost exactly what slides underneath; the top bar
 * only turns to glass once something actually is underneath it.
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

/**
 * The screen's one primary action, floating over the list on a pane of frosted glass. It
 * rises on a spring when there is something to do and sinks away when there is not.
 */
@Composable
internal fun PinnedActionBar(
    visible: Boolean,
    haze: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxWidth(),
        enter = slideInVertically(if (reduced) snap() else Motion.weight()) { it } + fadeIn(finite(reduced)),
        exit = slideOutVertically(finite(reduced)) { it } + fadeOut(finite(reduced)),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = Space.m, end = Space.m, bottom = Space.s)
                .fillMaxWidth()
                .frostedPane(haze)
                .padding(start = Space.l, end = Space.l, top = Space.m, bottom = Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.s),
            content = content,
        )
    }
}

/** A floating sheet of glass: blurred backdrop, then the lit rim on top of it. */
internal fun Modifier.frostedPane(haze: HazeState): Modifier = this
    .glass(Shapes.sheet, Color.Transparent, Ink.HairlineStrong)
    .frosted(haze)

/**
 * The two figures a batch action is about. Both roll, so ticking one more tile visibly
 * moves the count instead of swapping the label.
 */
@Composable
internal fun BarFigures(count: Int, noun: String, bytes: Long, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
            RollingNumber(value = Format.count(count), style = PadlasTheme.type.numericLarge, color = Ink.Text)
            Text(
                text = " $noun",
                style = PadlasTheme.type.label,
                color = Ink.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Spacer(Modifier.width(Space.s))
        RollingNumber(value = Format.bytesValue(bytes), style = PadlasTheme.type.numericLarge, color = Ink.Text)
        Text(
            text = " " + Format.bytesUnit(bytes),
            style = PadlasTheme.type.label,
            color = Ink.TextSecondary,
            maxLines = 1,
            modifier = Modifier.padding(bottom = 3.dp),
        )
    }
}

/** The figure the screen exists for, set large on a lit card, with one sentence on what it means. */
@Composable
internal fun ReclaimHero(
    label: String,
    bytes: Long,
    caption: String?,
    modifier: Modifier = Modifier,
    glow: Color = Ink.Accent,
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
            0f to color.copy(alpha = 0.26f),
            0.55f to color.copy(alpha = 0.07f),
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

/** Removals collapse and the rest closes up on a spring instead of jumping. */
@Composable
internal fun LazyGridItemScope.itemMotion(): Modifier =
    if (LocalReducedMotion.current) {
        Modifier
    } else {
        Modifier.animateItem(
            fadeInSpec = Motion.settle(),
            placementSpec = Motion.settle(),
            fadeOutSpec = Motion.snapSpring(),
        )
    }

internal fun popIn(reduced: Boolean) = scaleIn(if (reduced) snap() else Motion.bouncy(), initialScale = 0.6f) +
    fadeIn(finite(reduced))

internal fun <T> finite(reduced: Boolean): FiniteAnimationSpec<T> = if (reduced) snap() else Motion.settle()

private const val EntranceWindowMs = 450L
private const val EntranceStepMs = 45L
private val EntranceRise = 18.dp
