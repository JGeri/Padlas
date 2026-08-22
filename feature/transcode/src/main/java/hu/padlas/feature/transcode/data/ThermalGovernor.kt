package hu.padlas.feature.transcode.data

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

/**
 * The Exynos 2200 in the European S22 Ultra throttles hard under sustained encoding: an
 * unsupervised night batch collapses to a fraction of its speed after half an hour. The
 * pipeline therefore stops between items whenever the device reports a thermal status
 * above MODERATE, and resumes on its own once the SoC has cooled.
 */
@Singleton
class ThermalGovernor @Inject constructor(@ApplicationContext context: Context) {

    private val power: PowerManager? = context.getSystemService(PowerManager::class.java)

    /** true while the device is too hot to keep encoding. Emits on every status change. */
    val throttled: Flow<Boolean> = callbackFlow {
        val pm = power
        if (pm == null) {
            send(false)
            awaitClose { }
            return@callbackFlow
        }
        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            trySend(status > PAUSE_ABOVE)
        }
        send(pm.currentThermalStatus > PAUSE_ABOVE)
        pm.addThermalStatusListener(listener)
        awaitClose { pm.removeThermalStatusListener(listener) }
    }.distinctUntilChanged()

    fun isThrottled(): Boolean = (power?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE) > PAUSE_ABOVE

    /** Suspends until the device is cool enough for the next item. */
    suspend fun awaitCool() {
        throttled.first { !it }
    }

    private companion object {
        /** Strictly above MODERATE: LIGHT and MODERATE are still safe to encode through. */
        val PAUSE_ABOVE = PowerManager.THERMAL_STATUS_MODERATE
    }
}
