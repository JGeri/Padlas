package hu.padlas.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import hu.padlas.feature.archive.ArchiveScheduler
import hu.padlas.feature.archive.ui.AtticRoute
import hu.padlas.feature.audit.LogRoute
import hu.padlas.feature.dedup.DedupRoute
import hu.padlas.feature.index.IndexRoute
import hu.padlas.feature.map.MapRoute
import hu.padlas.feature.rules.RulesRoute
import hu.padlas.feature.sort.SortRoute
import hu.padlas.feature.transcode.VideoRoute
import hu.padlas.feature.triage.DiscardRoute
import hu.padlas.feature.triage.FolderRoute
import hu.padlas.vault.VaultRoute

object Dest {
    const val VAULT = "vault"
    const val INDEX = "index"
    const val MAP = "map"
    const val DEDUP = "dedup"
    const val DISCARD = "discard"
    const val VIDEO = "video"
    const val ATTIC = "attic"
    const val SORT = "sort"
    const val RULES = "rules"
    const val LOG = "log"
    const val FOLDER = "folder"
    const val FOLDER_ARG = "bucketId"
    fun folder(bucketId: Long) = "$FOLDER/$bucketId"
}

@Composable
fun PadlasNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Dest.VAULT,
        modifier = modifier,
    ) {
        composable(Dest.VAULT) {
            VaultRoute(
                onOpenMap = { navController.navigate(Dest.MAP) },
                onOpenAttic = { navController.navigate(Dest.ATTIC) },
                onOpenLog = { navController.navigate(Dest.LOG) },
                onOpenDedup = { navController.navigate(Dest.DEDUP) },
                onOpenDiscard = { navController.navigate(Dest.DISCARD) },
                onOpenVideo = { navController.navigate(Dest.VIDEO) },
                onOpenSort = { navController.navigate(Dest.SORT) },
                onOpenRules = { navController.navigate(Dest.RULES) },
                onOpenIndex = { navController.navigate(Dest.INDEX) },
                onOpenFolder = { bucketId -> navController.navigate(Dest.folder(bucketId)) },
            )
        }
        composable(Dest.INDEX) {
            IndexRoute(onFinished = { navController.popBackStack() })
        }
        composable(Dest.MAP) { MapRoute(onBack = { navController.popBackStack() }) }
        composable(Dest.DEDUP) { DedupRoute(onBack = { navController.popBackStack() }) }
        composable(Dest.DISCARD) { DiscardRoute(onBack = { navController.popBackStack() }) }
        composable(Dest.VIDEO) { VideoRoute(onBack = { navController.popBackStack() }) }
        composable(Dest.ATTIC) { AtticRoute(onBack = { navController.popBackStack() }) }
        composable(Dest.SORT) {
            val context = LocalContext.current
            SortRoute(
                onBack = { navController.popBackStack() },
                // The rows are already queued; the deck only has to wake the worker.
                onArchiveRequested = { ArchiveScheduler.enqueueUploads(context) },
            )
        }
        composable(Dest.RULES) { RulesRoute(onBack = { navController.popBackStack() }) }
        composable(Dest.LOG) { LogRoute(onBack = { navController.popBackStack() }) }
        composable(
            route = "${Dest.FOLDER}/{${Dest.FOLDER_ARG}}",
            arguments = listOf(navArgument(Dest.FOLDER_ARG) { type = NavType.LongType }),
        ) { entry ->
            FolderRoute(
                bucketId = entry.arguments?.getLong(Dest.FOLDER_ARG) ?: 0L,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
