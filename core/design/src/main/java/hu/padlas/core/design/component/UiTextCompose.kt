package hu.padlas.core.design.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import hu.padlas.core.common.text.UiText

/** Resolves [UiText] in the app's current language, and again when that changes. */
@Composable
@ReadOnlyComposable
fun UiText.asString(): String {
    LocalConfiguration.current
    return resolve(LocalContext.current.resources)
}
