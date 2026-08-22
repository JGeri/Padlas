package hu.padlas.core.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.haptics
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space
import hu.padlas.core.design.theme.Spectrum
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI

private const val StartAngle = 135f
private const val TotalSweep = 270f
private val GaugeDiameter = 280.dp
private val ArcThickness = 14.dp
private val SegmentGap = 2.dp

data class GaugeSegment(val label: String, val bytes: Long, val color: Color)

/** The span the drain just handed back, held only while the arc is moving. */
private data class Reclaimed(val start: Float, val sweep: Float)

/**
 * The one spectacular moment in the app: an instrument, not a progress bar. Freed space
 * flows back counter-clockwise instead of the arc simply redrawing itself, because the
 * whole point is that you watch the space come back.
 */
@Composable
fun StorageGauge(
    segments: List<GaugeSegment>,
    freeBytes: Long,
    totalBytes: Long,
    modifier: Modifier = Modifier,
) {
    val source = rememberUpdatedState(segments)
    val capacity = rememberUpdatedState(totalBytes)
    // Degrees are derived, so a parent recomposition with unchanged bytes never restarts an
    // animation that is halfway through.
    val targets by remember { derivedStateOf { sweepDegrees(source.value, capacity.value) } }

    val sweeps = remember { mutableStateListOf<Animatable<Float, AnimationVector1D>>() }
    val reduced = LocalReducedMotion.current
    val padlasHaptics = haptics()
    var reclaimed by remember { mutableStateOf<Reclaimed?>(null) }
    var grown by remember { mutableStateOf(false) }

    LaunchedEffect(targets) {
        while (sweeps.size < targets.size) sweeps.add(Animatable(0f))
        while (sweeps.size > targets.size) sweeps.removeAt(sweeps.lastIndex)
        val before = sweeps.fold(0f) { sum, arc -> sum + arc.value }
        val after = targets.sum()
        val draining = grown && after < before - 0.25f
        reclaimed = if (draining) Reclaimed(StartAngle + after, before - after) else null

        val midpointTick = if (draining) {
            launch {
                // The tick belongs to the middle of the movement, not to its start.
                snapshotFlow { sweeps.fold(0f) { sum, arc -> sum + arc.value } }
                    .first { it <= (before + after) / 2f }
                padlasHaptics?.tick(0.35f)
            }
        } else {
            null
        }

        val spec: AnimationSpec<Float> = when {
            reduced -> snap()
            draining -> Motion.drain()
            else -> Motion.settle()
        }
        coroutineScope {
            targets.forEachIndexed { index, target ->
                launch {
                    // First arrival only: the segments grow out of zero one after another.
                    if (!grown) delay(staggerDelayFor(index, reduced).toLong())
                    sweeps[index].animateTo(target, spec)
                }
            }
        }
        midpointTick?.cancel()
        grown = true
        reclaimed = null
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(GaugeDiameter), contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val thickness = ArcThickness.toPx()
                        val topLeft = Offset(thickness / 2f, thickness / 2f)
                        val arcSize = Size(size.width - thickness, size.height - thickness)
                        val track = Stroke(width = thickness, cap = StrokeCap.Butt)
                        onDrawBehind {
                            drawArc(
                                color = Ink.Raised,
                                startAngle = StartAngle,
                                sweepAngle = TotalSweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = track,
                            )
                        }
                    },
            ) {
                val thickness = ArcThickness.toPx()
                val topLeft = Offset(thickness / 2f, thickness / 2f)
                val arcSize = Size(size.width - thickness, size.height - thickness)
                val radius = (size.minDimension - thickness) / 2f
                val gap = SegmentGap.toPx() / radius * (180f / PI.toFloat())
                val stroke = Stroke(width = thickness, cap = StrokeCap.Butt)

                var angle = StartAngle
                sweeps.forEachIndexed { index, arc ->
                    val value = arc.value
                    if (value > 0.05f) {
                        drawArc(
                            color = segments.getOrNull(index)?.color
                                ?: Spectrum[index % Spectrum.size],
                            startAngle = angle,
                            sweepAngle = (value - gap).coerceAtLeast(0f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke,
                        )
                    }
                    angle += value
                }
                reclaimed?.let { span ->
                    drawArc(
                        color = Ink.Brass.copy(alpha = 0.20f),
                        startAngle = span.start,
                        sweepAngle = span.sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = thickness * 1.7f, cap = StrokeCap.Butt),
                    )
                    drawArc(
                        color = Ink.Brass,
                        startAngle = span.start,
                        sweepAngle = span.sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Butt),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RollingNumber(
                    value = bytesValue(freeBytes),
                    style = PadlasTheme.type.display,
                    color = Ink.Paper,
                )
                SectionLabel(bytesUnit(freeBytes))
            }
        }
        if (segments.isNotEmpty()) {
            Spacer(Modifier.height(Space.m))
            GaugeLegend(segments)
        }
    }
}

@Composable
private fun GaugeLegend(segments: List<GaugeSegment>) {
    FlowRow(
        modifier = Modifier.padding(horizontal = Space.l),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.Center,
    ) {
        segments.forEach { segment ->
            Row(
                modifier = Modifier.padding(end = Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(width = 10.dp, height = 3.dp)) { drawRect(segment.color) }
                Spacer(Modifier.width(Space.xs))
                SectionLabel(segment.label)
            }
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
