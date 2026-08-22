package hu.padlas.feature.sort

import android.content.Context
import android.location.Address
import android.location.Geocoder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Turns an event's GPS centroid into "Balaton". Never fails loudly: without a geocoder,
 * without a network or without a match the session simply keeps its folder name.
 */
@Singleton
internal class PlaceLabeler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val cache = ConcurrentHashMap<String, String>()

    suspend fun label(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val key = cacheKey(latitude, longitude)
        cache[key]?.let { return it }

        val name = lookup(latitude, longitude) ?: return null
        cache[key] = name
        return name
    }

    private suspend fun lookup(latitude: Double, longitude: Double): String? =
        suspendCancellableCoroutine { continuation ->
            val listener = object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    if (continuation.isActive) continuation.resumeWith(Result.success(addresses.firstOrNull()?.label()))
                }

                override fun onError(errorMessage: String?) {
                    if (continuation.isActive) continuation.resumeWith(Result.success(null))
                }
            }
            val started = runCatching {
                Geocoder(context, Locale.forLanguageTag("hu-HU"))
                    .getFromLocation(latitude, longitude, 1, listener)
            }.isSuccess
            if (!started && continuation.isActive) continuation.resumeWith(Result.success(null))
        }

    /** The sub-administrative area is what people call a place; the city is the fallback. */
    private fun Address.label(): String? =
        subAdminArea?.takeIf { it.isNotBlank() }
            ?: locality?.takeIf { it.isNotBlank() }
            ?: adminArea?.takeIf { it.isNotBlank() }

    /** Roughly a kilometre of resolution: two shots in one town share a lookup. */
    private fun cacheKey(latitude: Double, longitude: Double): String =
        "${(latitude * 100).roundToInt()}:${(longitude * 100).roundToInt()}"
}
