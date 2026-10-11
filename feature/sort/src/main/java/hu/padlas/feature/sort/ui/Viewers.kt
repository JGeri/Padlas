package hu.padlas.feature.sort.ui

import android.app.Activity
import android.content.IntentSender
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import hu.padlas.core.design.component.PhotoViewer

/** Launches a MediaStore request and reports whether the user (or the system) said yes. */
@Composable
internal fun rememberSystemRequest(onResult: (Boolean) -> Unit): (IntentSender) -> Unit {
    val callback = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        callback.value(result.resultCode == Activity.RESULT_OK)
    }
    return remember(launcher) {
        { sender: IntentSender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) }
    }
}

/** The photo itself, full screen, through the design system's viewer. */
@Composable
internal fun ZoomViewer(uri: Uri, onDismiss: () -> Unit) {
    PhotoViewer(onDismiss = onDismiss) { modifier ->
        AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
    }
}
