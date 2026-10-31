package hu.padlas.feature.index

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.RollingNumber
import hu.padlas.core.design.component.StaggeredColumnItem
import hu.padlas.core.design.component.asString
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.finite
import hu.padlas.core.design.motion.haptics
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space
import hu.padlas.core.design.theme.auroraBackground
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun IndexScreen(
    state: IndexUiState,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .auroraBackground()
            .systemBarsPadding()
            .padding(horizontal = Space.page),
    ) {
        if (state.error != null) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                StaggeredColumnItem(index = 0) {
                    EmptyState(
                        title = stringResource(R.string.index_failed_title),
                        text = state.error.asString(),
                        icon = Icons.Rounded.ErrorOutline,
                        tint = Ink.Danger,
                        actionLabel = stringResource(R.string.index_retry),
                        onAction = onRetry,
                    )
                }
            }
            return@Box
        }

        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            IndexProgress(state = state)
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(
                visible = !state.finished,
                enter = fadeIn(),
                exit = fadeOut(finite(LocalReducedMotion.current)),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.index_leave_hint),
                        style = PadlasTheme.type.caption,
                        color = Ink.TextTertiary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Space.xs))
                    PadlasTextButton(text = stringResource(R.string.index_continue_in_background), onClick = onContinue)
                    Spacer(Modifier.height(Space.l))
                }
            }
        }
    }
}

/**
 * The count, always present. A spinner with no number tells the user nothing about a
 * pass that takes a minute and a half (spec 2.3).
 */
@Composable
private fun IndexProgress(state: IndexUiState, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val haptic = haptics()
    val pop = remember { Animatable(1f) }
    LaunchedEffect(state.finished) {
        if (!state.finished) return@LaunchedEffect
        haptic?.keep()
        if (!reduced) {
            pop.snapTo(0.94f)
            pop.animateTo(1f, Motion.bouncy())
        }
    }
    Column(
        modifier = modifier.fillMaxWidth().widthIn(max = 420.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StaggeredColumnItem(index = 0) {
            GlowRing(
                fraction = if (state.finished) 1f else state.fraction,
                finished = state.finished,
                measuring = !state.finished && state.total <= 0,
                modifier = Modifier.graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedVisibility(
                        visible = state.finished,
                        enter = scaleIn(if (reduced) snap() else Motion.bouncy(), initialScale = 0.4f) + fadeIn(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Ink.Success,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    RollingNumber(
                        value = Format.count(state.scanned),
                        style = PadlasTheme.type.display,
                        color = Ink.Text,
                    )
                    Text(
                        text = pluralStringResource(R.plurals.index_files_noun, state.scanned),
                        style = PadlasTheme.type.label,
                        color = Ink.TextSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.xl))
        StaggeredColumnItem(index = 1) {
            AnimatedContent(
                targetState = state.finished,
                transitionSpec = {
                    (slideInVertically(finite(reduced)) { it / 2 } + fadeIn(finite(reduced))) togetherWith
                        (slideOutVertically(finite(reduced)) { -it / 2 } + fadeOut(finite(reduced))) using
                        SizeTransform(clip = false)
                },
                contentAlignment = Alignment.Center,
                label = "indexTitle",
            ) { done ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(if (done) R.string.index_title_done else R.string.index_title_running),
                        style = PadlasTheme.type.headline,
                        color = Ink.Text,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Space.s))
                    Text(
                        text = stringResource(if (done) R.string.index_subtitle_done else R.string.index_subtitle_running),
                        style = PadlasTheme.type.body,
                        color = Ink.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.m))
        StaggeredColumnItem(index = 2) {
            AnimatedContent(
                targetState = phase(state),
                transitionSpec = {
                    (slideInVertically(finite(reduced)) { it } + fadeIn(finite(reduced))) togetherWith
                        (slideOutVertically(finite(reduced)) { -it } + fadeOut(finite(reduced)))
                },
                contentAlignment = Alignment.Center,
                label = "indexPhase",
            ) { line ->
                Text(
                    text = line,
                    style = PadlasTheme.type.label,
                    color = Ink.TextTertiary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(Space.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            Box(modifier = Modifier.weight(1f)) {
                StaggeredColumnItem(index = 3) {
                    RollingStat(label = stringResource(R.string.index_stat_scanned), value = Format.bytes(state.bytes))
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                StaggeredColumnItem(index = 4) {
                    RollingStat(
                        label = stringResource(if (state.finished) R.string.index_stat_duration else R.string.index_stat_progress),
                        value = when {
                            state.finished && state.durationMs > 0L -> Format.duration(state.durationMs)
                            state.finished -> Format.percent(1f)
                            else -> Format.percent(state.fraction)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RollingStat(label: String, value: String, modifier: Modifier = Modifier) {
    PadlasCard(modifier = modifier.fillMaxWidth(), contentPadding = Space.l) {
        Column {
            RollingNumber(value = value, style = PadlasTheme.type.numericLarge, color = Ink.Text)
            Spacer(Modifier.height(2.dp))
            Text(text = label, style = PadlasTheme.type.caption, color = Ink.TextSecondary, maxLines = 1)
        }
    }
}

/**
 * The first-run ring: violet running into cyan with a bright head on the arc, a halo that
 * breathes while the walk is going, and a turn to green when it is done. Every moving
 * part is read in the draw phase, so the ring never recomposes the screen around it.
 */
@Composable
private fun GlowRing(
    fraction: Float,
    finished: Boolean,
    measuring: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    stroke: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val swept by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = motionSpec(Motion.drain()),
        label = "ringSweep",
    )
    val done by animateFloatAsState(
        targetValue = if (finished) 1f else 0f,
        animationSpec = motionSpec(Motion.settle()),
        label = "ringDone",
    )
    val breath: State<Float>
    val spin: State<Float>
    if (reduced || finished) {
        breath = remember { mutableFloatStateOf(0.7f) }
        spin = remember { mutableFloatStateOf(0f) }
    } else {
        val clock = rememberInfiniteTransition(label = "ringClock")
        breath = clock.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(BreathMs, easing = LinearEasing), RepeatMode.Reverse),
            label = "ringBreath",
        )
        spin = clock.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(SpinMs, easing = LinearEasing), RepeatMode.Restart),
            label = "ringSpin",
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val halo = lerp(Ink.Accent, Ink.Success, done)
                val radius = this.size.minDimension * 0.68f
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to halo.copy(alpha = 0.30f * breath.value),
                        0.6f to halo.copy(alpha = 0.10f * breath.value),
                        1f to Color.Transparent,
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val width = stroke.toPx()
            val inset = width
            val arcSize = Size(this.size.width - inset * 2f, this.size.height - inset * 2f)
            val topLeft = Offset(inset, inset)
            val start = lerp(Ink.Accent, Ink.Success, done)
            val end = lerp(Ink.AccentCyan, Ink.Success, done)

            drawArc(Ink.SurfaceHigh, -90f, 360f, false, topLeft, arcSize, style = Stroke(width))
            if (measuring) {
                // No total yet: a short arc circles instead of a ring that claims a fraction.
                rotate(spin.value) {
                    drawArc(start, -90f, 70f, false, topLeft, arcSize, style = Stroke(width, cap = StrokeCap.Round))
                }
                return@Canvas
            }
            if (swept <= 0f) return@Canvas
            val sweep = 360f * swept
            // The colours span the filled arc, not the whole circle, so a quarter-full ring
            // already runs from violet into cyan; past the head it turns back towards violet
            // so the round cap at the start of the arc keeps the start colour.
            val head = swept.coerceIn(0.02f, 0.98f)
            val sweepBrush = Brush.sweepGradient(
                0f to start,
                head to end,
                (head + 1f) / 2f to end,
                1f to start,
                center = center,
            )
            rotate(-90f) {
                drawArc(
                    brush = sweepBrush,
                    startAngle = 0f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    alpha = 0.28f,
                    style = Stroke(width * 2.4f, cap = StrokeCap.Round),
                )
                drawArc(
                    brush = sweepBrush,
                    startAngle = 0f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width, cap = StrokeCap.Round),
                )
            }
            val radius = arcSize.width / 2f
            val angle = (sweep - 90f) * (PI.toFloat() / 180f)
            val tip = Offset(center.x + radius * cos(angle), center.y + radius * sin(angle))
            drawCircle(Color.White.copy(alpha = 0.22f * (1f - done)), radius = width * 1.2f, center = tip)
            drawCircle(Color.White.copy(alpha = 0.9f * (1f - done)), radius = width * 0.32f, center = tip)
        }
        content()
    }
}

@Composable
private fun phase(state: IndexUiState): String = when {
    state.finished -> pluralStringResource(
        R.plurals.index_phase_done,
        state.scanned,
        Format.count(state.scanned),
        Format.bytes(state.bytes),
    )
    state.bucket.isNotBlank() -> stringResource(R.string.index_phase_bucket, state.bucket)
    state.total > 0 -> pluralStringResource(R.plurals.index_phase_waiting, state.total, Format.count(state.total))
    else -> stringResource(R.string.index_phase_walking)
}

private const val BreathMs = 1_800
private const val SpinMs = 1_100
