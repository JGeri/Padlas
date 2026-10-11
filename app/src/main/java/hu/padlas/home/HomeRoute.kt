package hu.padlas.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.rounded.CalendarMonth
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
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.ButtonSize
import hu.padlas.core.design.component.ButtonStyle
import hu.padlas.core.design.component.GaugeSegment
import hu.padlas.core.design.component.IconBubble
import hu.padlas.core.design.component.LargeHeader
import hu.padlas.core.design.component.LinearMeter
import hu.padlas.core.design.component.ListRow
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.PadlasIconButton
import hu.padlas.core.design.component.ProgressStrip
import hu.padlas.core.design.component.SectionHeader
import hu.padlas.core.design.component.StaggeredColumnItem
import hu.padlas.core.design.component.StorageGauge
import hu.padlas.core.design.component.glass
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.morphBounds
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.CategoryColor
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.ProgressGradient
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space
import hu.padlas.core.media.MediaThumb
import hu.padlas.feature.index.IndexScheduler
import hu.padlas.feature.index.IndexState
import hu.padlas.work.PadlasBootstrap

enum class Tool { SWIPE, STORAGE, PENDING, DUPLICATES, VIDEOS, DISCARD, BROWSE, OLD_PHOTOS, TRASH, CLOUD, RULES, LOG, SETTINGS }

@Composable
fun HomeRoute(
    onOpen: (Tool, String?) -> Unit,
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
    val wins = winsOf(state)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.page,
            end = Space.page,
            top = top + Space.s,
            bottom = contentPadding.calculateBottomPadding() + Space.l,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            StaggeredColumnItem(0) {
                LargeHeader(
                    title = "Padlás",
                    subtitle = headerLine(state),
                    actions = {
                        PadlasIconButton(
                            icon = Icons.Rounded.Settings,
                            contentDescription = "Beállítások",
                            onClick = { onOpen(Tool.SETTINGS, "tool-SETTINGS") },
                            modifier = Modifier.morphBounds("tool-SETTINGS"),
                        )
                    },
                )
            }
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

        item(key = "storage") {
            StaggeredColumnItem(1) { StorageHero(state = state, onAnalyze = { onOpen(Tool.STORAGE, null) }) }
        }

        (state.index as? IndexState.Scanning)?.let { scanning ->
            item(key = "indexing") {
                PadlasCard(modifier = Modifier.fillMaxWidth().animateItem()) {
                    ProgressStrip(
                        fraction = scanning.fraction,
                        label = "Galéria beolvasása · ${Format.count(scanning.scanned)} / ${Format.count(scanning.total)}",
                    )
                }
            }
        }

        item(key = "swipe") {
            StaggeredColumnItem(2) {
                SwipeHero(
                    state = state,
                    onStart = { onOpen(Tool.SWIPE, null) },
                    onPending = { onOpen(Tool.PENDING, "home-pending") },
                )
            }
        }

        if (wins.isNotEmpty()) {
            item(key = "wins-title") { StaggeredColumnItem(3) { SectionHeader(title = "Gyors nyereség") } }
            wins.chunked(2).forEachIndexed { index, row ->
                item(key = "wins-$index") {
                    StaggeredColumnItem(4 + index) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { win ->
                                WinTile(win = win, onClick = { onOpen(win.tool, "win-${win.tool}") }, modifier = Modifier.weight(1f))
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
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

        item(key = "tools-title") { StaggeredColumnItem(7) { SectionHeader(title = "Eszközök") } }
        tools.chunked(2).forEachIndexed { index, row ->
            item(key = "tools-$index") {
                StaggeredColumnItem(8 + index) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { tool ->
                            ToolTile(tool = tool, onClick = { onOpen(tool.tool, "tool-${tool.tool}") }, modifier = Modifier.weight(1f))
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun headerLine(state: HomeUiState): String {
    val total = state.photoCount + state.videoCount
    return if (total > 0) "${Format.count(total)} fotó és videó" else "Tárhely és galéria egy helyen"
}

@Composable
private fun AccessHero(partial: Boolean, onGrant: () -> Unit, onOpenSettings: () -> Unit) {
    PadlasCard(modifier = Modifier.fillMaxWidth(), border = Ink.Accent.copy(alpha = 0.45f)) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
            IconBubble(icon = Icons.Rounded.PhotoLibrary, tint = Ink.Accent, size = 48.dp, iconSize = 24.dp)
            Text(
                text = if (partial) "Csak néhány fotót látok" else "Engedd meg a fotók elérését",
                style = PadlasTheme.type.title,
                color = Ink.Text,
            )
            Text(
                text = if (partial) {
                    "Most csak a kiválasztott képekhez férek hozzá. A teljes válogatáshoz és a javaslatokhoz minden fotó és videó kell."
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
        GaugeSegment("Minden más", state.otherBytes, CategoryColor.Apps),
    )
    PadlasCard(modifier = Modifier.fillMaxWidth(), onClick = onAnalyze, contentPadding = 18.dp, shape = Shapes.hero) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StorageGauge(
                    segments = segments,
                    freeBytes = volume.freeBytes,
                    totalBytes = volume.totalBytes,
                    diameter = 148.dp,
                    thickness = 13.dp,
                    compact = true,
                )
                Spacer(Modifier.width(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    LegendLine("Fotók", Format.bytes(state.photoBytes), CategoryColor.Photos)
                    LegendLine("Videók", Format.bytes(state.videoBytes), CategoryColor.Videos)
                    LegendLine("Minden más", Format.bytes(state.otherBytes), CategoryColor.Apps)
                    LegendLine("Összesen", Format.bytes(volume.totalBytes), Ink.TextTertiary)
                }
            }
            Spacer(Modifier.height(Space.m))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (volume.totalBytes > 0) "${(volume.usedBytes * 100 / volume.totalBytes)}% foglalt" else "Mérés…",
                    style = PadlasTheme.type.caption,
                    color = Ink.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(text = "Tárhely elemzése", style = PadlasTheme.type.label, color = Ink.Accent)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = Ink.Accent, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun LegendLine(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(9.dp)
                .clip(Shapes.pill)
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = PadlasTheme.type.caption, color = Ink.TextSecondary, modifier = Modifier.weight(1f), maxLines = 1)
        Text(text = value, style = PadlasTheme.type.numeric, color = Ink.Text, maxLines = 1)
    }
}

/**
 * The one lit element of the overview. It breathes a little, slowly: the place the app
 * wants your thumb to go next.
 */
@Composable
private fun SwipeHero(state: HomeUiState, onStart: () -> Unit, onPending: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val breath = if (reduced) {
        null
    } else {
        rememberInfiniteTransition(label = "breath").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
            label = "breathValue",
        )
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = motionSpec(Motion.rest()),
        label = "heroPress",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = press
                scaleY = press
                val b = breath?.value ?: 0.5f
                shadowElevation = (18 + 18 * b).dp.toPx()
                shape = Shapes.hero
                clip = false
                ambientShadowColor = Ink.Accent
                spotShadowColor = Ink.Accent
            }
            .glass(Shapes.hero, Color.Transparent, Ink.Accent.copy(alpha = 0.45f))
            .background(Brush.linearGradient(listOf(Ink.Accent.copy(alpha = 0.32f), Ink.AccentCyan.copy(alpha = 0.12f))))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onStart)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PhotoFan(state.newest.map { MediaThumb(Uri.parse(it.uri), 256) })
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Válogass swipe-pal", style = PadlasTheme.type.title, color = Ink.Text)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Balra törlés, jobbra marad, felfelé kedvenc",
                    style = PadlasTheme.type.caption,
                    color = Ink.TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                LinearMeter(
                    fraction = if (state.reviewable > 0) state.reviewed.toFloat() / state.reviewable else 0f,
                    brush = ProgressGradient,
                    track = Color.White.copy(alpha = 0.12f),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (state.reviewable > 0) {
                        "${Format.count(state.reviewed)} / ${Format.count(state.reviewable)} átnézve"
                    } else {
                        "Még nincs mit átnézni"
                    },
                    style = PadlasTheme.type.micro,
                    color = Ink.TextSecondary,
                )
            }
        }
        if (state.pending.itemCount > 0) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .morphBounds("home-pending")
                    .glass(Shapes.pill, Ink.DangerSoft, Ink.Danger.copy(alpha = 0.5f))
                    .clickable(onClick = onPending)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = Ink.Danger, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${Format.count(state.pending.itemCount)} elem vár törlésre · ${Format.bytes(state.pending.totalBytes)}",
                    style = PadlasTheme.type.label,
                    color = Ink.Text,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = Ink.Danger, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Three of the newest photos, fanned like a hand of cards: the deck you are about to hold. */
@Composable
private fun PhotoFan(thumbs: List<MediaThumb>) {
    Box(modifier = Modifier.size(width = 78.dp, height = 92.dp)) {
        val angles = listOf(-12f, 7f, 0f)
        val shifts = listOf(-4.dp, 8.dp, 2.dp)
        angles.forEachIndexed { index, angle ->
            val model = thumbs.getOrNull(index)
            Box(
                modifier = Modifier
                    .offset(x = 8.dp + shifts[index], y = 6.dp)
                    .size(width = 56.dp, height = 76.dp)
                    .rotate(angle)
                    .shadow(if (index == 2) 14.dp else 4.dp, Shapes.small)
                    .clip(Shapes.small)
                    .background(Ink.SurfaceHighest),
            ) {
                if (model != null) {
                    AsyncImage(
                        model = model,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private data class Win(
    val tool: Tool,
    val value: String,
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val weight: Long,
)

private fun winsOf(state: HomeUiState): List<Win> = buildList {
    if (state.duplicates.itemCount > 0) {
        add(Win(Tool.DUPLICATES, Format.bytes(state.duplicates.totalBytes), "${Format.count(state.duplicates.itemCount)} duplikátum", Icons.Rounded.ContentCopy, Ink.Accent, state.duplicates.totalBytes))
    }
    if (state.discardable.itemCount > 0) {
        add(Win(Tool.DISCARD, Format.bytes(state.discardable.totalBytes), "${Format.count(state.discardable.itemCount)} eldobható kép", Icons.Rounded.Screenshot, Ink.Warning, state.discardable.totalBytes))
    }
    if (state.largeVideos.itemCount > 0) {
        add(Win(Tool.VIDEOS, Format.bytes(state.largeVideos.totalBytes), "${Format.count(state.largeVideos.itemCount)} nagy videó", Icons.Rounded.VideoLibrary, CategoryColor.Videos, state.largeVideos.totalBytes / 2))
    }
    if (state.trashTotal > 0) {
        add(Win(Tool.TRASH, Format.bytes(state.trashTotal), "a kukában", Icons.Rounded.DeleteSweep, Ink.Danger, state.trashTotal))
    }
    if (state.oldPhotos.itemCount > 0) {
        add(Win(Tool.OLD_PHOTOS, Format.bytes(state.oldPhotos.totalBytes), "${Format.count(state.oldPhotos.itemCount)} régi fotó", Icons.Rounded.CalendarMonth, Ink.Info, state.oldPhotos.totalBytes / 4))
    }
}.sortedByDescending { it.weight }

@Composable
private fun WinTile(win: Win, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PadlasCard(
        modifier = modifier.morphBounds("win-${win.tool}"),
        onClick = onClick,
        contentPadding = 16.dp,
    ) {
        Column {
            IconBubble(icon = win.icon, tint = win.tint, size = 38.dp, iconSize = 19.dp)
            Spacer(Modifier.height(12.dp))
            Text(text = win.value, style = PadlasTheme.type.numericLarge, color = Ink.Text, maxLines = 1)
            Text(text = win.label, style = PadlasTheme.type.caption, color = Ink.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
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
    PadlasCard(
        modifier = modifier.morphBounds("tool-${tool.tool}"),
        onClick = onClick,
        contentPadding = 16.dp,
    ) {
        Column {
            IconBubble(icon = tool.icon, tint = tool.tint, size = 40.dp)
            Spacer(Modifier.height(12.dp))
            Text(text = tool.title, style = PadlasTheme.type.bodyStrong, color = Ink.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = tool.subtitle, style = PadlasTheme.type.caption, color = Ink.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
