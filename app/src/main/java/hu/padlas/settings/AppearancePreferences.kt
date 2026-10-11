package hu.padlas.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AppearancePreferences @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    private val _liveBackground = MutableStateFlow(prefs.getBoolean(KEY_LIVE, true))

    /** The drifting aurora redraws every frame; some people prefer it still, and the battery too. */
    val liveBackground: StateFlow<Boolean> = _liveBackground.asStateFlow()

    fun setLiveBackground(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LIVE, enabled).apply()
        _liveBackground.value = enabled
    }

    private companion object {
        const val KEY_LIVE = "live_background"
    }
}
