package hu.padlas

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import hu.padlas.settings.AppearancePreferences
import javax.inject.Inject
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.feature.index.IndexRepository
import hu.padlas.feature.index.IndexScheduler
import hu.padlas.feature.index.MediaStoreObserver
import hu.padlas.navigation.PadlasNavHost
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appearance: AppearancePreferences
    @Inject lateinit var index: IndexRepository
    @Inject lateinit var mediaChanges: MediaStoreObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        // Transparent bars on both ends: the app paints its own bottom bar, and the system's
        // contrast scrim would otherwise draw a second, darker strip under the gesture area.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        window.isNavigationBarContrastEnforced = false
        // New photos show up while the app is open, not at the next six-hourly pass: each
        // quiet spell after a MediaStore change runs the cheap incremental sync, and so does
        // coming back to the app, for whatever was shot or deleted while it was away.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Until the first full scan lands there is nothing to bring up to date.
                index.indexed.first { it }
                IndexScheduler.enqueueIncrementalNow(this@MainActivity)
                mediaChanges.changes.collect { IndexScheduler.enqueueIncrementalNow(this@MainActivity) }
            }
        }
        setContent {
            val live by appearance.liveBackground.collectAsState()
            PadlasTheme(liveBackground = live) {
                PadlasNavHost(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Ink.Background),
                )
            }
        }
    }
}
