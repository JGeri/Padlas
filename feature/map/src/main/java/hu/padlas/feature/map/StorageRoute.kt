package hu.padlas.feature.map

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.ConfirmDialog
import hu.padlas.core.design.component.LargeHeader
import hu.padlas.core.design.component.PadlasIconButton
import hu.padlas.core.design.component.PadlasTopBar
import hu.padlas.core.design.component.SegmentedControl
import hu.padlas.core.design.component.Toast
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.haptics
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.auroraBackground
import hu.padlas.core.design.theme.Space

/** The "Tárhely" tab: a WizTree-style map of the whole shared storage and what to do about it. */
@Composable
fun StorageRoute(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues()) {
    val viewModel: StorageViewModel = hiltViewModel()
    StorageScreen(
        viewModel = viewModel,
        contentPadding = contentPadding,
        largeHeader = true,
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    )
}

/** The same screen pushed on top of another one, with a back button instead of a tab title. */
@Composable
fun MapRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: StorageViewModel = hiltViewModel()
    val subtitle by viewModel.header.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val access by viewModel.access.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxSize()
            .auroraBackground()
            .systemBarsPadding(),
    ) {
        PadlasTopBar(
            title = "Tárhely",
            subtitle = subtitle,
            onBack = { if (!viewModel.navigateUp()) onBack() },
            action = { RefreshButton(enabled = access.allFiles && !scanning, onClick = viewModel::rescan) },
        )
        StorageScreen(
            viewModel = viewModel,
            contentPadding = PaddingValues(),
            largeHeader = false,
            modifier = Modifier.weight(1f),
        )
    }
}

private sealed interface Panel {
    data object None : Panel
    data object Busy : Panel
    data class Select(val selection: SelectionUi) : Panel
    data class Detail(val focus: Focus) : Panel
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StorageScreen(
    viewModel: StorageViewModel,
    contentPadding: PaddingValues,
    largeHeader: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val access by viewModel.access.collectAsStateWithLifecycle()
    val header by viewModel.header.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val progressState = viewModel.progress.collectAsStateWithLifecycle()
    val hasResult by viewModel.hasResult.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val view by viewModel.view.collectAsStateWithLifecycle()
    val directory by viewModel.directory.collectAsStateWithLifecycle()
    val types by viewModel.types.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val focus by viewModel.focus.collectAsStateWithLifecycle()
    val confirm by viewModel.confirm.collectAsStateWithLifecycle()
    val deleting by viewModel.deleting.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val reduced = LocalReducedMotion.current
    val haptic = haptics()

    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    val dir = directory
    val inDirectory = view == StorageView.MAP || view == StorageView.FOLDERS
    val cleanupOpen = view == StorageView.FILES && files?.cleanup != null
    BackHandler(enabled = selection.active || focus != null || cleanupOpen || (inDirectory && dir != null && !dir.isRoot)) {
        when {
            selection.active -> viewModel.clearSelection()
            focus != null -> viewModel.clearFocus()
            cleanupOpen -> viewModel.closeCleanup()
            else -> viewModel.navigateUp()
        }
    }

    val listState = rememberLazyListState()
    val switcherIndex = listOf(largeHeader, scanning, summary != null).count { it }
    LaunchedEffect(view, dir?.path, files?.cleanup) {
        // A new view or folder starts at its top, not wherever the previous list was left.
        if (listState.firstVisibleItemIndex > switcherIndex) listState.scrollToItem(switcherIndex)
    }

    val panel: Panel = when {
        deleting -> Panel.Busy
        selection.active -> Panel.Select(selection)
        focus != null -> focus?.let { Panel.Detail(it) } ?: Panel.None
        else -> Panel.None
    }
    val bottomInset = contentPadding.calculateBottomPadding()
    val listBottom = bottomInset + if (panel == Panel.None) Space.xl else 236.dp

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = listBottom),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (largeHeader) {
                item(key = "header") {
                    LargeHeader(
                        title = "Tárhely",
                        subtitle = header,
                        modifier = Modifier.padding(horizontal = Space.page),
                        actions = { RefreshButton(enabled = access.allFiles && !scanning, onClick = viewModel::rescan) },
                    )
                }
            }
            if (!access.allFiles) {
                item(key = "gate") {
                    AllFilesGate(
                        onGrant = { context.openAllFilesSettings() },
                        modifier = Modifier.padding(horizontal = Space.page, vertical = Space.s),
                    )
                }
                return@LazyColumn
            }
            if (scanning) {
                item(key = "progress") {
                    ScanProgressCard(
                        progressState = progressState,
                        rootPath = viewModel.rootPath,
                        onCancel = viewModel::cancelScan,
                        modifier = Modifier.padding(horizontal = Space.page, vertical = Space.s),
                    )
                }
            }
            if (!hasResult && !scanning) {
                item(key = "no-result") {
                    NoResultCard(onStart = viewModel::rescan, modifier = Modifier.padding(horizontal = Space.page, vertical = Space.s))
                }
            }
            summary?.let { current ->
                item(key = "summary") {
                    SummaryCard(current, modifier = Modifier.padding(horizontal = Space.page, vertical = Space.s))
                }
            }
            if (!hasResult) return@LazyColumn

            stickyHeader(key = "switcher") {
                ViewSwitcher(
                    view = view,
                    onSelect = {
                        haptic?.tick(0.2f)
                        viewModel.selectView(it)
                    },
                )
            }
            when (view) {
                StorageView.MAP -> if (dir != null) {
                    directoryHeader(dir, viewModel::jumpTo, { viewModel.navigateUp() })
                    mapSection(dir, focus, viewModel::onTileTap)
                }
                StorageView.FOLDERS -> if (dir != null) {
                    directoryHeader(dir, viewModel::jumpTo, { viewModel.navigateUp() })
                    foldersSection(
                        directory = dir,
                        selection = selection,
                        animate = !reduced,
                        onClick = { item ->
                            when {
                                selection.active -> viewModel.toggleSelection(item)
                                item.isDirectory -> viewModel.open(item.path)
                                else -> viewModel.focus(item, dir.bytes)
                            }
                        },
                        onLongClick = { item ->
                            haptic?.threshold()
                            viewModel.toggleSelection(item)
                        },
                    )
                }
                StorageView.TYPES -> types?.let { typesSection(it, viewModel::showCategory) }
                StorageView.FILES -> files?.let {
                    filesSection(
                        files = it,
                        selection = selection,
                        rootPath = viewModel.rootPath,
                        animate = !reduced,
                        onFilter = viewModel::setFilter,
                        onCleanup = viewModel::openCleanup,
                        onCloseCleanup = viewModel::closeCleanup,
                        onToggle = { item ->
                            haptic?.tick(0.2f)
                            viewModel.toggleSelection(item)
                        },
                        onSelectAll = viewModel::selectAll,
                        onClearSelection = viewModel::clearSelection,
                    )
                }
                StorageView.APPS -> appsSection(
                    apps = apps,
                    iconLoader = viewModel::appIcon,
                    onGrant = { context.openUsageSettings() },
                    onOpenApp = { context.openAppDetails(it) },
                    onNextStop = viewModel::advanceTour,
                    onRestartTour = viewModel::restartTour,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = bottomInset + Space.m)
                .padding(horizontal = Space.page),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            ToastHost(toast, onShown = viewModel::toastShown)
            BottomPanel(
                panel = panel,
                reduced = reduced,
                rootPath = viewModel.rootPath,
                hint = if (view == StorageView.MAP) "Koppints rá még egyszer a térképen, és megnyílik." else null,
                viewModel = viewModel,
            )
        }
    }

    confirm?.let { request ->
        ConfirmDialog(
            title = deleteTitle(request),
            text = deleteText(request),
            confirmLabel = "Végleges törlés",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
            destructive = true,
            icon = Icons.Rounded.DeleteOutline,
        )
    }
}

@Composable
private fun BottomPanel(
    panel: Panel,
    reduced: Boolean,
    rootPath: String,
    hint: String?,
    viewModel: StorageViewModel,
) {
    AnimatedContent(
        targetState = panel,
        contentKey = { it::class },
        transitionSpec = {
            val slide: FiniteAnimationSpec<IntOffset> = if (reduced) snap() else Motion.settle()
            val fade: FiniteAnimationSpec<Float> = if (reduced) snap() else Motion.settle()
            (slideInVertically(slide) { it / 2 } + fadeIn(fade)) togetherWith
                (slideOutVertically(slide) { it / 2 } + fadeOut(fade)) using SizeTransform(clip = false)
        },
        contentAlignment = Alignment.BottomCenter,
        label = "bottomPanel",
    ) { state ->
        when (state) {
            Panel.None -> Box(Modifier.fillMaxWidth())
            Panel.Busy -> BusyPill(text = "Törlés folyamatban…")
            is Panel.Select -> SelectionBar(
                selection = state.selection,
                onDelete = viewModel::requestDeleteSelection,
                onClear = viewModel::clearSelection,
            )
            is Panel.Detail -> FocusPanel(
                focus = state.focus,
                rootPath = rootPath,
                hint = if (state.focus is Focus.Item && state.focus.item.isDirectory) hint else null,
                onOpen = { viewModel.open(it.path) },
                onDelete = { viewModel.requestDelete(listOf(it)) },
                onShowAll = viewModel::showAllInFolder,
                onClose = viewModel::clearFocus,
            )
        }
    }
}

@Composable
private fun ToastHost(toast: ToastUi?, onShown: () -> Unit) {
    val haptic = haptics()
    var tone by remember { mutableStateOf(ToastTone.SUCCESS) }
    LaunchedEffect(toast) {
        val current = toast ?: return@LaunchedEffect
        tone = current.tone
        if (current.tone != ToastTone.ERROR) haptic?.commit()
    }
    Toast(
        message = toast?.message,
        onShown = onShown,
        icon = when (tone) {
            ToastTone.SUCCESS -> Icons.Rounded.CheckCircle
            ToastTone.WARNING -> Icons.Rounded.WarningAmber
            ToastTone.ERROR -> Icons.Rounded.ErrorOutline
        },
        tint = when (tone) {
            ToastTone.SUCCESS -> Ink.Success
            ToastTone.WARNING -> Ink.Warning
            ToastTone.ERROR -> Ink.Danger
        },
    )
}

@Composable
private fun ViewSwitcher(view: StorageView, onSelect: (StorageView) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink.Background)
            .padding(horizontal = Space.page, vertical = Space.s),
    ) {
        SegmentedControl(
            options = StorageView.entries.map { it.label },
            selectedIndex = view.ordinal,
            onSelect = { onSelect(StorageView.entries[it]) },
        )
    }
}

@Composable
private fun RefreshButton(enabled: Boolean, onClick: () -> Unit) {
    PadlasIconButton(
        icon = Icons.Rounded.Refresh,
        contentDescription = "Újraelemzés",
        onClick = onClick,
        enabled = enabled,
    )
}

private fun deleteTitle(request: DeleteRequest): String = when {
    request.items.size > 1 -> "Törlöd a kijelölt elemeket?"
    request.dirCount == 1 -> "Törlöd ezt a mappát?"
    else -> "Törlöd ezt a fájlt?"
}

private fun deleteText(request: DeleteRequest): String {
    val what = if (request.items.size == 1) {
        val item = request.items.first()
        val inside = when {
            !item.isDirectory -> ""
            item.fileCount == 0 -> " A mappa üres."
            else -> " Benne ${Format.count(item.fileCount)} fájl van."
        }
        "„${item.name}”, ${Format.bytes(item.bytes)}.$inside"
    } else {
        val inside = request.items.filter { it.isDirectory }.sumOf { it.fileCount }
        val extra = if (request.dirCount > 0 && inside > 0) " A mappákban ${Format.count(inside)} fájl van." else ""
        "${describeCount(request.fileCount, request.dirCount)}, összesen ${Format.bytes(request.bytes)}.$extra"
    }
    return "$what\n\nVéglegesen törlődik, nem vonható vissza."
}

private fun Context.openAllFilesSettings() {
    try {
        startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:$packageName".toUri()))
    } catch (e: ActivityNotFoundException) {
        runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
    }
}

private fun Context.openUsageSettings() {
    try {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, "package:$packageName".toUri()))
    } catch (e: ActivityNotFoundException) {
        runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
    }
}

private fun Context.openAppDetails(packageName: String) {
    runCatching {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
    }
}
