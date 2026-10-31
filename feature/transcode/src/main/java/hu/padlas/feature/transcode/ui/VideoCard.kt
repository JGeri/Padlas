package hu.padlas.feature.transcode.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.entity.JobState
import hu.padlas.core.design.component.LinearMeter
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.RollingNumber
import hu.padlas.core.design.component.asString
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.haptics
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.CategoryColor
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.ProgressGradient
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space
import hu.padlas.feature.transcode.R
import hu.padlas.feature.transcode.data.TranscodeMath
import hu.padlas.feature.transcode.data.VideoItem

private val THUMB = 72.dp

/**
 * Animation 5, tamed. The bar under each video is its size against the largest one; a
 * finished compression drains it to the new proportion, the old size is struck out left
 * to right and the saving flashes in green. One card changes, nothing else moves.
 */
@Composable
internal fun VideoCard(
    item: VideoItem,
    progress: Float?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val done = item.state == JobState.SUCCEEDED && item.resultBytes > 0L
    val shrink = if (done) TranscodeMath.shrinkRatio(item.originalBytes, item.resultBytes) else 1f
    val reduced = LocalReducedMotion.current
    val haptics = haptics()

    val barFraction by animateFloatAsState(
        targetValue = item.heightFraction * shrink,
        animationSpec = motionSpec(Motion.drain()),
        label = "sizeBar",
    )
    val strike = remember { Animatable(if (done) 1f else 0f) }
    val flash = remember { Animatable(0f) }

    LaunchedEffect(done) {
        if (!done) {
            strike.snapTo(0f)
            flash.snapTo(0f)
            return@LaunchedEffect
        }
        if (strike.value >= 1f) return@LaunchedEffect
        haptics?.tick(0.35f)
        // Spec 6.6 (5) fixes these two durations; a spring would make the strike arrive
        // at an unpredictable moment and the fade would never settle.
        strike.animateTo(1f, tween(STRIKE_MS, easing = LinearEasing))
        flash.snapTo(1f)
        flash.animateTo(0f, tween(FLASH_MS, easing = LinearEasing))
    }
    LaunchedEffect(reduced, done) {
        if (reduced && done) {
            strike.snapTo(1f)
            flash.snapTo(0f)
        }
    }

    PadlasCard(modifier = modifier.fillMaxWidth(), contentPadding = Space.m) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumbnail(item)
                Spacer(Modifier.width(Space.m))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayName,
                        style = PadlasTheme.type.bodyStrong,
                        color = Ink.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(Space.xxs))
                    SizeLine(item = item, done = done, strike = strike.value)
                    Spacer(Modifier.height(Space.xs))
                    StatusLine(item = item, progress = progress)
                }
                Spacer(Modifier.width(Space.s))
                Saving(item = item, done = done, flash = flash.value)
            }
            Spacer(Modifier.height(Space.m))
            LinearMeter(
                fraction = if (item.state == JobState.RUNNING && progress != null) progress else barFraction,
                color = when {
                    done -> Ink.Success
                    item.state == JobState.RUNNING -> Ink.Accent
                    else -> CategoryColor.Videos.copy(alpha = 0.8f)
                },
                height = 4.dp,
                brush = if (item.state == JobState.RUNNING) ProgressGradient else null,
            )
            if (item.state == JobState.FAILED) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.failureReason?.asString() ?: stringResource(R.string.transcode_failed_default),
                        style = PadlasTheme.type.caption,
                        color = Ink.Danger,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    PadlasTextButton(
                        text = stringResource(R.string.transcode_retry),
                        onClick = onRetry,
                        icon = Icons.Rounded.Refresh,
                        modifier = Modifier.heightIn(min = 44.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(item: VideoItem) {
    Box(
        modifier = Modifier
            .size(THUMB)
            .clip(Shapes.tile)
            .background(Ink.SurfaceHigh),
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(THUMB),
        )
        if (item.durationMs > 0L) {
            Text(
                text = Format.duration(item.durationMs),
                style = PadlasTheme.type.micro,
                color = Ink.Text,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(Shapes.pill)
                    .background(Ink.Background.copy(alpha = 0.7f))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun SizeLine(item: VideoItem, done: Boolean, strike: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = Format.bytes(item.originalBytes),
            style = PadlasTheme.type.numeric,
            color = if (done) Ink.TextTertiary else Ink.TextSecondary,
            maxLines = 1,
            modifier = Modifier.drawWithContent {
                drawContent()
                if (strike <= 0f) return@drawWithContent
                val y = size.height / 2f
                drawLine(
                    color = Ink.TextTertiary,
                    start = Offset(0f, y),
                    end = Offset(size.width * strike, y),
                    strokeWidth = 1.5.dp.toPx(),
                )
            },
        )
        if (done) {
            Text(
                text = "  →  " + Format.bytes(item.resultBytes),
                style = PadlasTheme.type.numeric,
                color = Ink.Text,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Saving(item: VideoItem, done: Boolean, flash: Float) {
    val saved = if (done) {
        TranscodeMath.savedBytes(item.originalBytes, item.resultBytes)
    } else {
        TranscodeMath.estimatedSavingBytes(item.originalBytes)
    }
    val color = if (done) lerp(Ink.Success, Color.White, flash * 0.6f) else Ink.TextSecondary
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        RollingNumber(
            value = "−" + Format.bytes(saved),
            style = PadlasTheme.type.numeric,
            color = color,
        )
        Text(
            text = stringResource(if (done) R.string.transcode_saved else R.string.transcode_expected),
            style = PadlasTheme.type.micro,
            color = Ink.TextTertiary,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusLine(item: VideoItem, progress: Float?) {
    val (text, color) = when (item.state) {
        JobState.RUNNING -> (
            if (progress != null && progress > 0f) {
                stringResource(R.string.transcode_status_running_percent, Format.percent(progress))
            } else {
                stringResource(R.string.transcode_status_running)
            }
            ) to Ink.Accent
        JobState.PENDING -> stringResource(R.string.transcode_status_pending) to Ink.TextTertiary
        JobState.FAILED -> stringResource(R.string.transcode_status_failed) to Ink.Danger
        JobState.SUCCEEDED -> if (item.published) {
            stringResource(R.string.transcode_status_published) to Ink.Warning
        } else {
            stringResource(R.string.transcode_status_done) to Ink.Success
        }
        JobState.CANCELLED -> stringResource(R.string.transcode_status_cancelled) to Ink.TextTertiary
        null -> stringResource(R.string.transcode_status_candidate) to Ink.TextTertiary
    }
    Text(text = text, style = PadlasTheme.type.caption, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private const val STRIKE_MS = 200
private const val FLASH_MS = 800
