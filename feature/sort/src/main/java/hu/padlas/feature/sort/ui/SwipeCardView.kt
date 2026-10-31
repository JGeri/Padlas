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
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.transformations
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.entity.SwipeChoice
import hu.padlas.core.design.component.PadlasIconButton
import hu.padlas.core.design.component.Pill
import hu.padlas.core.design.component.glass
import hu.padlas.core.design.motion.morphElement
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Sora
import hu.padlas.core.design.theme.Space
import hu.padlas.core.media.MediaThumb
import hu.padlas.feature.sort.R
import hu.padlas.feature.sort.SwipeCard
import hu.padlas.feature.sort.SwipeDates
import hu.padlas.feature.sort.session.label
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

/** Roughly the deck's own shape; a photo this close to it fills the card edge to edge. */
private const val CardAspect = 0.66f

/**
 * One photograph or video. Pictures shaped like the card fill it; anything much wider or
 * taller is shown whole over a blurred copy of itself, because deciding about a photo you
 * cannot fully see is guessing. While it is dragged, the card glows in the colour of the
 * fate it is heading for.
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
    val fills = fillsCard(card)
    val context = LocalPlatformContext.current
    Box(modifier = modifier.padding(horizontal = Space.m, vertical = 6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val state = overlay()
                    val strongest = max(state.delete, max(state.keep, state.favorite))
                    val glow = when {
                        strongest <= 0.02f -> Color.Black
                        strongest == state.delete -> Ink.Danger
                        strongest == state.keep -> Ink.Success
                        else -> Ink.Favorite
                    }
                    shadowElevation = (if (isTop) 18f + 34f * strongest else 10f).dp.toPx()
                    ambientShadowColor = glow
                    spotShadowColor = glow
                    shape = Shapes.hero
                    clip = true
                }
                .background(Ink.SurfaceSolid)
                .border(1.dp, Ink.HairlineStrong, Shapes.hero),
        ) {
            val picture = remember(card.uri) {
                ImageRequest.Builder(context)
                    .data(if (card.isVideo) MediaThumb(card.uri, 1024) else card.uri)
                    .placeholderMemoryCacheKey(Cover.cacheKey(card.uri))
                    .build()
            }
            val pictureModifier = Modifier
                .fillMaxSize()
                .let { if (isTop) it.morphElement(Cover.morphKey(card.uri)) else it }
            if (fills) {
                AsyncImage(
                    model = picture,
                    contentDescription = card.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = pictureModifier,
                )
            } else {
                AsyncImage(
                    model = remember(card.uri) {
                        ImageRequest.Builder(context).data(MediaThumb(card.uri, 128)).transformations(SoftBlur).build()
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.7f,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Ink.Background.copy(alpha = 0.45f)))
                AsyncImage(
                    model = picture,
                    contentDescription = card.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = pictureModifier,
                )
            }
            if (card.isVideo && isTop && player != null) {
                VideoSurface(player = player, uri = card.uri, modifier = Modifier.fillMaxSize())
            }

            Tint(overlay)
            Badges(card, Modifier.align(Alignment.TopStart))
            if (card.isVideo && isTop) {
                PadlasIconButton(
                    icon = if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                    contentDescription = stringResource(if (muted) R.string.sort_unmute else R.string.sort_mute),
                    onClick = onToggleMute,
                    background = Ink.SurfaceSolid.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Space.m),
                )
            }
            Caption(card, Modifier.align(Alignment.BottomCenter))

            Stamp(stringResource(R.string.sort_stamp_keep), Ink.Success, -12f, { overlay().keep }, Modifier.align(Alignment.TopStart).padding(top = 72.dp, start = 22.dp))
            Stamp(stringResource(R.string.sort_stamp_delete), Ink.Danger, 12f, { overlay().delete }, Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 22.dp))
            FavoriteBurst({ overlay().favorite }, Modifier.align(Alignment.Center))
        }
    }
}

private fun fillsCard(card: SwipeCard): Boolean {
    if (card.width <= 0 || card.height <= 0) return true
    val aspect = card.width.toFloat() / card.height
    return abs(ln(aspect / CardAspect)) < 0.42f
}

/** The edge the card is dragged towards lights up in that fate's colour. */
@Composable
private fun BoxScope.Tint(overlay: () -> SwipeOverlay) {
    Box(
        Modifier
            .matchParentSize()
            .drawWithContent {
                val state = overlay()
                if (state.delete > 0f) {
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Ink.Danger.copy(alpha = 0.5f * state.delete))))
                }
                if (state.keep > 0f) {
                    drawRect(Brush.horizontalGradient(listOf(Ink.Success.copy(alpha = 0.5f * state.keep), Color.Transparent)))
                }
                if (state.favorite > 0f) {
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Ink.Favorite.copy(alpha = 0.45f * state.favorite))))
                }
            },
    )
}

@Composable
private fun Stamp(text: String, color: Color, rotation: Float, alpha: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .graphicsLayer {
                val value = alpha()
                this.alpha = value
                rotationZ = rotation
                val grow = 0.7f + 0.3f * value
                scaleX = grow
                scaleY = grow
            }
            .clip(Shapes.small)
            .background(Ink.Background.copy(alpha = 0.4f))
            .border(3.dp, color, Shapes.small)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = color,
            style = PadlasTheme.type.title.copy(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 0.06.em),
        )
    }
}

@Composable
private fun FavoriteBurst(alpha: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(104.dp)
            .graphicsLayer {
                val value = alpha()
                this.alpha = value
                val grow = 0.5f + 0.5f * value
                scaleX = grow
                scaleY = grow
                rotationZ = -18f * (1f - value)
            }
            .clip(Shapes.pill)
            .background(Ink.Favorite.copy(alpha = 0.22f))
            .border(2.dp, Ink.Favorite, Shapes.pill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Star, contentDescription = null, tint = Ink.Favorite, modifier = Modifier.size(52.dp))
    }
}

@Composable
private fun Badges(card: SwipeCard, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(Space.m),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val translucent = Ink.SurfaceSolid.copy(alpha = 0.6f)
        if (card.isVideo) {
            Pill(
                text = card.durationMs?.let(Format::duration) ?: stringResource(R.string.sort_video),
                icon = Icons.Rounded.Videocam,
                background = translucent,
            )
        }
        if (card.isFavorite) {
            Pill(text = stringResource(R.string.sort_favorite), icon = Icons.Rounded.Favorite, color = Ink.Favorite, background = translucent)
        }
        if (card.isProtected) {
            Pill(text = stringResource(R.string.sort_protected), icon = Icons.Rounded.Lock, color = Ink.Info, background = translucent)
        }
        card.previous?.let { choice ->
            Pill(
                text = stringResource(choice.label()),
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

/**
 * A pane of glass at the foot of the card: when and where, never more than two lines. Dark
 * enough to stay readable over a white screenshot, which is half of what people swipe.
 */
@Composable
private fun Caption(card: SwipeCard, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .glass(Shapes.card, Ink.SurfaceSolid.copy(alpha = 0.82f), Ink.HairlineStrong)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = SwipeDates.day(card.dateTaken),
            style = PadlasTheme.type.bodyStrong,
            color = Ink.Text,
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
            color = Ink.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
