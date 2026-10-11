package hu.padlas.feature.transcode.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.entity.JobState
import hu.padlas.core.design.component.NumericText
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.RollingNumber
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.haptics
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space
import hu.padlas.feature.transcode.data.TranscodeMath
import hu.padlas.feature.transcode.data.VideoItem

private val MinCardHeight = 88.dp
private val MaxCardHeight = 232.dp

/**
 * Animation 5. The card's height *is* the file size, so a finished compression is not a
 * label change: the card physically thins to the new proportion, the old number gets
 * struck out left to right, and the new one rolls in underneath it.
 */
@Composable
internal fun VideoCard(
    item: VideoItem,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val done = item.state == JobState.SUCCEEDED && item.resultBytes > 0L
    val shrink = if (done) TranscodeMath.shrinkRatio(item.originalBytes, item.resultBytes) else 1f
    val reduced = LocalReducedMotion.current
    val haptics = haptics()

    val heightFraction by animateFloatAsState(
        targetValue = item.heightFraction * shrink,
        animationSpec = motionSpec(Motion.drain()),
        label = "cardHeight",
    )
    val strike = remember { Animatable(if (done) 1f else 0f) }
    val flash = remember { Animatable(0f) }

    LaunchedEffect(done) {
        if (!done) {
            strike.snapTo(0f)
            flash.snapTo(0f)
            return@LaunchedEffect
        }
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

    PadlasCard(
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight(heightFraction)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = item.displayName,
                style = PadlasTheme.type.body,
                color = Ink.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Space.s),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SizeColumn(item = item, done = done, strike = strike.value)
                DeltaLabel(item = item, done = done, flash = flash.value)
            }
            StatusLine(item = item, onRetry = onRetry)
        }
    }
}

@Composable
private fun SizeColumn(item: VideoItem, done: Boolean, strike: Float) {
    Column {
        Box(
            modifier = Modifier.drawWithContent {
                drawContent()
                if (strike <= 0f) return@drawWithContent
                val y = size.height / 2f
                drawLine(
                    color = Ink.TextTertiary,
                    start = Offset(0f, y),
                    end = Offset(size.width * strike, y),
                    strokeWidth = 1.5f,
                )
            },
        ) {
            NumericText(
                text = Format.bytes(item.originalBytes),
                color = if (done) Ink.TextTertiary else Ink.Text,
                large = !done,
            )
        }
        if (done) {
            RollingNumber(
                value = Format.bytes(item.resultBytes),
                style = PadlasTheme.type.numericLarge,
                color = Ink.Text,
            )
        } else {
            NumericText(text = Format.duration(item.durationMs), color = Ink.TextTertiary)
        }
    }
}

@Composable
private fun DeltaLabel(item: VideoItem, done: Boolean, flash: Float) {
    if (!done) return
    val color = lerp(Ink.TextSecondary, Ink.Accent, flash)
    NumericText(
        text = "-" + Format.bytes(TranscodeMath.savedBytes(item.originalBytes, item.resultBytes)),
        color = color,
        large = true,
    )
}

@Composable
private fun StatusLine(item: VideoItem, onRetry: () -> Unit) {
    when (item.state) {
        JobState.RUNNING -> Label("Tömörítés folyamatban.", Ink.TextSecondary)
        JobState.PENDING -> Label("Sorban áll.", Ink.TextTertiary)
        JobState.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
            Label(item.failureReason ?: "A tömörítés megszakadt.", Ink.Danger)
            PadlasTextButton(text = "Újra", onClick = onRetry)
        }
        JobState.SUCCEEDED -> Label(
            if (item.published) "Kész, az eredeti még helyet foglal." else "Kész, cserére vár.",
            Ink.Success,
        )
        JobState.CANCELLED -> Label("Megszakítva.", Ink.TextTertiary)
        null -> Label("Tömöríthető.", Ink.TextTertiary)
    }
}

@Composable
private fun Label(text: String, color: Color) {
    Text(text = text, style = PadlasTheme.type.label, color = color)
}

private fun cardHeight(fraction: Float): Dp =
    MinCardHeight + (MaxCardHeight - MinCardHeight) * fraction.coerceIn(0f, 1f)

private const val STRIKE_MS = 200
private const val FLASH_MS = 800
