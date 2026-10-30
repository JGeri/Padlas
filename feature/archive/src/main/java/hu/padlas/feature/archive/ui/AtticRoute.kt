package hu.padlas.feature.archive.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
                    text = if (picked) "Kijelölés megszüntetése" else "Kijelölöm",
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
                    title = "Felhő archívum",
                    onBack = onBack,
                    subtitle = when {
                        selecting -> "${Format.count(state.selected.size)} kijelölve"
                        state.assets.isEmpty() -> "Google Drive"
                        else -> "Google Drive · ${Format.count(state.assets.size)} fájl"
                    },
                    action = {
                        AnimatedContent(
                            targetState = selecting,
                            transitionSpec = { popIn(reduced) togetherWith fadeOut(finite(reduced)) },
                            label = "atticCancel",
                        ) { active ->
                            if (active) {
                                PadlasTextButton(text = "Mégse", onClick = viewModel::clearSelection)
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
                                    noun = "kijelölve",
                                    value = Format.bytesValue(selectedBytes),
                                    unit = Format.bytesUnit(selectedBytes),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                                    if (selectedPending) {
                                        PadlasButton(
                                            text = "Feltöltés",
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
                                        text = "Felszabadítás",
                                        onClick = viewModel::requestGhosting,
                                        enabled = !state.busy && selectedBytes > 0,
                                        icon = Icons.Rounded.CloudDone,
                                        modifier = Modifier.weight(if (selectedPending) 1.4f else 1f),
                                    )
                                }
                            } else {
                                BarFigures(
                                    count = Format.count(freeable),
                                    noun = "fájl felszabadítható",
                                    value = Format.bytesValue(state.reclaimable),
                                    unit = Format.bytesUnit(state.reclaimable),
                                )
                                PadlasButton(
                                    text = "Hely felszabadítása",
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
                    message = state.message,
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
                            label = "Felszabadítható a telefonon",
                            value = Format.bytesValue(state.reclaimable),
                            unit = Format.bytesUnit(state.reclaimable),
                            caption = "Ezek a fájlok már biztonságban vannak a Drive-on, a telefonról törölhetők. " +
                                "Helyükön egy kis előnézet marad.",
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
                                title = "Még üres az archívum",
                                text = "A Böngészés vagy a Válogatás képernyőn jelölhetsz ki képeket, " +
                                    "amelyeket a Drive-odra mentünk.",
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
                            SectionHeader(title = "A felhőben")
                            Text(
                                text = "Koppints a képekre a kijelöléshez, hosszan nyomva nagyban is megnézheted őket. " +
                                    "A halvány képek már csak a felhőben vannak meg.",
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
                                        caption = "${Format.bytes(asset.sizeBytes)} · ${whereItLives(asset.state)}",
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

private fun whereItLives(state: AssetState): String = when (state) {
    AssetState.GHOST -> "csak a felhőben"
    AssetState.ARCHIVED -> "a telefonon és a felhőben"
    AssetState.UPLOADING -> "feltöltés alatt"
    else -> "feltöltésre vár"
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
                    asset.state == AssetState.UPLOADING -> "Feltöltés"
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
            title = "Google Drive csatlakoztatva",
            subtitle = state.assets.filter { it.state != AssetState.UPLOADING }.let { inCloud ->
                "${Format.count(inCloud.size)} fájl a felhőben · ${Format.bytes(inCloud.sumOf { it.sizeBytes })}"
            },
            icon = Icons.Rounded.CloudDone,
            iconTint = Ink.Success,
        )

        AuthState.Unconfigured -> ListRow(
            title = "A Drive-kapcsolat nincs beállítva",
            subtitle = "Ebből a változatból hiányzik a Google-azonosító, ezért most nem lehet feltölteni.",
            icon = Icons.Rounded.CloudOff,
            iconTint = Ink.Warning,
        )

        is AuthState.Failed -> ConnectCard(
            icon = Icons.Rounded.ErrorOutline,
            tint = Ink.Danger,
            title = "Nem sikerült csatlakozni",
            text = auth.message,
            actionLabel = "Újrapróbálom",
            actionIcon = Icons.Rounded.Refresh,
            onAction = onConnect,
        )

        else -> ConnectCard(
            icon = Icons.Rounded.Cloud,
            tint = Ink.Info,
            title = "Kösd össze a Google Drive-oddal",
            text = "A képek a saját Drive-od egy külön mappájába kerülnek. " +
                "Az app csak az általa feltöltött fájlokat látja.",
            actionLabel = "Google Drive összekötése",
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
                            progress.active -> "Feltöltés folyamatban"
                            state.queue.itemCount > 0 -> "${Format.count(state.queue.itemCount)} fájl vár feltöltésre"
                            else -> "A feltöltés megállt"
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
                                "${Format.bytes(state.queue.totalBytes)} · Wi-Fi-n, töltés közben halad"
                            else -> "Wi-Fi-n, töltés közben újra megpróbáljuk."
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
                    label = buildString {
                        append(Format.bytes(progress.bytesSent))
                        append(" / ")
                        append(Format.bytes(progress.totalBytes))
                        if (state.queue.itemCount > 1) {
                            append(" · még ")
                            append(Format.count(state.queue.itemCount - 1))
                            append(" fájl")
                        }
                    },
                )
            } else {
                progress.message?.let { reason ->
                    Text(
                        text = reason,
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
                        text = "Az első nagy feltöltést számítógépről érdemes",
                        style = PadlasTheme.type.bodyStrong,
                        color = Ink.Text,
                    )
                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        text = "A telefon naponta csak kb. hat órát tölthet fel a háttérben, így egy teljes " +
                            "galéria hetekig tartana. Utána az app már csak az újakat viszi fel.",
                        style = PadlasTheme.type.caption,
                        color = Ink.TextSecondary,
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PadlasTextButton(text = "Értem", onClick = onDismiss)
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
