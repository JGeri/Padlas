package hu.padlas.feature.triage

import android.content.Context
import android.content.pm.ApplicationInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.padlas.core.common.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * What the installed apps say they are, by label: games flag themselves, and maps, social,
 * video and music apps declare a category. That covers the apps no dictionary could list,
 * a game the user installed last week among them.
 */
@Singleton
class InstalledApps @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    @Volatile
    private var cached: Map<String, ShotSource>? = null

    suspend fun sources(): Map<String, ShotSource> = cached ?: withContext(io) {
        runCatching {
            val packages = context.packageManager
            packages.getInstalledApplications(0).mapNotNull { info ->
                val source = sourceOf(info) ?: return@mapNotNull null
                ShotSources.normalize(packages.getApplicationLabel(info).toString()) to source
            }.toMap()
        }.getOrDefault(emptyMap())
    }.also { cached = it }

    @Suppress("DEPRECATION")
    private fun sourceOf(info: ApplicationInfo): ShotSource? = when {
        info.category == ApplicationInfo.CATEGORY_GAME || info.flags and ApplicationInfo.FLAG_IS_GAME != 0 ->
            ShotSource.GAME
        info.category == ApplicationInfo.CATEGORY_MAPS -> ShotSource.NAVIGATION
        info.category == ApplicationInfo.CATEGORY_SOCIAL -> ShotSource.SOCIAL
        info.category == ApplicationInfo.CATEGORY_VIDEO || info.category == ApplicationInfo.CATEGORY_AUDIO ->
            ShotSource.MEDIA
        else -> null
    }
}
