package hu.padlas.feature.archive.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.design.component.BarFigures
import hu.padlas.core.design.component.ButtonSize
import hu.padlas.core.design.component.ButtonStyle
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.GhostTile
import hu.padlas.core.design.component.GlassScreen
import hu.padlas.core.design.component.IconBubble
import hu.padlas.core.design.component.ListRow
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.PadlasTopBar
import hu.padlas.core.design.component.PinnedActionBar
import hu.padlas.core.design.component.PinnedBarClearance
import hu.padlas.core.design.component.ProgressRing
import hu.padlas.core.design.component.ProgressStrip
import hu.padlas.core.design.component.ReclaimHero
import hu.padlas.core.design.component.SectionHeader
import hu.padlas.core.design.component.SelectableTile
import hu.padlas.core.design.component.Toast
import hu.padlas.core.design.component.ZoomHost
import hu.padlas.core.design.component.ZoomTarget
import hu.padlas.core.design.component.ZoomableImage
import hu.padlas.core.design.component.asString
import hu.padlas.core.design.component.navigationBarBottom
import hu.padlas.core.design.component.rememberSystemRequest
import hu.padlas.core.design.component.rememberZoomState
import hu.padlas.core.design.component.topBarClearance
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.entrance
import hu.padlas.core.design.motion.finite
import hu.padlas.core.design.motion.haptics
import hu.padlas.core.design.motion.itemMotion
import hu.padlas.core.design.motion.popIn
import hu.padlas.core.design.motion.rememberEntrance
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space
import hu.padlas.feature.archive.ArchiveScheduler
import hu.padlas.feature.archive.R
import hu.padlas.feature.archive.drive.AuthState
import java.io.File

/**
 * The cloud archive. A file that only lives on Drive keeps its place in the grid as a faded
 * tile with a cloud mark, so the grid never re-flows when local copies are freed.
 */
@Composable
fun AtticRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: AtticViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onConsentResult(result.data) }

    val launchDelete = rememberSystemRequest(viewModel::onGhostResult)

    LaunchedEffect(state.ghostRequest) {
        val request = state.ghostRequest ?: return@LaunchedEffect
        launchDelete(request.intentSender)
    }

    val authState = state.authState
    LaunchedEffect(authState) {
        if (authState is AuthState.ConsentNeeded) {
            runCatching { consentLauncher.launch(IntentSenderRequest.Builder(authState.intent.intentSender).build()) }
        }
    }

    val selectedAssets = state.assets.filter { it.mediaStoreId in state.selected }
    val selectedBytes = selectedAssets.filter { it.state == AssetState.ARCHIVED }.sumOf { it.sizeBytes }
    val selectedPending = selectedAssets.any { it.state != AssetState.ARCHIVED }
    val selecting = state.selected.isNotEmpty()
    val freeable = state.assets.count { it.state == AssetState.ARCHIVED }
    val showBar = !state.loading && (selecting || state.reclaimable > 0)
    val navBottom = navigationBarBottom()
    val gridState = rememberLazyGridState()
    val scrolled by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0 }
    }
    val zoom = rememberZoomState()
    val entrance = rememberEntrance()
    val reduced = LocalReducedMotion.current
    val haptic = haptics()

    ZoomHost(
        state = zoom,
        picture = { target, placeholder, pictureModifier -> ZoomPicture(target, placeholder, pictureModifier) },
        modifier = modifier.fillMaxSize(),
        actions = { target ->
            val id = target.key as? Long
            val asset = id?.let { key -> state.assets.firstOrNull { it.mediaStoreId == key } }
            if (asset != null && asset.state != AssetState.GHOST) {
                val picked = asset.mediaStoreId in state.selected
                PadlasButton(
                    text = stringResource(if (picked) R.string.archive_deselect else R.string.archive_select),
                    onClick = {
                        haptic?.tick()
                        viewModel.toggle(asset.mediaStoreId)
                    },
                    icon = if (picked) Icons.Rounded.RadioButtonUnchecked else Icons.Rounded.CheckCircle,
                    style = ButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        GlassScreen(
            scrolled = scrolled,
            topBar = {
                PadlasTopBar(
                    title = stringResource(R.string.archive_title),
                    onBack = onBack,
                    subtitle = when {
                        selecting -> stringResource(R.string.archive_subtitle_selected, Format.count(state.selected.size))
                        state.assets.isEmpty() -> stringResource(R.string.archive_subtitle_drive)
                        else -> pluralStringResource(
                            R.plurals.archive_subtitle_files,
                            state.assets.size,
                            Format.count(state.assets.size),
                        )
                    },
                    action = {
                        AnimatedContent(
                            targetState = selecting,
                            transitionSpec = { popIn(reduced) togetherWith fadeOut(finite(reduced)) },
                            label = "atticCancel",
                        ) { active ->
                            if (active) {
                                PadlasTextButton(text = stringResource(R.string.archive_cancel), onClick = viewModel::clearSelection)
                            } else {
                                Spacer(Modifier.size(0.dp))
                            }
                        }
                    },
                )
            },
            overlay = {
                PinnedActionBar(visible = showBar, modifier = Modifier.align(Alignment.BottomCenter)) {
                    AnimatedContent(
                        targetState = selecting,
                        transitionSpec = {
                            (fadeIn(finite(reduced)) togetherWith fadeOut(finite(reduced)))
                                .using(SizeTransform(clip = false))
                        },
                        label = "atticBar",
                    ) { active ->
                        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                            if (active) {
                                BarFigures(
                                    count = Format.count(state.selected.size),
                                    noun = stringResource(R.string.archive_bar_selected),
                                    value = Format.bytesValue(selectedBytes),
                                    unit = Format.bytesUnit(selectedBytes),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                                    if (selectedPending) {
                                        PadlasButton(
                                            text = stringResource(R.string.archive_upload),
                                            onClick = {
                                                viewModel.enqueueSelected { ArchiveScheduler.enqueueUploads(context) }
                                            },
                                            enabled = !state.busy,
                                            icon = Icons.Rounded.CloudUpload,
                                            style = ButtonStyle.Secondary,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    PadlasButton(
                                        text = stringResource(R.string.archive_free),
                                        onClick = viewModel::requestGhosting,
                                        enabled = !state.busy && selectedBytes > 0,
                                        icon = Icons.Rounded.CloudDone,
                                        modifier = Modifier.weight(if (selectedPending) 1.4f else 1f),
                                    )
                                }
                            } else {
                                BarFigures(
                                    count = Format.count(freeable),
                                    noun = pluralStringResource(R.plurals.archive_bar_freeable, freeable),
                                    value = Format.bytesValue(state.reclaimable),
                                    unit = Format.bytesUnit(state.reclaimable),
                                )
                                PadlasButton(
                                    text = stringResource(R.string.archive_free_up_space),
                                    onClick = viewModel::freeUpAll,
                                    enabled = !state.busy,
                                    icon = Icons.Rounded.CloudDone,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
                Toast(
                    message = state.message?.asString(),
                    onShown = viewModel::dismissMessage,
                    icon = if (state.messageOk) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                    tint = if (state.messageOk) Ink.Success else Ink.Info,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = navBottom + if (showBar) PinnedBarClearance else Space.l),
                )
            },
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(GRID_COLUMNS),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Space.page,
                    end = Space.page,
                    top = topBarClearance() + Space.s,
                    bottom = navBottom + if (showBar) PinnedBarClearance else Space.xl,
                ),
                horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
                verticalArrangement = Arrangement.spacedBy(TILE_SPACING),
            ) {
                item(key = "account", span = { GridItemSpan(maxLineSpan) }) {
                    Box(modifier = Modifier.entrance(entrance).then(itemMotion())) {
                        AccountCard(state = state, onConnect = viewModel::connect)
                    }
                }

                if (state.reclaimable > 0) {
                    item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
                        ReclaimHero(
                            label = stringResource(R.string.archive_hero_label),
                            value = Format.bytesValue(state.reclaimable),
                            unit = Format.bytesUnit(state.reclaimable),
                            caption = stringResource(R.string.archive_hero_caption),
                            glow = Ink.Info,
                            modifier = Modifier.entrance(entrance).then(itemMotion()).padding(top = Space.m),
                        )
                    }
                }

                if (state.progress.active || state.queue.itemCount > 0 || state.progress.message != null) {
                    item(key = "upload", span = { GridItemSpan(maxLineSpan) }) {
                        UploadCard(
                            state = state,
                            modifier = Modifier.entrance(entrance).then(itemMotion()).padding(top = Space.m),
                        )
                    }
                }

                if (!state.desktopNoticeRead) {
                    item(key = "desktop", span = { GridItemSpan(maxLineSpan) }) {
                        DesktopNotice(
                            onDismiss = viewModel::dismissDesktopNotice,
                            modifier = Modifier.entrance(entrance).then(itemMotion()).padding(top = Space.m),
                        )
                    }
                }

                if (state.assets.isEmpty()) {
                    if (!state.loading) {
                        item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(
                                title = stringResource(R.string.archive_empty_title),
                                text = stringResource(R.string.archive_empty_text),
                                icon = Icons.Rounded.Cloud,
                                tint = Ink.Info,
                                modifier = Modifier.entrance(entrance).padding(top = Space.l),
                            )
                        }
                    }
                } else {
                    item(key = "grid-head", span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            modifier = Modifier
                                .entrance(entrance)
                                .then(itemMotion())
                                .padding(top = Space.l, bottom = Space.s),
                        ) {
                            SectionHeader(title = stringResource(R.string.archive_grid_title))
                            Text(
                                text = stringResource(R.string.archive_grid_hint),
                                style = PadlasTheme.type.caption,
                                color = Ink.TextSecondary,
                            )
                        }
                    }
                    items(state.assets, key = { it.mediaStoreId }) { asset ->
                        val model: Any? = if (asset.state == AssetState.GHOST) {
                            asset.ghostThumbPath?.let(::File)
                        } else {
                            viewModel.uriOf(asset)
                        }
                        val caption = "${Format.bytes(asset.sizeBytes)} · ${stringResource(whereItLives(asset.state))}"
                        AtticTile(
                            asset = asset,
                            selected = asset.mediaStoreId in state.selected,
                            uploadFraction = state.progress
                                .takeIf { it.active && it.assetId == asset.mediaStoreId }
                                ?.fraction,
                            onToggle = {
                                haptic?.tick()
                                viewModel.toggle(asset.mediaStoreId)
                            },
                            onView = {
                                haptic?.tick(0.2f)
                                zoom.open(
                                    ZoomTarget(
                                        key = asset.mediaStoreId,
                                        model = model,
                                        title = asset.displayName,
                                        caption = caption,
                                    ),
                                )
                            },
                            model = model,
                            modifier = Modifier.entrance(entrance).then(itemMotion()),
                        )
                    }
                }
            }
        }
    }
}

@StringRes
private fun whereItLives(state: AssetState): Int = when (state) {
    AssetState.GHOST -> R.string.archive_where_cloud_only
    AssetState.ARCHIVED -> R.string.archive_where_both
    AssetState.UPLOADING -> R.string.archive_where_uploading
    else -> R.string.archive_where_waiting
}

@Composable
private fun AtticTile(
    asset: Asset,
    selected: Boolean,
    uploadFraction: Float?,
    onToggle: () -> Unit,
    onView: () -> Unit,
    model: Any?,
    modifier: Modifier = Modifier,
) {
    val image: @Composable (Modifier) -> Unit = { tileModifier ->
        ZoomableImage(key = asset.mediaStoreId, modifier = tileModifier) { imageModifier, onLoaded ->
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { onLoaded(it.painter.intrinsicSize, it.result.memoryCacheKey) },
                modifier = imageModifier,
            )
        }
    }
    Box(modifier = modifier) {
        if (asset.state == AssetState.GHOST) {
            // A ghost cannot be selected, so a tap is free to open the closer look.
            Box(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Image,
                    onClick = onView,
                ),
            ) {
                GhostTile(model = model, image = image)
            }
        } else {
            SelectableTile(
                model = model,
                selected = selected,
                onToggle = onToggle,
                onLongPress = onView,
                badge = when {
                    uploadFraction != null -> Format.percent(uploadFraction)
                    asset.state == AssetState.UPLOADING -> stringResource(R.string.archive_badge_uploading)
                    else -> null
                },
                image = image,
            )
        }
    }
}

@Composable
private fun AccountCard(state: AtticUiState, onConnect: () -> Unit) {
    when (val auth = state.authState) {
        AuthState.Connected -> ListRow(
            title = stringResource(R.string.archive_drive_connected),
            subtitle = state.assets.filter { it.state != AssetState.UPLOADING }.let { inCloud ->
                pluralStringResource(
                    R.plurals.archive_drive_in_cloud,
                    inCloud.size,
                    Format.count(inCloud.size),
                    Format.bytes(inCloud.sumOf { it.sizeBytes }),
                )
            },
            icon = Icons.Rounded.CloudDone,
            iconTint = Ink.Success,
        )

        AuthState.Unconfigured -> ListRow(
            title = stringResource(R.string.archive_drive_unconfigured_title),
            subtitle = stringResource(R.string.archive_drive_unconfigured_text),
            icon = Icons.Rounded.CloudOff,
            iconTint = Ink.Warning,
        )

        is AuthState.Failed -> ConnectCard(
            icon = Icons.Rounded.ErrorOutline,
            tint = Ink.Danger,
            title = stringResource(R.string.archive_connect_failed),
            text = auth.message.asString(),
            actionLabel = stringResource(R.string.archive_retry),
            actionIcon = Icons.Rounded.Refresh,
            onAction = onConnect,
        )

        else -> ConnectCard(
            icon = Icons.Rounded.Cloud,
            tint = Ink.Info,
            title = stringResource(R.string.archive_connect_title),
            text = stringResource(R.string.archive_connect_text),
            actionLabel = stringResource(R.string.archive_connect_action),
            actionIcon = Icons.Rounded.AddLink,
            onAction = onConnect,
        )
    }
}

@Composable
private fun ConnectCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    text: String,
    actionLabel: String,
    actionIcon: ImageVector,
    onAction: () -> Unit,
) {
    PadlasCard(modifier = Modifier.fillMaxWidth(), contentPadding = Space.xl) {
        Column {
            IconBubble(icon = icon, tint = tint, size = 48.dp, iconSize = 24.dp)
            Spacer(Modifier.height(Space.l))
            Text(text = title, style = PadlasTheme.type.title, color = Ink.Text)
            Spacer(Modifier.height(Space.xs))
            Text(text = text, style = PadlasTheme.type.body, color = Ink.TextSecondary)
            Spacer(Modifier.height(Space.l))
            PadlasButton(
                text = actionLabel,
                onClick = onAction,
                icon = actionIcon,
                size = ButtonSize.Medium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun UploadCard(state: AtticUiState, modifier: Modifier = Modifier) {
    val progress = state.progress
    PadlasCard(modifier = modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (progress.active) {
                    ProgressRing(fraction = progress.fraction, size = 44.dp, stroke = 4.dp, color = Ink.Info) {
                        Icon(
                            imageVector = Icons.Rounded.CloudUpload,
                            contentDescription = null,
                            tint = Ink.Info,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    IconBubble(icon = Icons.Rounded.CloudQueue, tint = Ink.Info)
                }
                Spacer(Modifier.width(Space.m))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            progress.active -> stringResource(R.string.archive_upload_active)
                            state.queue.itemCount > 0 -> pluralStringResource(
                                R.plurals.archive_upload_waiting,
                                state.queue.itemCount,
                                Format.count(state.queue.itemCount),
                            )
                            else -> stringResource(R.string.archive_upload_stopped)
                        },
                        style = PadlasTheme.type.bodyStrong,
                        color = Ink.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when {
                            progress.active -> progress.name
                            state.queue.itemCount > 0 ->
                                stringResource(R.string.archive_upload_waiting_detail, Format.bytes(state.queue.totalBytes))
                            else -> stringResource(R.string.archive_upload_retry_detail)
                        },
                        style = PadlasTheme.type.caption,
                        color = Ink.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (progress.active) {
                Spacer(Modifier.height(Space.l))
                ProgressStrip(
                    fraction = progress.fraction,
                    label = if (state.queue.itemCount > 1) {
                        pluralStringResource(
                            R.plurals.archive_upload_progress_more,
                            state.queue.itemCount - 1,
                            Format.bytes(progress.bytesSent),
                            Format.bytes(progress.totalBytes),
                            Format.count(state.queue.itemCount - 1),
                        )
                    } else {
                        "${Format.bytes(progress.bytesSent)} / ${Format.bytes(progress.totalBytes)}"
                    },
                )
            } else {
                progress.message?.let { reason ->
                    Text(
                        text = reason.asString(),
                        style = PadlasTheme.type.caption,
                        color = Ink.Danger,
                        modifier = Modifier.padding(top = Space.m),
                    )
                }
            }
        }
    }
}

/**
 * Android 15 caps `dataSync` foreground work at about six hours a day, so a first upload
 * of a full library over the phone would take weeks. Saying so once is more honest than
 * letting the queue crawl.
 */
@Composable
private fun DesktopNotice(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    PadlasCard(modifier = modifier.fillMaxWidth(), background = Ink.SurfaceHigh) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                IconBubble(icon = Icons.Rounded.Computer, tint = Ink.Accent)
                Spacer(Modifier.width(Space.m))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.archive_desktop_title),
                        style = PadlasTheme.type.bodyStrong,
                        color = Ink.Text,
                    )
                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        text = stringResource(R.string.archive_desktop_text),
                        style = PadlasTheme.type.caption,
                        color = Ink.TextSecondary,
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PadlasTextButton(text = stringResource(R.string.archive_got_it), onClick = onDismiss)
            }
        }
    }
}

/** The full picture, on top of the tile's cached thumbnail until it has loaded. */
@Composable
private fun ZoomPicture(target: ZoomTarget, placeholder: Any?, modifier: Modifier) {
    val context = LocalContext.current
    val request = remember(target.model, placeholder) {
        ImageRequest.Builder(context)
            .data(target.model)
            .placeholderMemoryCacheKey(placeholder as? MemoryCache.Key)
            .crossfade(true)
            .build()
    }
    AsyncImage(model = request, contentDescription = target.title, contentScale = ContentScale.Crop, modifier = modifier)
}

private const val GRID_COLUMNS = 4
private val TILE_SPACING = 3.dp
