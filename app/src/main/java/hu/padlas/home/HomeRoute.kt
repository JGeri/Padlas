package hu.padlas.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.ButtonSize
import hu.padlas.core.design.component.ButtonStyle
import hu.padlas.core.design.component.GaugeSegment
import hu.padlas.core.design.component.IconBubble
import hu.padlas.core.design.component.LargeHeader
import hu.padlas.core.design.component.LegendItem
import hu.padlas.core.design.component.LinearMeter
import hu.padlas.core.design.component.ListRow
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.PadlasIconButton
import hu.padlas.core.design.component.ProgressStrip
import hu.padlas.core.design.component.SectionHeader
import hu.padlas.core.design.component.StorageGauge
import hu.padlas.core.design.theme.AccentGradient
import hu.padlas.core.design.theme.CategoryColor
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space
import hu.padlas.feature.index.IndexScheduler
import hu.padlas.feature.index.IndexState
import hu.padlas.work.PadlasBootstrap

enum class Tool { SWIPE, STORAGE, PENDING, DUPLICATES, VIDEOS, DISCARD, BROWSE, OLD_PHOTOS, TRASH, CLOUD, RULES, LOG, SETTINGS }

@Composable
fun HomeRoute(
    onOpen: (Tool) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.refresh()
    }
    val requestMedia = {
        permissions.launch(
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS,
            ),
        )
    }

    LaunchedEffect(Unit) {
        val granted = context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestMedia()
    }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    // The first run has nothing to show until the index exists, so it starts itself; once
    // it exists the measuring passes (hashes, screenshot recognition) are queued as well.
    LaunchedEffect(state.device.mediaAccess, state.indexedCount == 0) {
        if (!state.device.mediaAccess) return@LaunchedEffect
        if (state.indexedCount == 0) IndexScheduler.enqueueFullScan(context) else PadlasBootstrap.onIndexReady(context)
    }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Ink.Background),
        contentPadding = PaddingValues(
            start = Space.page,
            end = Space.page,
            top = top,
            bottom = contentPadding.calculateBottomPadding() + Space.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item(key = "header") {
            LargeHeader(
                title = "Padlás",
                subtitle = headerLine(state),
                actions = {
                    PadlasIconButton(
                        icon = Icons.Rounded.Settings,
                        contentDescription = "Beállítások",
                        onClick = { onOpen(Tool.SETTINGS) },
                        background = Ink.Surface,
                    )
                },
            )
        }

        if (!state.device.mediaAccess && !state.loading) {
            item(key = "access") {
                AccessHero(
                    partial = state.device.partialMediaAccess,
                    onGrant = requestMedia,
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                        )
                    },
                )
            }
        }

        item(key = "storage") { StorageHero(state = state, onAnalyze = { onOpen(Tool.STORAGE) }) }

        (state.index as? IndexState.Scanning)?.let { scanning ->
            item(key = "indexing") {
                PadlasCard(modifier = Modifier.fillMaxWidth()) {
                    ProgressStrip(
                        fraction = scanning.fraction,
                        label = "Galéria beolvasása · ${Format.count(scanning.scanned)} / ${Format.count(scanning.total)}",
                    )
                }
            }
        }

        item(key = "swipe") {
            SwipeCard(
                reviewed = state.reviewed,
                reviewable = state.reviewable,
                pendingCount = state.pending.itemCount,
                pendingBytes = state.pending.totalBytes,
                onStart = { onOpen(Tool.SWIPE) },
                onPending = { onOpen(Tool.PENDING) },
            )
        }

        item(key = "suggestions-title") { SectionHeader(title = "Javaslatok") }
        val suggestions = suggestionsOf(state)
        if (suggestions.isEmpty()) {
            item(key = "all-good") {
                ListRow(
                    title = "Minden rendben",
                    subtitle = if (state.indexedCount == 0) "A beolvasás után itt jelennek meg a javaslatok." else "Most nincs gyors nyereség. Válogass vagy nézd meg a tárhelyet.",
                    icon = Icons.Rounded.CheckCircle,
                    iconTint = Ink.Success,
                )
            }
        } else {
            suggestions.forEach { suggestion ->
                item(key = "suggestion-${suggestion.tool}") {
                    ListRow(
                        title = suggestion.title,
                        subtitle = suggestion.subtitle,
                        icon = suggestion.icon,
                        iconTint = suggestion.tint,
                        value = suggestion.value,
                        valueColor = Ink.Text,
                        onClick = { onOpen(suggestion.tool) },
                    )
                }
            }
        }

        if (state.device.oneDriveRisk) {
            item(key = "onedrive") {
                ListRow(
                    title = "A OneDrive-szinkron be lehet kapcsolva",
                    subtitle = "Ilyenkor a helyi törlés a felhőből is törölhet. Törlés előtt kapcsold ki a Samsung Galéria szinkronját.",
                    icon = Icons.Rounded.Warning,
                    iconTint = Ink.Warning,
                    showChevron = false,
                )
            }
        }

        item(key = "tools-title") { SectionHeader(title = "Eszközök") }
        tools.chunked(2).forEachIndexed { index, row ->
            item(key = "tools-$index") {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    row.forEach { tool ->
                        ToolTile(tool = tool, onClick = { onOpen(tool.tool) }, modifier = Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun headerLine(state: HomeUiState): String {
    val volume = state.device.volume
    if (volume.totalBytes <= 0L) return "Tárhely és galéria egy helyen"
    return "${Format.bytes(volume.freeBytes)} szabad · ${Format.bytes(volume.totalBytes)} összesen"
}

@Composable
private fun AccessHero(partial: Boolean, onGrant: () -> Unit, onOpenSettings: () -> Unit) {
    PadlasCard(modifier = Modifier.fillMaxWidth(), border = Ink.Accent.copy(alpha = 0.4f)) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
            IconBubble(icon = Icons.Rounded.PhotoLibrary, tint = Ink.Accent, size = 48.dp, iconSize = 24.dp)
            Text(
                text = if (partial) "Csak néhány fotót látok" else "Engedd meg a fotók elérését",
                style = PadlasTheme.type.title,
                color = Ink.Text,
            )
            Text(
                text = if (partial) {
                    "Most csak a kiválasztott képekhez férek hozzá. A teljes válogatáshoz és a tárhely-javaslatokhoz minden fotó és videó kell."
                } else {
                    "A válogatáshoz és a takarítási javaslatokhoz látnom kell a fotóidat és videóidat. Semmi nem hagyja el a telefont."
                },
                style = PadlasTheme.type.body,
                color = Ink.TextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                PadlasButton(text = "Hozzáférés", onClick = onGrant, size = ButtonSize.Medium, modifier = Modifier.weight(1f))
                PadlasButton(
                    text = "Beállítások",
                    onClick = onOpenSettings,
                    style = ButtonStyle.Secondary,
                    size = ButtonSize.Medium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StorageHero(state: HomeUiState, onAnalyze: () -> Unit) {
    val volume = state.device.volume
    val segments = listOf(
        GaugeSegment("Fotók", state.photoBytes, CategoryColor.Photos),
        GaugeSegment("Videók", state.videoBytes, CategoryColor.Videos),
        GaugeSegment("Minden más", state.otherBytes, CategoryColor.Other),
    )
    PadlasCard(modifier = Modifier.fillMaxWidth(), contentPadding = Space.xl) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            StorageGauge(
                segments = segments,
                freeBytes = volume.freeBytes,
                totalBytes = volume.totalBytes,
            )
            Spacer(Modifier.height(Space.l))
            Column(verticalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
                LegendItem(
                    label = "Fotók · ${Format.count(state.photoCount)} db",
                    value = Format.bytes(state.photoBytes),
                    color = CategoryColor.Photos,
                    modifier = Modifier.fillMaxWidth(),
                )
                LegendItem(
                    label = "Videók · ${Format.count(state.videoCount)} db",
                    value = Format.bytes(state.videoBytes),
                    color = CategoryColor.Videos,
                    modifier = Modifier.fillMaxWidth(),
                )
                LegendItem(
                    label = "Appok, rendszer és egyéb",
                    value = Format.bytes(state.otherBytes),
                    color = CategoryColor.Other,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(Space.l))
            PadlasButton(
                text = "Tárhely elemzése",
                onClick = onAnalyze,
                style = ButtonStyle.Secondary,
                size = ButtonSize.Medium,
                icon = Icons.Rounded.DonutLarge,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SwipeCard(
    reviewed: Int,
    reviewable: Int,
    pendingCount: Int,
    pendingBytes: Long,
    onStart: () -> Unit,
    onPending: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(AccentGradient)
            .padding(Space.xl),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(
                    icon = Icons.Rounded.Swipe,
                    tint = Color.White,
                    background = Color.White.copy(alpha = 0.18f),
                )
                Column(Modifier.padding(start = Space.m).weight(1f)) {
                    Text(text = "Válogass swipe-pal", style = PadlasTheme.type.title, color = Color.White)
                    Text(
                        text = if (reviewable > 0) {
                            "${Format.count(reviewed)} / ${Format.count(reviewable)} elem átnézve"
                        } else {
                            "Balra törlés, jobbra megtartás, felfelé kedvenc"
                        },
                        style = PadlasTheme.type.caption,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (reviewable > 0) {
                LinearMeter(
                    fraction = reviewed.toFloat() / reviewable,
                    color = Color.White,
                    track = Color.White.copy(alpha = 0.22f),
                    height = 5.dp,
                )
            }
            Spacer(Modifier.height(Space.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                PadlasButton(
                    text = "Hónap vagy mappa",
                    onClick = onStart,
                    style = ButtonStyle.Secondary,
                    size = ButtonSize.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (pendingCount > 0) {
                    PadlasButton(
                        text = "${Format.count(pendingCount)} törlésre",
                        onClick = onPending,
                        style = ButtonStyle.Destructive,
                        size = ButtonSize.Medium,
                        icon = Icons.Rounded.Delete,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (pendingCount > 0) {
                Text(
                    text = "${Format.bytes(pendingBytes)} vár törlésre",
                    style = PadlasTheme.type.micro,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
        }
    }
}

private data class Suggestion(
    val tool: Tool,
    val title: String,
    val subtitle: String,
    val value: String,
    val icon: ImageVector,
    val tint: Color,
)

private fun suggestionsOf(state: HomeUiState): List<Suggestion> = buildList {
    if (state.duplicates.itemCount > 0) {
        add(
            Suggestion(
                Tool.DUPLICATES,
                "${Format.count(state.duplicates.itemCount)} duplikátum",
                "Azonos és nagyon hasonló képek",
                Format.bytes(state.duplicates.totalBytes),
                Icons.Rounded.ContentCopy,
                Ink.Accent,
            ),
        )
    }
    if (state.largeVideos.itemCount > 0) {
        add(
            Suggestion(
                Tool.VIDEOS,
                "${Format.count(state.largeVideos.itemCount)} nagy videó",
                "Tömörítéssel nagyjából a fele visszanyerhető",
                Format.bytes(state.largeVideos.totalBytes),
                Icons.Rounded.VideoLibrary,
                CategoryColor.Videos,
            ),
        )
    }
    if (state.discardable.itemCount > 0) {
        add(
            Suggestion(
                Tool.DISCARD,
                "${Format.count(state.discardable.itemCount)} eldobható kép",
                "Képernyőképek, letöltött és chatképek",
                Format.bytes(state.discardable.totalBytes),
                Icons.Rounded.Screenshot,
                Ink.Warning,
            ),
        )
    }
    if (state.trashTotal > 0) {
        add(
            Suggestion(
                Tool.TRASH,
                "Kuka",
                "Ami a kukában van, még mindig helyet foglal",
                Format.bytes(state.trashTotal),
                Icons.Rounded.DeleteSweep,
                Ink.Danger,
            ),
        )
    }
    if (state.oldPhotos.itemCount > 0) {
        add(
            Suggestion(
                Tool.OLD_PHOTOS,
                "${Format.count(state.oldPhotos.itemCount)} egy évnél régebbi fotó",
                "Nézd át mappánként vagy mentsd a felhőbe",
                Format.bytes(state.oldPhotos.totalBytes),
                Icons.Rounded.CalendarMonth,
                Ink.Info,
            ),
        )
    }
}.sortedByDescending { suggestionWeight(it, state) }

private fun suggestionWeight(suggestion: Suggestion, state: HomeUiState): Long = when (suggestion.tool) {
    Tool.DUPLICATES -> state.duplicates.totalBytes
    Tool.VIDEOS -> state.largeVideos.totalBytes / 2
    Tool.DISCARD -> state.discardable.totalBytes
    Tool.TRASH -> state.trashTotal
    Tool.OLD_PHOTOS -> state.oldPhotos.totalBytes / 4
    else -> 0L
}

private data class ToolEntry(val tool: Tool, val title: String, val subtitle: String, val icon: ImageVector, val tint: Color)

private val tools = listOf(
    ToolEntry(Tool.STORAGE, "Tárhely-elemző", "Mi foglalja a helyet", Icons.Rounded.DonutLarge, Ink.Accent),
    ToolEntry(Tool.DUPLICATES, "Duplikátumok", "Azonos és hasonló képek", Icons.Rounded.ContentCopy, Ink.Info),
    ToolEntry(Tool.VIDEOS, "Nagy videók", "Tömörítés minőségvesztés nélkül", Icons.Rounded.VideoLibrary, CategoryColor.Videos),
    ToolEntry(Tool.DISCARD, "Eldobható képek", "Képernyőképek és letöltések", Icons.Rounded.Screenshot, Ink.Warning),
    ToolEntry(Tool.BROWSE, "Böngészés", "Mappák és hónapok rácsban", Icons.Rounded.FolderOpen, CategoryColor.Documents),
    ToolEntry(Tool.TRASH, "Kuka", "Visszaállítás vagy ürítés", Icons.Rounded.DeleteSweep, Ink.Danger),
    ToolEntry(Tool.CLOUD, "Felhő archívum", "Mentés Google Drive-ra", Icons.Rounded.CloudUpload, CategoryColor.Cloud),
    ToolEntry(Tool.RULES, "Szabályok", "Automatikus rendrakás", Icons.AutoMirrored.Rounded.Rule, Ink.Success),
    ToolEntry(Tool.LOG, "Napló", "Minden művelet visszanézhető", Icons.Rounded.History, Ink.TextSecondary),
)

@Composable
private fun ToolTile(tool: ToolEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PadlasCard(modifier = modifier, onClick = onClick, contentPadding = Space.l) {
        Column {
            IconBubble(icon = tool.icon, tint = tool.tint, size = 40.dp)
            Spacer(Modifier.height(Space.m))
            Text(text = tool.title, style = PadlasTheme.type.bodyStrong, color = Ink.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = tool.subtitle, style = PadlasTheme.type.caption, color = Ink.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
