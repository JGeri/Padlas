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
import dagger.hilt.android.AndroidEntryPoint
import hu.padlas.settings.AppearancePreferences
import javax.inject.Inject
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.navigation.PadlasNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appearance: AppearancePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        // Transparent bars on both ends: the app paints its own bottom bar, and the system's
        // contrast scrim would otherwise draw a second, darker strip under the gesture area.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        window.isNavigationBarContrastEnforced = false
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
