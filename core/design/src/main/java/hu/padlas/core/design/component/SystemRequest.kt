package hu.padlas.core.design.component

import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Launches a MediaStore request and reports whether the user (or the system) said yes. A
 * sender the system refuses to start counts as a no instead of an exception, so the screen
 * releases its busy state and stays where it was.
 */
@Composable
fun rememberSystemRequest(onResult: (Boolean) -> Unit): (IntentSender) -> Unit {
    val callback = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        callback.value(result.resultCode == Activity.RESULT_OK)
    }
    return remember(launcher) {
        { sender: IntentSender ->
            try {
                launcher.launch(IntentSenderRequest.Builder(sender).build())
            } catch (e: RuntimeException) {
                callback.value(false)
            }
        }
    }
}
