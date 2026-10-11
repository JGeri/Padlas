package hu.padlas.core.design.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.finite
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space
import hu.padlas.core.design.theme.auroraBackground

/** What a list keeps free under its last row, so the [PinnedActionBar] never hides it. */
val PinnedBarClearance = 144.dp

/** [PadlasTopBar]'s height: the clearance below has to follow it. */
private val TopBarHeight = 64.dp

@Composable
fun navigationBarBottom(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** Where a list under the floating top bar starts, so its first row sits below the title. */
@Composable
fun topBarClearance(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarHeight

/**
 * The frame every pushed feature screen shares. The aurora and the list are one blur
 * source, so the bars floating over them frost exactly what slides underneath; the top bar
 * only turns to glass once something actually is underneath it.
 */
@Composable
fun GlassScreen(
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
fun PinnedActionBar(
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

/** A floating pane of glass: the list behind it blurred, then the lit rim on top. */
fun Modifier.frostedPane(haze: HazeState, shape: Shape = Shapes.sheet): Modifier = this
    .glass(shape, Color.Transparent, Ink.HairlineStrong)
    .frosted(haze)
