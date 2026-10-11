package hu.padlas.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import hu.padlas.core.design.component.hairlineTop
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.haptics
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.feature.map.StorageRoute
import hu.padlas.feature.sort.setup.SwipeHomeRoute
import hu.padlas.home.HomeRoute
import hu.padlas.home.Tool

enum class MainTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("Áttekintés", Icons.Outlined.SpaceDashboard, Icons.Rounded.SpaceDashboard),
    SWIPE("Válogatás", Icons.Outlined.Swipe, Icons.Rounded.Swipe),
    STORAGE("Tárhely", Icons.Outlined.DonutLarge, Icons.Rounded.DonutLarge),
}

private val BarHeight = 64.dp

/** The three tabs. Each keeps its scroll position while another one is on screen. */
@Composable
fun MainScreen(
    tab: MainTab,
    onTabChange: (MainTab) -> Unit,
    onOpen: (Tool) -> Unit,
    onStartSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val holder = rememberSaveableStateHolder()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentPadding = PaddingValues(bottom = BarHeight + navInset)

    BackHandler(enabled = tab != MainTab.HOME) { onTabChange(MainTab.HOME) }

    Box(modifier = modifier.fillMaxSize().background(Ink.Background)) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(Motion.snapSpring()) togetherWith fadeOut(Motion.snapSpring()) },
            label = "tab",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            holder.SaveableStateProvider(current.name) {
                when (current) {
                    MainTab.HOME -> HomeRoute(
                        onOpen = { tool ->
                            when (tool) {
                                Tool.SWIPE -> onTabChange(MainTab.SWIPE)
                                Tool.STORAGE -> onTabChange(MainTab.STORAGE)
                                else -> onOpen(tool)
                            }
                        },
                        contentPadding = contentPadding,
                    )

                    MainTab.SWIPE -> SwipeHomeRoute(
                        onStartSession = onStartSession,
                        onOpenPending = { onOpen(Tool.PENDING) },
                        contentPadding = contentPadding,
                    )

                    MainTab.STORAGE -> StorageRoute(contentPadding = contentPadding)
                }
            }
        }

        BottomBar(
            selected = tab,
            onSelect = onTabChange,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun BottomBar(selected: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    val haptic = haptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Ink.Surface)
            .hairlineTop()
            .navigationBarsPadding()
            .height(BarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MainTab.entries.forEach { tab ->
            val active = tab == selected
            val tint by animateColorAsState(
                targetValue = if (active) Ink.Text else Ink.TextTertiary,
                animationSpec = motionSpec(Motion.settle()),
                label = "tabTint",
            )
            val indicator by animateFloatAsState(
                targetValue = if (active) 1f else 0f,
                animationSpec = motionSpec(Motion.settle()),
                label = "tabIndicator",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClick = {
                            if (!active) haptic?.tick(0.2f)
                            onSelect(tab)
                        },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(30.dp)
                        .scale(0.7f + 0.3f * indicator)
                        .clip(Shapes.pill)
                        .background(Ink.AccentSoft.copy(alpha = Ink.AccentSoft.alpha * indicator)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (active) tab.selectedIcon else tab.icon,
                        contentDescription = null,
                        tint = if (active) Ink.Accent else tint,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(text = tab.label, style = PadlasTheme.type.micro, color = tint)
            }
        }
    }
}
