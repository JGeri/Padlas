package hu.padlas.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.Chip
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.IconBubble
import hu.padlas.core.design.component.LinearMeter
import hu.padlas.core.design.component.ListRow
import hu.padlas.core.design.component.PadlasIconButton
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.SectionHeader
import hu.padlas.core.design.component.SelectionMark
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.theme.CategoryColor
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space

internal fun LazyListScope.typesSection(types: TypesUi, onCategory: (FileCategory) -> Unit) {
    item(key = "types-head") {
        SectionHeader(title = "Kategóriák", modifier = Modifier.then(itemMotion()).padding(horizontal = Space.page, vertical = Space.s))
    }
    val total = types.totalBytes.coerceAtLeast(1L)
    itemsIndexed(types.categories, key = { _, it -> "cat-" + it.category.name }) { index, category ->
        GroupedRow(
            index = index,
            count = types.categories.size,
            onClick = { onCategory(category.category) },
            modifier = Modifier.then(itemMotion()).padding(horizontal = Space.page),
        ) {
            IconBubble(icon = category.category.icon, tint = category.category.color)
            Spacer(Modifier.width(Space.m))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = category.category.label,
                        style = PadlasTheme.type.bodyStrong,
                        color = Ink.Text,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = Format.bytes(category.bytes), style = PadlasTheme.type.numeric, color = Ink.Text)
                }
                Row {
                    Text(
                        text = "${Format.count(category.count)} fájl",
                        style = PadlasTheme.type.caption,
                        color = Ink.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = Format.percent(category.bytes.toFloat() / total),
                        style = PadlasTheme.type.caption,
                        color = Ink.TextTertiary,
                    )
                }
                Spacer(Modifier.height(Space.s))
                LinearMeter(fraction = category.bytes.toFloat() / total, color = category.category.color, height = 4.dp)
            }
            Spacer(Modifier.width(Space.xs))
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Ink.TextTertiary, modifier = Modifier.size(20.dp))
        }
    }
    if (types.extensions.isEmpty()) return
    item(key = "ext-head") {
        SectionHeader(
            title = "Kiterjesztések",
            modifier = Modifier.then(itemMotion()).padding(start = Space.page, end = Space.page, top = Space.xl, bottom = Space.s),
        )
    }
    val largest = types.extensions.first().bytes.coerceAtLeast(1L)
    itemsIndexed(types.extensions, key = { _, it -> "ext-" + it.extension }) { index, extension ->
        GroupedRow(
            index = index,
            count = types.extensions.size,
            dividerInset = 82.dp,
            modifier = Modifier.then(itemMotion()).padding(horizontal = Space.page),
        ) {
            ExtensionBadge(extension)
            Spacer(Modifier.width(Space.m))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${Format.count(extension.count)} fájl",
                        style = PadlasTheme.type.caption,
                        color = Ink.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = Format.bytes(extension.bytes), style = PadlasTheme.type.numeric, color = Ink.Text)
                }
                Spacer(Modifier.height(6.dp))
                LinearMeter(
                    fraction = extension.bytes.toFloat() / largest,
                    color = extension.category.color.copy(alpha = 0.85f),
                    height = 4.dp,
                )
            }
        }
    }
}

@Composable
private fun ExtensionBadge(extension: ExtensionTotal) {
    val label = if (extension.extension.isEmpty()) "nincs" else "." + extension.extension
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 56.dp)
            .clip(Shapes.small)
            .background(extension.category.color.copy(alpha = 0.16f))
            .padding(horizontal = Space.s, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = PadlasTheme.type.label,
            color = extension.category.color,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

private val filterOrder = listOf(
    null,
    FileCategory.VIDEOS,
    FileCategory.IMAGES,
    FileCategory.AUDIO,
    FileCategory.DOCUMENTS,
    FileCategory.ARCHIVES,
    FileCategory.INSTALLERS,
    FileCategory.OTHER,
)

internal fun LazyListScope.filesSection(
    files: FilesUi,
    selection: SelectionUi,
    rootPath: String,
    animate: Boolean,
    onFilter: (FileCategory?) -> Unit,
    onCleanup: (Cleanup) -> Unit,
    onCloseCleanup: () -> Unit,
    onToggle: (StorageItem) -> Unit,
    onSelectAll: (List<StorageItem>) -> Unit,
    onClearSelection: () -> Unit,
) {
    val cleanup = files.cleanup
    if (cleanup != null) {
        cleanupSection(files, cleanup, selection, rootPath, animate, onCloseCleanup, onToggle, onSelectAll, onClearSelection)
        return
    }
    item(key = "smart-head") {
        SectionHeader(title = "Okos takarítás", modifier = Modifier.then(itemMotion()).padding(horizontal = Space.page, vertical = Space.s))
    }
    item(key = "smart-rows") {
        Column(
            modifier = Modifier.then(itemMotion()).padding(horizontal = Space.page),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            files.shortcuts.forEach { shortcut ->
                val style = shortcutStyle(shortcut.kind)
                ListRow(
                    title = shortcut.kind.title,
                    subtitle = shortcutSubtitle(shortcut),
                    icon = style.first,
                    iconTint = style.second,
                    value = if (shortcut.kind == Cleanup.EMPTY_DIRS || shortcut.count == 0) null else Format.bytes(shortcut.bytes),
                    valueColor = Ink.Text,
                    onClick = { onCleanup(shortcut.kind) },
                )
            }
        }
    }
    item(key = "largest-head") {
        SectionHeader(
            title = "Legnagyobb fájlok",
            modifier = Modifier.then(itemMotion()).padding(start = Space.page, end = Space.page, top = Space.xl),
        )
    }
    item(key = "filters") {
        Row(
            modifier = Modifier
                .then(itemMotion())
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Space.page, vertical = Space.m),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            filterOrder.forEach { category ->
                Chip(
                    text = category?.label ?: "Mind",
                    selected = files.filter == category,
                    onClick = { onFilter(category) },
                    icon = category?.icon,
                )
            }
        }
    }
    if (files.items.isEmpty()) {
        item(key = "files-empty") {
            EmptyState(
                title = "Nincs ilyen fájl",
                text = "Ebben a kategóriában semmi nem foglal helyet.",
                icon = Icons.Rounded.DoneAll,
                tint = Ink.Success,
                modifier = Modifier.then(itemMotion()),
            )
        }
        return
    }
    fileRows(files.items, selection, rootPath, animate, onToggle)
}

private fun LazyListScope.cleanupSection(
    files: FilesUi,
    cleanup: Cleanup,
    selection: SelectionUi,
    rootPath: String,
    animate: Boolean,
    onClose: () -> Unit,
    onToggle: (StorageItem) -> Unit,
    onSelectAll: (List<StorageItem>) -> Unit,
    onClearSelection: () -> Unit,
) {
    item(key = "cleanup-head") {
        Column(modifier = Modifier.then(itemMotion()).padding(start = Space.page, end = Space.page, top = Space.s, bottom = Space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PadlasIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Vissza az okos takarításhoz",
                    onClick = onClose,
                    background = Ink.Surface,
                )
                Spacer(Modifier.width(Space.m))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cleanup.title,
                        style = PadlasTheme.type.title,
                        color = Ink.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = cleanupSummary(cleanup, files.items),
                        style = PadlasTheme.type.caption,
                        color = Ink.TextSecondary,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(Space.m))
            Text(text = cleanup.description, style = PadlasTheme.type.body, color = Ink.TextSecondary)
            if (files.items.isNotEmpty()) {
                val all = files.items.all { it.path in selection }
                PadlasTextButton(
                    text = if (all) "Kijelölés megszüntetése" else "Mind kijelölése",
                    onClick = { if (all) onClearSelection() else onSelectAll(files.items) },
                    icon = Icons.Rounded.DoneAll,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
        }
    }
    if (files.items.isEmpty()) {
        item(key = "cleanup-empty") {
            EmptyState(
                title = "Itt tiszta minden",
                text = "Nincs mit takarítani ebben a csoportban.",
                icon = Icons.Rounded.DoneAll,
                tint = Ink.Success,
                modifier = Modifier.then(itemMotion()),
            )
        }
        return
    }
    fileRows(files.items, selection, rootPath, animate, onToggle)
}

private fun LazyListScope.fileRows(
    items: List<StorageItem>,
    selection: SelectionUi,
    rootPath: String,
    animate: Boolean,
    onToggle: (StorageItem) -> Unit,
) {
    itemsIndexed(items, key = { _, it -> "file-" + it.path }) { index, item ->
        FileRow(
            item = item,
            index = index,
            count = items.size,
            selected = item.path in selection,
            rootPath = rootPath,
            animate = animate,
            onToggle = { onToggle(item) },
        )
    }
}

@Composable
private fun LazyItemScope.FileRow(
    item: StorageItem,
    index: Int,
    count: Int,
    selected: Boolean,
    rootPath: String,
    animate: Boolean,
    onToggle: () -> Unit,
) {
    GroupedRow(
        index = index,
        count = count,
        selected = selected,
        onClick = onToggle,
        dividerInset = 74.dp,
        modifier = Modifier
            .then(if (animate) Modifier.animateItem(Motion.settle(), Motion.settle(), Motion.settle()) else Modifier)
            .padding(horizontal = Space.page),
    ) {
        ItemThumb(item, 48.dp)
        Spacer(Modifier.width(Space.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = PadlasTheme.type.bodyStrong,
                color = Ink.Text,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            val date = TimeText.date(item.modified)
            Text(
                text = displayPath(item.parentPath, rootPath) + if (date.isEmpty()) "" else " · $date",
                style = PadlasTheme.type.caption,
                color = Ink.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.s))
        Text(
            text = if (item.isDirectory && item.bytes == 0L) "Üres" else Format.bytes(item.bytes),
            style = PadlasTheme.type.numeric,
            color = if (selected) Ink.Text else Ink.TextSecondary,
            maxLines = 1,
        )
        Spacer(Modifier.width(Space.m))
        SelectionMark(selected = selected)
    }
}

private fun shortcutStyle(kind: Cleanup): Pair<ImageVector, Color> = when (kind) {
    Cleanup.INSTALLERS -> Icons.Rounded.InstallMobile to CategoryColor.Cloud
    Cleanup.OLD_DOWNLOADS -> Icons.Rounded.Download to Ink.Warning
    Cleanup.THUMBNAILS -> Icons.Rounded.Layers to CategoryColor.Photos
    Cleanup.EMPTY_DIRS -> Icons.Rounded.FolderOpen to CategoryColor.Other
}

private fun shortcutSubtitle(shortcut: CleanupSummary): String = when {
    shortcut.count == 0 -> "Nincs ilyen"
    shortcut.kind == Cleanup.EMPTY_DIRS -> "${Format.count(shortcut.count)} mappa"
    shortcut.kind == Cleanup.THUMBNAILS -> "${Format.count(shortcut.count)} mappa"
    else -> "${Format.count(shortcut.count)} fájl"
}

private fun cleanupSummary(cleanup: Cleanup, items: List<StorageItem>): String = when {
    items.isEmpty() -> "Nincs ilyen elem"
    cleanup == Cleanup.EMPTY_DIRS -> "${Format.count(items.size)} mappa"
    else -> {
        val dirs = items.count { it.isDirectory }
        "${describeCount(items.size - dirs, dirs)} · ${Format.bytes(items.sumOf { it.bytes })}"
    }
}
