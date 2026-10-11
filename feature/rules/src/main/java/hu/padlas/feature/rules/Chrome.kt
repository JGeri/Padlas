package hu.padlas.feature.rules

import android.os.SystemClock
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import hu.padlas.core.design.component.frosted
import hu.padlas.core.design.component.hairlineBottom
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.motionSpec
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

private const val EntranceWindowMs = 450L
private const val EntranceStepMs = 45L
private val EntranceRise = 18.dp
