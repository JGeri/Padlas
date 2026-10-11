package hu.padlas.feature.sort.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.entity.SwipeChoice
import hu.padlas.core.design.component.PadlasIconButton
import hu.padlas.core.design.component.Pill
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space
import hu.padlas.core.media.MediaThumb
import hu.padlas.feature.sort.HungarianDates
import hu.padlas.feature.sort.SwipeCard
import hu.padlas.feature.sort.session.label

/**
 * One photograph or video, shown whole: the picture is fitted, never cropped, over a
 * blurred copy of itself, because deciding about a photo you cannot fully see is guessing.
 */
@Composable
internal fun SwipeCardView(
    card: SwipeCard,
    isTop: Boolean,
    overlay: () -> SwipeOverlay,
    player: Player?,
    muted: Boolean,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(horizontal = Space.m, vertical = Space.s)
            .clip(Shapes.hero)
            .background(Ink.SurfaceHigh)
            .border(1.dp, Ink.Hairline, Shapes.hero),
    ) {
        AsyncImage(
            model = MediaThumb(card.uri, 256),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(56.dp)
                .graphicsLayer { alpha = 0.7f },
        )
        Box(Modifier.fillMaxSize().background(Ink.Background.copy(alpha = 0.5f)))

        AsyncImage(
            model = if (card.isVideo) MediaThumb(card.uri, 1024) else card.uri,
            contentDescription = card.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        if (card.isVideo && isTop && player != null) {
            VideoSurface(player = player, uri = card.uri, modifier = Modifier.fillMaxSize())
        }

        Tint(overlay)
        Badges(card, Modifier.align(Alignment.TopStart))
        if (card.isVideo && isTop) {
            PadlasIconButton(
                icon = if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                contentDescription = if (muted) "Hang be" else "Hang ki",
                onClick = onToggleMute,
                background = Ink.Background.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Space.m),
            )
        }
        Caption(card, Modifier.align(Alignment.BottomStart))

        Stamp("Törlés", Icons.Rounded.Close, Ink.Danger, -12f, { overlay().delete }, Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = Space.xl))
        Stamp("Megtartom", Icons.Rounded.Check, Ink.Success, 12f, { overlay().keep }, Modifier.align(Alignment.TopStart).padding(top = 64.dp, start = Space.xl))
        Stamp("Kedvenc", Icons.Rounded.Star, Ink.Favorite, 0f, { overlay().favorite }, Modifier.align(Alignment.Center))
    }
}

/** The edge of the card glows in the colour of the fate it is being dragged towards. */
@Composable
private fun BoxScope.Tint(overlay: () -> SwipeOverlay) {
    Box(
        Modifier
            .matchParentSize()
            .drawWithContent {
                val state = overlay()
                if (state.delete > 0f) {
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Ink.Danger.copy(alpha = 0.45f * state.delete))))
                }
                if (state.keep > 0f) {
                    drawRect(Brush.horizontalGradient(listOf(Ink.Success.copy(alpha = 0.45f * state.keep), Color.Transparent)))
                }
                if (state.favorite > 0f) {
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Ink.Favorite.copy(alpha = 0.4f * state.favorite))))
                }
            },
    )
}

@Composable
private fun Stamp(
    text: String,
    icon: ImageVector,
    color: Color,
    rotation: Float,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .graphicsLayer {
                val value = alpha()
                this.alpha = value
                rotationZ = rotation
                val grow = 0.8f + 0.2f * value
                scaleX = grow
                scaleY = grow
            }
            .clip(Shapes.pill)
            .background(Ink.Background.copy(alpha = 0.55f))
            .border(2.5.dp, color, Shapes.pill)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(Space.s))
        Text(text = text.uppercase(), color = color, style = PadlasTheme.type.title)
    }
}

@Composable
private fun Badges(card: SwipeCard, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(Space.m),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val translucent = Ink.Background.copy(alpha = 0.6f)
        if (card.isVideo) {
            Pill(
                text = card.durationMs?.let(Format::duration) ?: "Videó",
                icon = Icons.Rounded.Videocam,
                background = translucent,
            )
        }
        if (card.isFavorite) Pill(text = "Kedvenc", icon = Icons.Rounded.Favorite, color = Ink.Favorite, background = translucent)
        if (card.isProtected) Pill(text = "Védett", icon = Icons.Rounded.Lock, color = Ink.Info, background = translucent)
        card.previous?.let { choice ->
            Pill(
                text = choice.label(),
                icon = Icons.Rounded.History,
                color = when (choice) {
                    SwipeChoice.KEEP -> Ink.Success
                    SwipeChoice.DELETE -> Ink.Danger
                    SwipeChoice.FAVORITE -> Ink.Favorite
                },
                background = translucent,
            )
        }
    }
}

@Composable
private fun Caption(card: SwipeCard, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
            .padding(start = Space.xl, end = Space.xl, top = Space.xxl, bottom = Space.l),
    ) {
        Text(
            text = HungarianDates.day(card.dateTaken),
            style = PadlasTheme.type.bodyStrong,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        val details = buildList {
            if (card.folder.isNotBlank()) add(card.folder)
            add(Format.bytes(card.sizeBytes))
            if (card.width > 0 && card.height > 0) add("${card.width}×${card.height}")
        }
        Text(
            text = details.joinToString(" · "),
            style = PadlasTheme.type.caption,
            color = Color.White.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
