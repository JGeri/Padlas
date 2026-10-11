package hu.padlas.feature.sort.ui

import android.app.Activity
import android.content.IntentSender
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import hu.padlas.core.design.component.PadlasIconButton
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.Space
import kotlinx.coroutines.launch

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

/** Full-screen photo with pinch and double-tap zoom, for checking focus before deciding. */
@Composable
internal fun ZoomViewer(uri: Uri, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val scope = rememberCoroutineScope()
        val scale = remember { Animatable(1f) }
        val pan = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scope.launch {
                                val zoomed = scale.value > 1.05f
                                launch { scale.animateTo(if (zoomed) 1f else 2.5f) }
                                launch { pan.animateTo(Offset.Zero) }
                            }
                        },
                        onTap = { if (scale.value <= 1.05f) onDismiss() },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        scope.launch {
                            val next = (scale.value * zoomChange).coerceIn(1f, 6f)
                            scale.snapTo(next)
                            val limitX = size.width * (next - 1f) / 2f
                            val limitY = size.height * (next - 1f) / 2f
                            val moved = pan.value + panChange
                            pan.snapTo(Offset(moved.x.coerceIn(-limitX, limitX), moved.y.coerceIn(-limitY, limitY)))
                        }
                    }
                },
        ) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        translationX = pan.value.x
                        translationY = pan.value.y
                    },
            )
            PadlasIconButton(
                icon = Icons.Rounded.Close,
                contentDescription = "Bezárás",
                onClick = onDismiss,
                background = Ink.Background.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(Space.l),
            )
        }
    }
}
