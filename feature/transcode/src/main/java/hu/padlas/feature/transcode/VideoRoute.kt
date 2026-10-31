package hu.padlas.feature.transcode

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.entity.JobState
import hu.padlas.core.design.component.BarFigures
import hu.padlas.core.design.component.ButtonSize
import hu.padlas.core.design.component.ButtonStyle
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.GlassScreen
import hu.padlas.core.design.component.IconBubble
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.PadlasTopBar
import hu.padlas.core.design.component.PinnedActionBar
import hu.padlas.core.design.component.PinnedBarClearance
import hu.padlas.core.design.component.ProgressRing
import hu.padlas.core.design.component.ProgressStrip
import hu.padlas.core.design.component.ReclaimHero
import hu.padlas.core.design.component.SectionHeader
import hu.padlas.core.design.component.navigationBarBottom
import hu.padlas.core.design.component.topBarClearance
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.entrance
import hu.padlas.core.design.motion.finite
import hu.padlas.core.design.motion.itemMotion
import hu.padlas.core.design.motion.rememberEntrance
import hu.padlas.core.design.theme.CategoryColor
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space
import hu.padlas.feature.transcode.data.TranscodeMath
import hu.padlas.feature.transcode.data.VideoItem
import hu.padlas.feature.transcode.ui.VideoCard

/**
 * The largest videos first. Compression is a night job on the charger; the screen says
 * which state it is in and what each video will save, and nothing moves until a video
 * actually finishes.
 */
@Composable
fun VideoRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: VideoViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val removalRequest by viewModel.removalRequest.collectAsStateWithLifecycle()

    // MediaStore is the only source of truth for what the system dialog actually did.
    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { viewModel.refreshPendingRemovals() }

    LaunchedEffect(removalRequest) {
        val request = removalRequest ?: return@LaunchedEffect
        viewModel.removalRequestConsumed()
        runCatching { trashLauncher.launch(IntentSenderRequest.Builder(request).build()) }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPendingRemovals()
        onPauseOrDispose { }
    }

    VideoScreen(
        state = state,
        onBack = onBack,
        onStart = viewModel::startBatch,
        onStop = viewModel::stopBatch,
        onReplace = viewModel::replaceOriginals,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
private fun VideoScreen(
    state: VideoUiState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReplace: () -> Unit,
    onRetry: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val waiting = state.videos.filter { it.state == null || it.state == JobState.PENDING }
    val open = state.videos.filter { it.state != JobState.SUCCEEDED && it.state != JobState.FAILED }
    val estimate = open.sumOf { TranscodeMath.estimatedSavingBytes(it.originalBytes) }
    val waitingEstimate = waiting.sumOf { TranscodeMath.estimatedSavingBytes(it.originalBytes) }
    val finished = state.videos.count { it.state == JobState.SUCCEEDED && !it.published }
    val showBar = state.running || waiting.isNotEmpty()
    val navBottom = navigationBarBottom()
    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    val entrance = rememberEntrance()
    val reduced = LocalReducedMotion.current

    GlassScreen(
        scrolled = scrolled,
        modifier = modifier,
        topBar = {
            PadlasTopBar(
                title = stringResource(R.string.transcode_title),
                onBack = onBack,
                subtitle = when {
                    state.throttled -> stringResource(R.string.transcode_subtitle_hot)
                    state.running -> stringResource(R.string.transcode_in_progress)
                    state.videos.isEmpty() -> null
                    else -> pluralStringResource(R.plurals.transcode_videos, state.videos.size, Format.count(state.videos.size)) +
                        " · " + Format.bytes(state.videos.sumOf { it.originalBytes })
                },
            )
        },
        overlay = {
            PinnedActionBar(visible = showBar, modifier = Modifier.align(Alignment.BottomCenter)) {
                AnimatedContent(
                    targetState = state.running,
                    transitionSpec = {
                        (fadeIn(finite(reduced)) togetherWith fadeOut(finite(reduced)))
                            .using(SizeTransform(clip = false))
                    },
                    label = "videoBar",
                ) { running ->
                    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        if (running) {
                            PadlasButton(
                                text = stringResource(R.string.transcode_stop),
                                onClick = onStop,
                                enabled = !state.busy,
                                icon = Icons.Rounded.Stop,
                                style = ButtonStyle.Secondary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            BarFigures(
                                count = Format.count(waiting.size),
                                noun = pluralStringResource(R.plurals.transcode_bar_noun, waiting.size),
                                value = Format.bytesValue(waitingEstimate),
                                unit = Format.bytesUnit(waitingEstimate),
                                valuePrefix = stringResource(R.string.transcode_approx),
                            )
                            PadlasButton(
                                text = stringResource(R.string.transcode_start),
                                onClick = onStart,
                                enabled = !state.busy,
                                icon = Icons.Rounded.Compress,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
    ) {
        if (state.videos.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.transcode_empty_title),
                text = stringResource(R.string.transcode_empty_text, Format.bytes(TranscodeMath.MIN_CANDIDATE_BYTES)),
                icon = Icons.Rounded.VideoLibrary,
                tint = CategoryColor.Videos,
                modifier = Modifier.padding(top = topBarClearance() + Space.xxl),
            )
            return@GlassScreen
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.page,
                end = Space.page,
                top = topBarClearance() + Space.s,
                bottom = navBottom + if (showBar) PinnedBarClearance else Space.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            item(key = "hero") {
                ReclaimHero(
                    label = stringResource(R.string.transcode_hero_label),
                    value = Format.bytesValue(estimate),
                    unit = Format.bytesUnit(estimate),
                    caption = stringResource(R.string.transcode_hero_caption),
                    glow = CategoryColor.Videos,
                    modifier = Modifier.entrance(entrance),
                ) {
                    AnimatedVisibility(
                        visible = state.saved.totalBytes > 0,
                        enter = expandVertically(finite(reduced)) + fadeIn(finite(reduced)),
                        exit = shrinkVertically(finite(reduced)) + fadeOut(finite(reduced)),
                    ) {
                        Column {
                            Spacer(Modifier.height(Space.l))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = Ink.Success,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(Space.s))
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.transcode_saved_so_far,
                                        state.saved.itemCount,
                                        Format.bytes(state.saved.totalBytes),
                                        Format.count(state.saved.itemCount),
                                    ),
                                    style = PadlasTheme.type.label,
                                    color = Ink.Text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            statusFor(state)?.let { status ->
                item(key = "status") {
                    AnimatedContent(
                        targetState = status,
                        transitionSpec = {
                            (fadeIn(finite(reduced)) togetherWith fadeOut(finite(reduced)))
                                .using(SizeTransform(clip = false))
                        },
                        label = "runPhase",
                        modifier = Modifier.entrance(entrance).then(itemMotion()),
                    ) { phase ->
                        StatusCard(status = phase, state = state)
                    }
                }
            }

            if (finished > 0 || state.pendingRemovalIds.isNotEmpty()) {
                item(key = "replace") {
                    ReplaceCard(
                        finished = finished,
                        pendingRemovalBytes = state.pendingRemovalBytes,
                        busy = state.busy,
                        onReplace = onReplace,
                        modifier = Modifier.entrance(entrance).then(itemMotion()),
                    )
                }
            }

            item(key = "list-head") {
                SectionHeader(
                    title = stringResource(R.string.transcode_list_title),
                    modifier = Modifier.entrance(entrance).then(itemMotion()),
                )
            }

            items(state.videos, key = { it.assetId }) { item ->
                VideoCard(
                    item = item,
                    progress = progressOf(item, state),
                    onRetry = { onRetry(item.assetId) },
                    modifier = Modifier.entrance(entrance).then(itemMotion()),
                )
            }
        }
    }
}

private enum class RunPhase { HOT, WORKING, WAITING_FOR_CHARGER, QUEUED }

private fun statusFor(state: VideoUiState): RunPhase? = when {
    state.throttled -> RunPhase.HOT
    state.running && (state.run.label.isNotBlank() || state.run.fraction > 0f) -> RunPhase.WORKING
    state.running -> RunPhase.WAITING_FOR_CHARGER
    state.pendingJobs > 0 -> RunPhase.QUEUED
    else -> null
}

@Composable
private fun StatusCard(status: RunPhase, state: VideoUiState) {
    when (status) {
        RunPhase.HOT -> StatusRow(
            icon = Icons.Rounded.Thermostat,
            tint = Ink.Warning,
            title = stringResource(R.string.transcode_hot_title),
            text = stringResource(R.string.transcode_hot_text),
        )

        RunPhase.WORKING -> PadlasCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(fraction = state.run.fraction, size = 48.dp, stroke = 4.dp) {
                        Icon(
                            imageVector = Icons.Rounded.Compress,
                            contentDescription = null,
                            tint = Ink.Accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(Space.m))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.transcode_in_progress),
                            style = PadlasTheme.type.bodyStrong,
                            color = Ink.Text,
                            maxLines = 1,
                        )
                        Text(
                            text = if (state.pendingJobs > 0) {
                                pluralStringResource(R.plurals.transcode_remaining, state.pendingJobs, Format.count(state.pendingJobs))
                            } else {
                                stringResource(R.string.transcode_last)
                            },
                            style = PadlasTheme.type.caption,
                            color = Ink.TextSecondary,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(Space.l))
                ProgressStrip(
                    fraction = state.run.fraction,
                    label = state.run.label.ifBlank { stringResource(R.string.transcode_starting) } + " · " +
                        Format.percent(state.run.fraction),
                )
            }
        }

        RunPhase.WAITING_FOR_CHARGER -> StatusRow(
            icon = Icons.Rounded.BatteryChargingFull,
            tint = Ink.Info,
            title = stringResource(R.string.transcode_charger_title),
            text = stringResource(R.string.transcode_charger_text),
        )

        RunPhase.QUEUED -> StatusRow(
            icon = Icons.Rounded.HourglassTop,
            tint = Ink.TextSecondary,
            title = pluralStringResource(R.plurals.transcode_queued_title, state.pendingJobs, Format.count(state.pendingJobs)),
            text = stringResource(R.string.transcode_queued_text),
        )
    }
}

@Composable
private fun StatusRow(icon: ImageVector, tint: Color, title: String, text: String) {
    PadlasCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            IconBubble(icon = icon, tint = tint)
            Spacer(Modifier.width(Space.m))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = PadlasTheme.type.bodyStrong, color = Ink.Text)
                Spacer(Modifier.height(Space.xxs))
                Text(text = text, style = PadlasTheme.type.caption, color = Ink.TextSecondary)
            }
        }
    }
}

/**
 * Publishing puts the compressed copies into the gallery; the originals then leave through
 * one system dialog. Until that dialog is accepted the space is still taken, and the card
 * says so instead of pretending the job is done.
 */
@Composable
private fun ReplaceCard(
    finished: Int,
    pendingRemovalBytes: Long,
    busy: Boolean,
    onReplace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = finished > 0
    PadlasCard(modifier = modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                IconBubble(
                    icon = if (ready) Icons.Rounded.TaskAlt else Icons.Rounded.Warning,
                    tint = if (ready) Ink.Success else Ink.Warning,
                )
                Spacer(Modifier.width(Space.m))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (ready) {
                            pluralStringResource(R.plurals.transcode_finished, finished, Format.count(finished))
                        } else {
                            stringResource(R.string.transcode_originals_waiting)
                        },
                        style = PadlasTheme.type.bodyStrong,
                        color = Ink.Text,
                    )
                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        text = if (ready) {
                            stringResource(R.string.transcode_replace_text)
                        } else {
                            stringResource(R.string.transcode_pending_removal_text, Format.bytes(pendingRemovalBytes))
                        },
                        style = PadlasTheme.type.caption,
                        color = Ink.TextSecondary,
                    )
                }
            }
            Spacer(Modifier.height(Space.l))
            PadlasButton(
                text = stringResource(if (ready) R.string.transcode_replace else R.string.transcode_trash_originals),
                onClick = onReplace,
                enabled = !busy,
                style = if (ready) ButtonStyle.Success else ButtonStyle.Secondary,
                size = ButtonSize.Medium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun progressOf(item: VideoItem, state: VideoUiState): Float? =
    if (item.state == JobState.RUNNING && state.run.label == item.displayName) state.run.fraction else null
