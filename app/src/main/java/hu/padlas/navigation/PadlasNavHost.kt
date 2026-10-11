package hu.padlas.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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

    /** Zero means "no preset"; the browser treats both axes as optional. */
    fun browse(olderThanDays: Int = 0, bucketId: Long = 0) =
        "$BROWSE?$BROWSE_DAYS=$olderThanDays&$BROWSE_BUCKET=$bucketId"
}

private val pushSpring = spring<IntOffset>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
private val fadeSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(): EnterTransition =
    slideInHorizontally(pushSpring) { it / 5 } + fadeIn(fadeSpring)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(): ExitTransition =
    slideOutHorizontally(pushSpring) { -it / 10 } + fadeOut(fadeSpring)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    slideInHorizontally(pushSpring) { -it / 10 } + fadeIn(fadeSpring)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    slideOutHorizontally(pushSpring) { it / 5 } + fadeOut(fadeSpring)

@Composable
fun PadlasNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val back: () -> Unit = { navController.popBackStack() }

    fun open(tool: Tool) {
        when (tool) {
            Tool.SWIPE -> tab = MainTab.SWIPE
            Tool.STORAGE -> tab = MainTab.STORAGE
            Tool.PENDING -> navController.navigate(Dest.PENDING)
            Tool.DUPLICATES -> navController.navigate(Dest.DEDUP)
            Tool.VIDEOS -> navController.navigate(Dest.VIDEO)
            Tool.DISCARD -> navController.navigate(Dest.DISCARD)
            Tool.BROWSE -> navController.navigate(Dest.browse())
            Tool.OLD_PHOTOS -> navController.navigate(Dest.browse(olderThanDays = 365))
            Tool.TRASH -> navController.navigate(Dest.TRASH)
            Tool.CLOUD -> navController.navigate(Dest.ATTIC)
            Tool.RULES -> navController.navigate(Dest.RULES)
            Tool.LOG -> navController.navigate(Dest.LOG)
            Tool.SETTINGS -> navController.navigate(Dest.SETTINGS)
        }
    }

    NavHost(
        navController = navController,
        startDestination = Dest.MAIN,
        modifier = modifier,
        enterTransition = { enter() },
        exitTransition = { exit() },
        popEnterTransition = { popEnter() },
        popExitTransition = { popExit() },
    ) {
        composable(Dest.MAIN) {
            MainScreen(
                tab = tab,
                onTabChange = { tab = it },
                onOpen = ::open,
                onStartSession = { navController.navigate(Dest.SESSION) },
            )
        }
        composable(
            Dest.SESSION,
            enterTransition = { slideInVertically(pushSpring) { it / 3 } + fadeIn(fadeSpring) },
            popExitTransition = { slideOutVertically(pushSpring) { it / 3 } + fadeOut(fadeSpring) },
        ) {
            SwipeSessionRoute(
                onClose = back,
                onNewSession = {
                    tab = MainTab.SWIPE
                    navController.popBackStack(Dest.MAIN, inclusive = false)
                },
                onOpenPending = { navController.navigate(Dest.PENDING) },
                onOpenTrash = { navController.navigate(Dest.TRASH) },
            )
        }
        composable(Dest.PENDING) { PendingDeletionsRoute(onBack = back) }
        composable(Dest.TRASH) { TrashRoute(onBack = back) }
        composable(Dest.SETTINGS) { SettingsRoute(onBack = back) }
        composable(Dest.INDEX) { IndexRoute(onFinished = back) }
        composable(Dest.DEDUP) { DedupRoute(onBack = back) }
        composable(Dest.DISCARD) { DiscardRoute(onBack = back) }
        composable(Dest.VIDEO) { VideoRoute(onBack = back) }
        composable(Dest.ATTIC) { AtticRoute(onBack = back) }
        composable(Dest.RULES) { RulesRoute(onBack = back) }
        composable(Dest.LOG) { LogRoute(onBack = back) }
        composable(
            route = "${Dest.BROWSE}?${Dest.BROWSE_DAYS}={${Dest.BROWSE_DAYS}}&${Dest.BROWSE_BUCKET}={${Dest.BROWSE_BUCKET}}",
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
        composable(
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
