package hu.padlas.vault

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.model.MediaTypeAggregate
import hu.padlas.core.design.component.ActionBar
import hu.padlas.core.design.component.ActionDirection
import hu.padlas.core.design.component.GaugeSegment
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.SectionLabel
import hu.padlas.core.design.component.StaggeredColumnItem
import hu.padlas.core.design.component.StorageGauge
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space
import hu.padlas.core.design.theme.Spectrum
import hu.padlas.feature.index.IndexScheduler

@Composable
fun VaultRoute(
    onOpenMap: () -> Unit,
    onOpenAttic: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenDedup: () -> Unit,
    onOpenDiscard: () -> Unit,
    onOpenVideo: () -> Unit,
    onOpenSort: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenIndex: () -> Unit,
    onOpenFolder: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refresh() }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS,
            ),
        )
    }

    // The first run has nothing to show until the index exists, so it starts itself.
    LaunchedEffect(state.indexedCount) {
        if (state.indexedCount == 0) IndexScheduler.enqueueFullScan(context)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .systemBarsPadding()
            .padding(horizontal = Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item {
            VaultHeader(
                indexedCount = state.indexedCount,
                onOpenRules = onOpenRules,
                onOpenIndex = onOpenIndex,
            )
        }

        item {
            val segments = remember(state.mediaTypes) { state.mediaTypes.toSegments() }
            StorageGauge(
                segments = segments,
                freeBytes = state.volume.freeBytes,
                totalBytes = state.volume.totalBytes,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item { Legend(state.mediaTypes) }

        if (state.oneDriveRisk) {
            item { OneDriveWarning() }
        }

        if (!state.allFilesAccess || !state.usageStatsAccess) {
            item {
                AccessRow(
                    allFiles = state.allFilesAccess,
                    usageStats = state.usageStatsAccess,
                    onAllFiles = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                    onUsageStats = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                )
            }
        }

        itemsIndexed(state.actions, key = { _, action -> action.kind.name }) { index, action ->
            StaggeredColumnItem(index = index) {
                ActionBar(
                    title = action.title(),
                    detail = action.detail(),
                    direction = action.direction(),
                    index = index,
                    onClick = {
                        when (action.kind) {
                            VaultActionKind.DEDUP -> onOpenDedup()
                            VaultActionKind.VIDEO -> onOpenVideo()
                            VaultActionKind.ARCHIVE -> onOpenAttic()
                            VaultActionKind.DISCARD -> onOpenDiscard()
                        }
                    },
                )
            }
        }

        if (state.trashBytes > 0 || state.samsungTrashBytes > 0) {
            item {
                TrashRow(
                    mediaStoreBytes = state.trashBytes,
                    samsungBytes = state.samsungTrashBytes,
                    onClick = onOpenDiscard,
                )
            }
        }

        if (state.junkFolders.isNotEmpty()) {
            item { SectionLabel(text = "SZEMÉTMAPPÁK", modifier = Modifier.padding(top = Space.l)) }
            items(state.junkFolders, key = { it.bucketId }) { bucket ->
                PadlasCard(
                    onClick = { onOpenFolder(bucket.bucketId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = bucket.bucketName.ifBlank { bucket.relativePath },
                            style = PadlasTheme.type.body,
                            color = Ink.Paper,
                        )
                        Text(
                            text = "${Format.count(bucket.itemCount)} · ${Format.bytes(bucket.totalBytes)}",
                            style = PadlasTheme.type.label,
                            color = Ink.Faded,
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.xl),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                PadlasTextButton(text = "Térkép", onClick = onOpenMap)
                PadlasTextButton(text = "Padlás", onClick = onOpenAttic)
                PadlasTextButton(text = "Válogató", onClick = onOpenSort)
                PadlasTextButton(text = "Napló", onClick = onOpenLog)
            }
        }

        item {
            PadlasTextButton(
                text = "Minden háttérmunka leállítása",
                onClick = viewModel::killAllWork,
                modifier = Modifier.padding(bottom = Space.xxl),
            )
        }
    }
}

@Composable
private fun VaultHeader(indexedCount: Int, onOpenRules: () -> Unit, onOpenIndex: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.l, bottom = Space.s),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(text = "PADLÁS")
        PadlasTextButton(
            text = if (indexedCount == 0) "Beolvasás" else "Szabályok",
            onClick = if (indexedCount == 0) onOpenIndex else onOpenRules,
        )
    }
}

@Composable
private fun Legend(types: List<MediaTypeAggregate>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        types.take(Spectrum.size).forEachIndexed { index, type ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .drawBehind {
                            drawCircle(color = Spectrum[index % Spectrum.size])
                        },
                )
                Spacer(Modifier.size(Space.xs))
                Text(
                    text = type.mediaType.hungarian(),
                    style = PadlasTheme.type.micro,
                    color = Ink.Dust,
                )
            }
        }
    }
}

@Composable
private fun OneDriveWarning() {
    PadlasCard(background = Ink.Raised, modifier = Modifier.fillMaxWidth()) {
        Column {
            SectionLabel(text = "FIGYELMEZTETÉS")
            Spacer(Modifier.height(Space.s))
            Text(
                text = "A OneDrive szinkron aktívnak tűnik. Ilyenkor a helyi törlés a felhőből is törölhet. " +
                    "Kapcsold ki a Samsung Galéria szinkront, mielőtt bármit kidobsz.",
                style = PadlasTheme.type.body,
                color = Ink.Paper,
            )
        }
    }
}

@Composable
private fun AccessRow(
    allFiles: Boolean,
    usageStats: Boolean,
    onAllFiles: () -> Unit,
    onUsageStats: () -> Unit,
) {
    PadlasCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            SectionLabel(text = "HOZZÁFÉRÉS")
            if (!allFiles) {
                Text(
                    text = "A térképhez teljes fájlhozzáférés kell.",
                    style = PadlasTheme.type.body,
                    color = Ink.Faded,
                )
                PadlasTextButton(text = "Megadom", onClick = onAllFiles)
            }
            if (!usageStats) {
                Text(
                    text = "Az app/cache bontáshoz használati hozzáférés kell.",
                    style = PadlasTheme.type.body,
                    color = Ink.Faded,
                )
                PadlasTextButton(text = "Megadom", onClick = onUsageStats)
            }
        }
    }
}

@Composable
private fun TrashRow(mediaStoreBytes: Long, samsungBytes: Long, onClick: () -> Unit) {
    PadlasCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "Kuka · ${Format.bytes(mediaStoreBytes + samsungBytes)}",
                style = PadlasTheme.type.body,
                color = Ink.Paper,
            )
            Text(
                text = "A kukában lévő fájl még helyet foglal.",
                style = PadlasTheme.type.label,
                color = Ink.Dust,
            )
        }
    }
}

private fun List<MediaTypeAggregate>.toSegments(): List<GaugeSegment> =
    mapIndexed { index, type ->
        GaugeSegment(
            label = type.mediaType.hungarian(),
            bytes = type.totalBytes,
            color = Spectrum[index % Spectrum.size],
        )
    }

private fun String.hungarian(): String = when (this) {
    "image" -> "fotó"
    "video" -> "videó"
    "audio" -> "hang"
    else -> "egyéb"
}

private fun VaultAction.title(): String = when (kind) {
    VaultActionKind.DEDUP -> "${Format.count(count)} duplikátum"
    VaultActionKind.VIDEO -> "${Format.count(count)} nagy videó"
    VaultActionKind.ARCHIVE -> "${Format.count(count)} kép egy évnél régebbi"
    VaultActionKind.DISCARD -> "${Format.count(count)} efemer felvétel"
}

private fun VaultAction.detail(): String = when (kind) {
    VaultActionKind.DEDUP -> "${Format.bytes(bytes)} visszanyerhető"
    VaultActionKind.VIDEO -> "~${Format.bytes(bytes)} tömöríthető"
    VaultActionKind.ARCHIVE -> "${Format.bytes(bytes)} padlásra vihető"
    VaultActionKind.DISCARD -> "${Format.bytes(bytes)} kidobható"
}

private fun VaultAction.direction(): ActionDirection = when (kind) {
    VaultActionKind.DEDUP -> ActionDirection.DOWN
    VaultActionKind.VIDEO -> ActionDirection.COMPRESS
    VaultActionKind.ARCHIVE -> ActionDirection.UP
    VaultActionKind.DISCARD -> ActionDirection.DOWN
}
