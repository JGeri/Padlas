package hu.padlas.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import hu.padlas.core.design.motion.LocalNavAnimatedVisibilityScope
import hu.padlas.core.design.motion.LocalSharedTransitionScope
import hu.padlas.core.design.motion.morphBounds
import hu.padlas.feature.archive.ArchiveScheduler
import hu.padlas.feature.archive.ui.AtticRoute
import hu.padlas.feature.audit.LogRoute
import hu.padlas.feature.browse.BrowseRoute
import hu.padlas.feature.browse.TrashRoute
import hu.padlas.feature.dedup.DedupRoute
import hu.padlas.feature.index.IndexRoute
import hu.padlas.feature.rules.RulesRoute
import hu.padlas.feature.sort.pending.PendingDeletionsRoute
import hu.padlas.feature.sort.session.SwipeSessionRoute
import hu.padlas.feature.transcode.VideoRoute
import hu.padlas.feature.triage.DiscardRoute
import hu.padlas.feature.triage.FolderRoute
import hu.padlas.home.Tool
import hu.padlas.main.MainScreen
import hu.padlas.main.MainTab
import hu.padlas.settings.SettingsRoute

object Dest {
    const val MAIN = "main"
    const val SESSION = "session"
    const val PENDING = "pending"
    const val TRASH = "trash"
    const val SETTINGS = "settings"
    const val INDEX = "index"
    const val DEDUP = "dedup"
    const val DISCARD = "discard"
    const val VIDEO = "video"
    const val ATTIC = "attic"
    const val RULES = "rules"
    const val LOG = "log"
    const val FOLDER = "folder"
    const val FOLDER_ARG = "bucketId"
    fun folder(bucketId: Long) = "$FOLDER/$bucketId"

    const val BROWSE = "browse"
    const val BROWSE_DAYS = "days"
    const val BROWSE_BUCKET = "bucket"
    const val BROWSE_ROUTE = "$BROWSE?$BROWSE_DAYS={$BROWSE_DAYS}&$BROWSE_BUCKET={$BROWSE_BUCKET}"

    /** Zero means "no preset"; the browser treats both axes as optional. */
    fun browse(olderThanDays: Int = 0, bucketId: Long = 0) =
        "$BROWSE?$BROWSE_DAYS=$olderThanDays&$BROWSE_BUCKET=$bucketId"
}

private val fadeSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
private val scaleSpring = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
private val sheetSpring = spring<IntOffset>(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)

/**
 * Fade through with depth: the new screen rises out of a slightly smaller scale while the
 * old one recedes. It works with the predictive back gesture (it can be scrubbed), and it
 * leaves room for container transforms, which carry their own motion.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(): EnterTransition =
    fadeIn(fadeSpring) + scaleIn(scaleSpring, initialScale = 0.94f)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(): ExitTransition =
    fadeOut(fadeSpring) + scaleOut(scaleSpring, targetScale = 1.04f)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    fadeIn(fadeSpring) + scaleIn(scaleSpring, initialScale = 1.04f)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    fadeOut(fadeSpring) + scaleOut(scaleSpring, targetScale = 0.94f)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PadlasNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val back: () -> Unit = { navController.popBackStack() }

    // Which element a destination grows out of. Kept per route, so the same tile is also
    // where the screen shrinks back into; a screen opened from anywhere else just fades.
    val morphKeys = remember { mutableStateMapOf<String, String>() }

    fun openRoute(route: String, morphKey: String?) {
        val base = route.substringBefore('?').substringBefore('/')
        if (morphKey != null) morphKeys[base] = morphKey else morphKeys.remove(base)
        navController.navigate(route)
    }

    fun open(tool: Tool, morphKey: String?) {
        when (tool) {
            Tool.SWIPE -> tab = MainTab.SWIPE
            Tool.STORAGE -> tab = MainTab.STORAGE
            Tool.PENDING -> openRoute(Dest.PENDING, morphKey)
            Tool.DUPLICATES -> openRoute(Dest.DEDUP, morphKey)
            Tool.VIDEOS -> openRoute(Dest.VIDEO, morphKey)
            Tool.DISCARD -> openRoute(Dest.DISCARD, morphKey)
            Tool.BROWSE -> openRoute(Dest.browse(), morphKey)
            Tool.OLD_PHOTOS -> openRoute(Dest.browse(olderThanDays = 365), morphKey)
            Tool.TRASH -> openRoute(Dest.TRASH, morphKey)
            Tool.CLOUD -> openRoute(Dest.ATTIC, morphKey)
            Tool.RULES -> openRoute(Dest.RULES, morphKey)
            Tool.LOG -> openRoute(Dest.LOG, morphKey)
            Tool.SETTINGS -> openRoute(Dest.SETTINGS, morphKey)
        }
    }

    SharedTransitionLayout(modifier = modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = navController,
                startDestination = Dest.MAIN,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { enter() },
                exitTransition = { exit() },
                popEnterTransition = { popEnter() },
                popExitTransition = { popExit() },
            ) {
                screen(Dest.MAIN) {
                    MainScreen(
                        tab = tab,
                        onTabChange = { tab = it },
                        onOpen = ::open,
                        onStartSession = { navController.navigate(Dest.SESSION) },
                    )
                }
                screen(
                    Dest.SESSION,
                    enter = { slideInVertically(sheetSpring) { it / 4 } + fadeIn(fadeSpring) },
                    popExit = { slideOutVertically(sheetSpring) { it / 4 } + fadeOut(fadeSpring) },
                ) {
                    SwipeSessionRoute(
                        onClose = back,
                        onNewSession = {
                            tab = MainTab.SWIPE
                            navController.popBackStack(Dest.MAIN, inclusive = false)
                        },
                        onOpenPending = { openRoute(Dest.PENDING, null) },
                        onOpenTrash = { openRoute(Dest.TRASH, null) },
                    )
                }
                morphing(Dest.PENDING, morphKeys) { PendingDeletionsRoute(onBack = back) }
                morphing(Dest.TRASH, morphKeys) { TrashRoute(onBack = back) }
                morphing(Dest.SETTINGS, morphKeys) { SettingsRoute(onBack = back) }
                screen(Dest.INDEX) { IndexRoute(onFinished = back) }
                morphing(Dest.DEDUP, morphKeys) { DedupRoute(onBack = back) }
                morphing(Dest.DISCARD, morphKeys) { DiscardRoute(onBack = back) }
                morphing(Dest.VIDEO, morphKeys) { VideoRoute(onBack = back) }
                morphing(Dest.ATTIC, morphKeys) { AtticRoute(onBack = back) }
                morphing(Dest.RULES, morphKeys) { RulesRoute(onBack = back) }
                morphing(Dest.LOG, morphKeys) { LogRoute(onBack = back) }
                morphing(
                    route = Dest.BROWSE_ROUTE,
                    morphKeys = morphKeys,
                    keyName = Dest.BROWSE,
                    arguments = listOf(
                        navArgument(Dest.BROWSE_DAYS) { type = NavType.IntType; defaultValue = 0 },
                        navArgument(Dest.BROWSE_BUCKET) { type = NavType.LongType; defaultValue = 0L },
                    ),
                ) { entry ->
                    val days = entry.arguments?.getInt(Dest.BROWSE_DAYS) ?: 0
                    val bucket = entry.arguments?.getLong(Dest.BROWSE_BUCKET) ?: 0L
                    BrowseRoute(
                        onBack = back,
                        olderThanDaysPreset = days.takeIf { it > 0 },
                        bucketPreset = bucket.takeIf { it > 0 },
                        onArchiveQueued = { ArchiveScheduler.enqueueUploads(context) },
                    )
                }
                screen(
                    route = "${Dest.FOLDER}/{${Dest.FOLDER_ARG}}",
                    arguments = listOf(navArgument(Dest.FOLDER_ARG) { type = NavType.LongType }),
                ) { entry ->
                    FolderRoute(
                        bucketId = entry.arguments?.getLong(Dest.FOLDER_ARG) ?: 0L,
                        onBack = back,
                    )
                }
            }
        }
    }
}

/** A destination that exposes its animation scope, so its content can take part in morphs. */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    enter: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition)? = null,
    popExit: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition)? = null,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(
        route = route,
        arguments = arguments,
        enterTransition = enter,
        popExitTransition = popExit,
    ) { entry ->
        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
            content(entry)
        }
    }
}

/** A destination that grows out of whichever tile opened it, and shrinks back into it. */
private fun NavGraphBuilder.morphing(
    route: String,
    morphKeys: Map<String, String>,
    keyName: String = route,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    screen(route, arguments) { entry ->
        val key = morphKeys[keyName]
        Box(modifier = if (key != null) Modifier.fillMaxSize().morphBounds(key) else Modifier.fillMaxSize()) {
            content(entry)
        }
    }
}
