package hu.padlas.feature.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun StorageRoute(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues()) {
    Box(modifier.statusBarsPadding().padding(contentPadding)) { MapRoute(onBack = {}) }
}
