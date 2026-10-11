package hu.padlas.feature.map

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.LinearMeter
import hu.padlas.core.design.component.PadlasIconButton
import hu.padlas.core.design.component.SelectionMark
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.finite
import hu.padlas.core.design.motion.itemMotion
import hu.padlas.core.design.motion.popIn
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space

private val MapShape = RoundedCornerShape(16.dp)

internal fun LazyListScope.directoryHeader(
    directory: DirectoryUi,
    onJump: (String) -> Unit,
    onUp: () -> Unit,
) {
    item(key = "crumbs") {
        Breadcrumbs(
            crumbs = directory.crumbs,
            onJump = onJump,
            modifier = Modifier.then(itemMotion()).padding(top = Space.s),
        )
    }
    item(key = "dir-title") {
        val reduced = LocalReducedMotion.current
        Row(
            modifier = Modifier
                .then(itemMotion())
                .fillMaxWidth()
                .padding(horizontal = Space.page, vertical = Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The title slides the way the map zooms: in from the right going deeper, in
            // from the left coming back up.
            AnimatedContent(
                targetState = directory,
                contentKey = { it.path },
                transitionSpec = {
                    val deeper = targetState.path.startsWith(initialState.path + "/")
                    val shift = if (deeper) 1 else -1
                    (slideInHorizontally(finite(reduced)) { shift * it / 4 } + fadeIn(finite(reduced))) togetherWith
                        (slideOutHorizontally(finite(reduced)) { -shift * it / 4 } + fadeOut(finite(reduced))) using
                        SizeTransform(clip = false)
                },
                modifier = Modifier.weight(1f),
                label = "dirTitle",
            ) { shown ->
                Column {
                    Text(
                        text = shown.label,
                        style = PadlasTheme.type.title,
                        color = Ink.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${Format.bytes(shown.bytes)} · ${Format.count(shown.fileCount)} fájl",
                        style = PadlasTheme.type.caption,
                        color = Ink.TextSecondary,
                        maxLines = 1,
                    )
                }
            }
            AnimatedVisibility(
                visible = !directory.isRoot,
                enter = popIn(reduced),
                exit = scaleOut(finite(reduced), targetScale = 0.6f) + fadeOut(finite(reduced)),
            ) {
                PadlasIconButton(
                    icon = Icons.Rounded.ArrowUpward,
                    contentDescription = "Egy szinttel feljebb",
                    onClick = onUp,
                    background = Ink.Surface,
                )
            }
        }
    }
}

internal fun LazyListScope.mapSection(
    directory: DirectoryUi,
    focus: Focus?,
    onTap: (MapTile) -> Unit,
) {
    if (directory.plan.items.isEmpty() && directory.plan.restBytes <= 0L) {
        item(key = "map-empty") {
            EmptyState(
                title = "Üres mappa",
                text = "Ebben a mappában nincs semmi, ami helyet foglalna.",
                icon = Icons.Rounded.FolderOpen,
                tint = Ink.TextSecondary,
                modifier = Modifier.then(itemMotion()),
            )
        }
        return
    }
    item(key = "map") {
        val selectedKey = when (focus) {
            is Focus.Item -> focus.item.path
            is Focus.Rest -> REST_KEY
            null -> null
        }
        Treemap(
            directory = directory,
            selectedKey = selectedKey,
            onTap = onTap,
            modifier = Modifier
                .then(itemMotion())
                .padding(horizontal = Space.page)
                .fillMaxWidth()
                .aspectRatio(MAP_ASPECT)
                .clip(MapShape)
                .background(Ink.Surface),
        )
    }
    item(key = "map-legend") {
        MapLegend(directory, modifier = Modifier.then(itemMotion()))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapLegend(directory: DirectoryUi, modifier: Modifier = Modifier) {
    val total = directory.bytes.coerceAtLeast(1L)
    val present = FileCategory.all
        .map { it to directory.categoryBytes.getOrElse(it.ordinal) { 0L } }
        .filter { it.second > 0L }
        .sortedByDescending { it.second }
    Column(modifier = modifier.padding(horizontal = Space.page, vertical = Space.m)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            present.forEach { (category, bytes) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(Shapes.pill)
                            .background(category.color),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(text = category.label, style = PadlasTheme.type.caption, color = Ink.TextSecondary)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = Format.percent(bytes.toFloat() / total),
                        style = PadlasTheme.type.numeric,
                        color = Ink.Text,
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.s))
        Text(
            text = "Koppints egy csempére a részletekért, egy mappára még egyszer a megnyitáshoz.",
            style = PadlasTheme.type.micro,
            color = Ink.TextTertiary,
        )
    }
}

internal fun LazyListScope.foldersSection(
    directory: DirectoryUi,
    selection: SelectionUi,
    animate: Boolean,
    onClick: (StorageItem) -> Unit,
    onLongClick: (StorageItem) -> Unit,
) {
    if (directory.entries.isEmpty()) {
        item(key = "folders-empty") {
            EmptyState(
                title = "Üres mappa",
                text = "Ebben a mappában nincs semmi.",
                icon = Icons.Rounded.FolderOpen,
                tint = Ink.TextSecondary,
                modifier = Modifier.then(itemMotion()),
            )
        }
        return
    }
    if (!selection.active) {
        item(key = "folders-hint") {
            Text(
                text = "Hosszan nyomva több elemet is kijelölhetsz.",
                style = PadlasTheme.type.micro,
                color = Ink.TextTertiary,
                modifier = Modifier.then(itemMotion()).padding(start = Space.page, end = Space.page, bottom = Space.s),
            )
        }
    }
    val total = directory.bytes.coerceAtLeast(1L)
    val count = directory.entries.size
    itemsIndexed(directory.entries, key = { _, item -> item.path }) { index, item ->
        FolderRow(
            item = item,
            share = item.bytes.toFloat() / total,
            index = index,
            count = count,
            selected = item.path in selection,
            selecting = selection.active,
            animate = animate,
            onClick = { onClick(item) },
            onLongClick = { onLongClick(item) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LazyItemScope.FolderRow(
    item: StorageItem,
    share: Float,
    index: Int,
    count: Int,
    selected: Boolean,
    selecting: Boolean,
    animate: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    GroupedRow(
        index = index,
        count = count,
        selected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier
            .then(if (animate) Modifier.animateItem(Motion.settle(), Motion.settle(), Motion.settle()) else Modifier)
            .padding(horizontal = Space.page),
    ) {
        if (selecting) {
            SelectionMark(selected = selected)
            Spacer(Modifier.width(Space.m))
        }
        ItemThumb(item, 40.dp)
        Spacer(Modifier.width(Space.m))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    style = PadlasTheme.type.bodyStrong,
                    color = Ink.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Space.s))
                Text(text = Format.bytes(item.bytes), style = PadlasTheme.type.numeric, color = Ink.Text, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rowSubtitle(item),
                    style = PadlasTheme.type.caption,
                    color = Ink.TextSecondary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(text = Format.percent(share), style = PadlasTheme.type.caption, color = Ink.TextTertiary, maxLines = 1)
            }
            Spacer(Modifier.height(Space.s))
            LinearMeter(
                fraction = share,
                color = if (item.bytes > 0L) item.category.color else Color.Transparent,
                height = 3.dp,
            )
        }
    }
}

private fun rowSubtitle(item: StorageItem): String = when {
    item.isDirectory && item.fileCount == 0 -> "Nincs benne fájl"
    item.isDirectory -> "${Format.count(item.fileCount)} fájl"
    else -> listOf(FileTypes.extensionOf(item.name).uppercase(), TimeText.date(item.modified))
        .filter { it.isNotEmpty() }
        .joinToString(" · ")
}

private const val MAP_ASPECT = 0.9f
