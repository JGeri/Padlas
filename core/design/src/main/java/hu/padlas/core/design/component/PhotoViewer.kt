package hu.padlas.core.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.Space
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Full-screen photo for checking focus before deciding. The picture is a slot because
 * core:design does not load images: callers pass an AsyncImage with ContentScale.Fit.
 * It springs open out of the card,
 * pinches and double-taps to zoom, and a downward flick sends it back, the backdrop fading
 * with the distance so the gesture always shows where it leads.
 */
@Composable
fun PhotoViewer(onDismiss: () -> Unit, image: @Composable (Modifier) -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val scope = rememberCoroutineScope()
        val reduced = LocalReducedMotion.current
        val appear = remember { Animatable(if (reduced) 1f else 0f) }
        val scale = remember { Animatable(1f) }
        val pan = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        val pull = remember { Animatable(0f) }
        val dismiss = rememberUpdatedState(onDismiss)

        LaunchedEffect(Unit) { appear.animateTo(1f, spring(0.78f, Spring.StiffnessMediumLow)) }

        fun close() {
            scope.launch {
                appear.animateTo(0f, spring(1f, Spring.StiffnessMedium))
                dismiss.value()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val fade = (1f - abs(pull.value) / (size.height * 0.5f)).coerceIn(0f, 1f)
                    drawRect(Color.Black.copy(alpha = appear.value.coerceIn(0f, 1f) * fade))
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scope.launch {
                                val zoomed = scale.value > 1.05f
                                launch { scale.animateTo(if (zoomed) 1f else 2.5f, spring(0.8f, Spring.StiffnessMediumLow)) }
                                launch { pan.animateTo(Offset.Zero, spring(0.8f, Spring.StiffnessMediumLow)) }
                            }
                        },
                        onTap = { if (scale.value <= 1.05f) close() },
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, amount ->
                            if (scale.value > 1.05f) return@detectVerticalDragGestures
                            change.consume()
                            scope.launch { pull.snapTo(pull.value + amount) }
                        },
                        onDragEnd = {
                            if (abs(pull.value) > size.height * 0.16f) {
                                close()
                            } else {
                                scope.launch { pull.animateTo(0f, spring(0.6f, Spring.StiffnessMedium)) }
                            }
                        },
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
            image(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val open = appear.value
                        val shrink = 1f - (abs(pull.value) / size.height).coerceIn(0f, 0.3f)
                        scaleX = scale.value * (0.86f + 0.14f * open) * shrink
                        scaleY = scale.value * (0.86f + 0.14f * open) * shrink
                        translationX = pan.value.x
                        translationY = pan.value.y + pull.value
                        alpha = open.coerceIn(0f, 1f)
                    },
            )
            PadlasIconButton(
                icon = Icons.Rounded.Close,
                contentDescription = "Bezárás",
                onClick = ::close,
                background = Ink.SurfaceSolid.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(Space.l)
                    .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) },
            )
        }
    }
}
