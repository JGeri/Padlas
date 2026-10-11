package hu.padlas.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import hu.padlas.core.design.component.frosted
import hu.padlas.core.design.component.glass
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.haptics
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.auroraBackground
import hu.padlas.feature.map.StorageRoute
import hu.padlas.feature.sort.setup.SwipeHomeRoute
import hu.padlas.home.HomeRoute
import hu.padlas.home.Tool

enum class MainTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("Áttekintés", Icons.Outlined.SpaceDashboard, Icons.Rounded.SpaceDashboard),
    SWIPE("Válogatás", Icons.Outlined.Swipe, Icons.Rounded.Swipe),
    STORAGE("Tárhely", Icons.Outlined.DonutLarge, Icons.Rounded.DonutLarge),
}

private val BarHeight = 68.dp
private val BarMargin = 14.dp

/**
 * The three tabs share one aurora, drawn here once, and slide sideways in the direction of
 * the tab you picked. The tab bar floats over the content as frosted glass.
 */
@Composable
fun MainScreen(
    tab: MainTab,
    onTabChange: (MainTab) -> Unit,
    onOpen: (Tool, String?) -> Unit,
    onStartSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val holder = rememberSaveableStateHolder()
    val haze = rememberHazeState()
    val reduced = LocalReducedMotion.current
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val contentPadding = PaddingValues(bottom = BarHeight + BarMargin * 2 + navInset)

    BackHandler(enabled = tab != MainTab.HOME) { onTabChange(MainTab.HOME) }

    Box(modifier = modifier.fillMaxSize().auroraBackground()) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = { tabTransition(initialState.ordinal, targetState.ordinal, reduced) },
            label = "tab",
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(haze),
        ) { current ->
            holder.SaveableStateProvider(current.name) {
                when (current) {
                    MainTab.HOME -> HomeRoute(
                        onOpen = { tool, key ->
                            when (tool) {
                                Tool.SWIPE -> onTabChange(MainTab.SWIPE)
                                Tool.STORAGE -> onTabChange(MainTab.STORAGE)
                                else -> onOpen(tool, key)
                            }
                        },
                        contentPadding = contentPadding,
                    )

                    MainTab.SWIPE -> SwipeHomeRoute(
                        onStartSession = onStartSession,
                        onOpenPending = { onOpen(Tool.PENDING, "swipe-pending") },
                        contentPadding = contentPadding,
                    )

                    MainTab.STORAGE -> StorageRoute(contentPadding = contentPadding)
                }
            }
        }

        // The system bars are transparent; fade the content out under them so lists do not
        // run on behind the clock or the gesture handle.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(navInset + BarMargin + BarHeight / 2 + 28.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Ink.Background.copy(alpha = 0.78f),
                        0.7f to Ink.Background,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(statusInset + 20.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Ink.Background.copy(alpha = 0.92f),
                        0.6f to Ink.Background.copy(alpha = 0.6f),
                        1f to Color.Transparent,
                    ),
                ),
        )

        FloatingTabBar(
            selected = tab,
            onSelect = onTabChange,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = BarMargin)
                .shadow(28.dp, Shapes.pill, ambientColor = Color.Black, spotColor = Color.Black)
                .frosted(haze),
        )
    }
}

private fun tabTransition(from: Int, to: Int, reduced: Boolean): ContentTransform {
    val slide = spring<IntOffset>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
    val fade = spring<Float>(stiffness = Spring.StiffnessMedium)
    if (reduced) return fadeIn(fade) togetherWith fadeOut(fade)
    val direction = if (to > from) 1 else -1
    return (slideInHorizontally(slide) { it / 5 * direction } + fadeIn(fade)) togetherWith
        (slideOutHorizontally(slide) { -it / 5 * direction } + fadeOut(fade))
}

@Composable
private fun FloatingTabBar(selected: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    val haptic = haptics()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .glass(Shapes.pill, Color.Transparent, Ink.HairlineStrong)
            .padding(6.dp),
    ) {
        val slot = maxWidth / MainTab.entries.size
        val indicator by animateDpAsState(
            targetValue = slot * selected.ordinal,
            animationSpec = motionSpec(spring(dampingRatio = 0.62f, stiffness = 380f)),
            label = "tabIndicator",
        )
        Box(
            modifier = Modifier
                .offset(x = indicator)
                .width(slot)
                .fillMaxHeight()
                .glass(Shapes.pill, Ink.AccentSoft, Ink.Accent.copy(alpha = 0.45f)),
        )
        Row(modifier = Modifier.fillMaxSize()) {
            MainTab.entries.forEach { tab ->
                val active = tab == selected
                val tint by animateColorAsState(
                    targetValue = if (active) Ink.Text else Ink.TextTertiary,
                    animationSpec = motionSpec(Motion.settle()),
                    label = "tabTint",
                )
                val lift by animateFloatAsState(
                    targetValue = if (active) 1f else 0f,
                    animationSpec = motionSpec(Motion.bouncy()),
                    label = "tabLift",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = {
                                if (!active) haptic?.tick(0.25f)
                                onSelect(tab)
                            },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = if (active) tab.selectedIcon else tab.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier
                            .size(22.dp)
                            .scale(1f + 0.12f * lift),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(text = tab.label, style = PadlasTheme.type.micro, color = tint)
                }
            }
        }
    }
}
