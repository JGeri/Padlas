package hu.padlas.core.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.R
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Spectrum
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI

private const val StartAngle = 135f
private const val TotalSweep = 270f

data class GaugeSegment(val label: String, val bytes: Long, val color: Color)

/**
 * The storage ring. Segments grow out of zero one after another on first arrival, and later
 * changes settle on a spring so freed space visibly flows back.
 */
@Composable
fun StorageGauge(
    segments: List<GaugeSegment>,
    freeBytes: Long,
    totalBytes: Long,
    modifier: Modifier = Modifier,
    diameter: Dp = 232.dp,
    thickness: Dp = 16.dp,
    compact: Boolean = false,
) {
    val source = rememberUpdatedState(segments)
    val capacity = rememberUpdatedState(totalBytes)
    val targets by remember { derivedStateOf { sweepDegrees(source.value, capacity.value) } }
    val sweeps = remember { mutableStateListOf<Animatable<Float, AnimationVector1D>>() }
    val reduced = LocalReducedMotion.current
    var grown by remember { mutableStateOf(false) }

    LaunchedEffect(targets) {
        while (sweeps.size < targets.size) sweeps.add(Animatable(0f))
        while (sweeps.size > targets.size) sweeps.removeAt(sweeps.lastIndex)
        coroutineScope {
            targets.forEachIndexed { index, target ->
                launch {
                    if (!grown) delay(staggerDelayFor(index, reduced).toLong() * 2)
                    if (reduced) sweeps[index].snapTo(target) else sweeps[index].animateTo(target, Motion.weight())
                }
            }
        }
        grown = true
    }

    val used = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedFraction = if (totalBytes > 0) used.toFloat() / totalBytes else 0f

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = thickness.toPx()
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val radius = (size.minDimension - stroke) / 2f
            val gapDegrees = (stroke * 0.9f) / radius * (180f / PI.toFloat())

            drawArc(
                color = Ink.SurfaceHigh,
                startAngle = StartAngle,
                sweepAngle = TotalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            var angle = StartAngle
            sweeps.forEachIndexed { index, arc ->
                val value = arc.value
                if (value > 0.5f) {
                    val color = segments.getOrNull(index)?.color ?: Spectrum[index % Spectrum.size]
                    val start = angle + gapDegrees / 2f
                    val sweep = (value - gapDegrees).coerceAtLeast(0.01f)
                    // A wide, faint pass first: the segment glows instead of being painted on.
                    drawArc(
                        color = color.copy(alpha = 0.22f),
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(stroke * 2.1f, cap = StrokeCap.Round),
                    )
                    drawArc(
                        brush = Brush.linearGradient(listOf(lerp(color, Color.White, 0.25f), color)),
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                angle += value
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (!compact) {
                Text(text = stringResource(R.string.design_gauge_free_title), style = PadlasTheme.type.caption, color = Ink.TextSecondary)
                Spacer(Modifier.height(2.dp))
            }
            if (totalBytes <= 0L) {
                Text(text = "…", style = if (compact) PadlasTheme.type.displaySmall else PadlasTheme.type.display, color = Ink.TextTertiary)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingNumber(
                        value = Format.bytesValue(freeBytes),
                        style = if (compact) PadlasTheme.type.displaySmall else PadlasTheme.type.display,
                        color = Ink.Text,
                    )
                    Text(
                        text = " " + Format.bytesUnit(freeBytes),
                        style = if (compact) PadlasTheme.type.caption else PadlasTheme.type.title,
                        color = Ink.TextSecondary,
                        modifier = Modifier.padding(bottom = if (compact) 5.dp else 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = when {
                    totalBytes <= 0L -> stringResource(R.string.design_gauge_measuring)
                    compact -> stringResource(R.string.design_gauge_free)
                    else -> stringResource(R.string.design_gauge_used, Format.count((usedFraction * 100).toInt()))
                },
                style = PadlasTheme.type.caption,
                color = Ink.TextTertiary,
            )
        }
    }
}

/** Clamped to the arc: a caller that over-reports must not wrap the gauge around itself. */
private fun sweepDegrees(segments: List<GaugeSegment>, totalBytes: Long): List<Float> {
    if (totalBytes <= 0L) return List(segments.size) { 0f }
    val raw = segments.map {
        (it.bytes.coerceAtLeast(0L).toDouble() / totalBytes * TotalSweep).toFloat()
    }
    val sum = raw.sum()
    return if (sum > TotalSweep) raw.map { it * TotalSweep / sum } else raw
}
