package hu.padlas.feature.sort.ui

import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize

/**
 * A TextureView rather than a SurfaceView: the card it sits on is dragged, rotated and
 * thrown, and only a texture follows a graphics layer transform.
 *
 * The poster under it stays visible until the first frame is actually on screen, so a
 * video never flashes black when it becomes the top card.
 */
@Composable
internal fun VideoSurface(
    player: Player,
    uri: Uri,
    modifier: Modifier = Modifier,
) {
    var aspect by remember(uri) { mutableFloatStateOf(0f) }
    var rendered by remember(uri) { mutableStateOf(false) }

    DisposableEffect(player, uri) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onRenderedFirstFrame() {
                rendered = true
            }
        }
        player.addListener(listener)
        player.setMediaItem(MediaItem.fromUri(uri))
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.prepare()
        player.playWhenReady = true
        onDispose {
            player.removeListener(listener)
            player.stop()
            player.clearMediaItems()
        }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val boxAspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 1f
        val surface = if (aspect > 0f) {
            Modifier
                .fillMaxSize()
                .wrapContentSize()
                .aspectRatio(aspect, matchHeightConstraintsFirst = aspect < boxAspect)
        } else {
            Modifier.fillMaxSize()
        }
        AndroidView(
            factory = { context -> TextureView(context) },
            update = { view -> player.setVideoTextureView(view) },
            onRelease = { view -> player.clearVideoTextureView(view) },
            modifier = surface.graphicsLayer { alpha = if (rendered) 1f else 0f },
        )
    }
}
