package hu.padlas.feature.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.ButtonSize
import hu.padlas.core.design.component.ButtonStyle
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.IconBubble
import hu.padlas.core.design.component.LegendItem
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.SectionHeader
import hu.padlas.core.design.component.SegmentBar
import hu.padlas.core.design.motion.itemMotion
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space

internal typealias IconLoader = suspend (packageName: String, sizePx: Int) -> ImageBitmap?

internal fun LazyListScope.appsSection(
    apps: AppsUi,
    iconLoader: IconLoader,
    onGrant: () -> Unit,
    onOpenApp: (String) -> Unit,
    onNextStop: () -> Unit,
    onRestartTour: () -> Unit,
) {
    if (!apps.hasAccess) {
        item(key = "apps-gate") {
            UsageGate(onGrant = onGrant, modifier = Modifier.then(itemMotion()).padding(horizontal = Space.page, vertical = Space.s))
        }
        return
    }
    if (apps.apps.isEmpty()) {
        item(key = "apps-loading") {
            EmptyState(
                title = stringResource(if (apps.loading) R.string.map_apps_measuring else R.string.map_apps_nothing),
                text = stringResource(if (apps.loading) R.string.map_apps_measuring_text else R.string.map_apps_no_sizes),
                icon = Icons.Rounded.Schedule,
                tint = Ink.TextSecondary,
                modifier = Modifier.then(itemMotion()),
            )
        }
        return
    }
    if (apps.cacheStops.isNotEmpty()) {
        item(key = "cache-tour") {
            CacheTour(
                apps = apps,
                iconLoader = iconLoader,
                onOpen = onOpenApp,
                onNext = onNextStop,
                onRestart = onRestartTour,
                modifier = Modifier.then(itemMotion()).padding(horizontal = Space.page, vertical = Space.s),
            )
        }
    }
    item(key = "apps-head") {
        Column(modifier = Modifier.then(itemMotion()).padding(start = Space.page, end = Space.page, top = Space.l, bottom = Space.m)) {
            SectionHeader(title = stringResource(R.string.map_apps_by_size))
            Text(
                text = pluralStringResource(
                    R.plurals.map_apps_count,
                    apps.apps.size,
                    Format.count(apps.apps.size),
                    Format.bytes(apps.apps.sumOf { it.totalBytes }),
                ),
                style = PadlasTheme.type.caption,
                color = Ink.TextSecondary,
            )
            Spacer(Modifier.height(Space.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                LegendItem(label = stringResource(R.string.map_app_part_program), value = "", color = AppPartColor.Program)
                LegendItem(label = stringResource(R.string.map_app_part_data), value = "", color = AppPartColor.Data)
                LegendItem(label = stringResource(R.string.map_app_part_cache), value = "", color = AppPartColor.Cache)
            }
        }
    }
    itemsIndexed(apps.apps, key = { _, it -> "app-" + it.packageName }) { index, app ->
        GroupedRow(
            index = index,
            count = apps.apps.size,
            onClick = { onOpenApp(app.packageName) },
            modifier = Modifier.then(itemMotion()).padding(horizontal = Space.page),
        ) {
            AppIcon(app.packageName, iconLoader, 40.dp)
            Spacer(Modifier.width(Space.m))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.label,
                        style = PadlasTheme.type.bodyStrong,
                        color = Ink.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Space.s))
                    Text(text = Format.bytes(app.totalBytes), style = PadlasTheme.type.numeric, color = Ink.Text)
                }
                Spacer(Modifier.height(6.dp))
                SegmentBar(
                    segments = listOf(
                        app.appBytes to AppPartColor.Program,
                        app.dataBytes to AppPartColor.Data,
                        app.cacheBytes to AppPartColor.Cache,
                    ),
                    total = app.totalBytes,
                    height = 5.dp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${Format.bytes(app.appBytes)} · ${Format.bytes(app.dataBytes)} · ${Format.bytes(app.cacheBytes)}",
                    style = PadlasTheme.type.micro,
                    color = Ink.TextTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Other apps' caches cannot be cleared from here, so the tour does the next best thing:
 * it opens each app's settings page in turn and remembers where the user is.
 */
@Composable
private fun CacheTour(
    apps: AppsUi,
    iconLoader: IconLoader,
    onOpen: (String) -> Unit,
    onNext: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stops = apps.cacheStops
    val current = stops.getOrNull(apps.tourIndex)
    PadlasCard(modifier = modifier.fillMaxWidth(), contentPadding = 20.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(icon = Icons.Rounded.CleaningServices, tint = Ink.Warning, size = 44.dp, iconSize = 22.dp)
                Spacer(Modifier.width(Space.m))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.map_cache_tour_title), style = PadlasTheme.type.title, color = Ink.Text)
                    Text(
                        text = pluralStringResource(
                            R.plurals.map_cache_tour_summary,
                            stops.size,
                            Format.bytes(stops.sumOf { it.cacheBytes }),
                            Format.count(stops.size),
                        ),
                        style = PadlasTheme.type.caption,
                        color = Ink.TextSecondary,
                    )
                }
            }
            Spacer(Modifier.height(Space.m))
            Text(
                text = stringResource(R.string.map_cache_tour_text),
                style = PadlasTheme.type.body,
                color = Ink.TextSecondary,
            )
            Spacer(Modifier.height(Space.l))
            if (current == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DoneAll, contentDescription = null, tint = Ink.Success, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Space.s))
                    Text(
                        text = stringResource(R.string.map_cache_tour_done),
                        style = PadlasTheme.type.bodyStrong,
                        color = Ink.Text,
                        modifier = Modifier.weight(1f),
                    )
                    PadlasTextButton(text = stringResource(R.string.map_cache_tour_restart), onClick = onRestart)
                }
                return@Column
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Shapes.tile)
                    .background(Ink.SurfaceHigh)
                    .padding(Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(current.packageName, iconLoader, 44.dp)
                Spacer(Modifier.width(Space.m))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = current.label,
                        style = PadlasTheme.type.bodyStrong,
                        color = Ink.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.map_cache_amount, Format.bytes(current.cacheBytes)),
                        style = PadlasTheme.type.caption,
                        color = Ink.Warning,
                    )
                }
                Text(
                    text = "${Format.count(apps.tourIndex + 1)} / ${Format.count(stops.size)}",
                    style = PadlasTheme.type.numeric,
                    color = Ink.TextTertiary,
                )
            }
            Spacer(Modifier.height(Space.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                PadlasButton(
                    text = stringResource(R.string.map_open),
                    onClick = { onOpen(current.packageName) },
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    size = ButtonSize.Medium,
                    modifier = Modifier.weight(1f),
                )
                PadlasButton(
                    text = stringResource(R.string.map_next),
                    onClick = onNext,
                    style = ButtonStyle.Secondary,
                    size = ButtonSize.Medium,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(Space.s))
            Text(
                text = stringResource(R.string.map_cache_tour_path),
                style = PadlasTheme.type.micro,
                color = Ink.TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AppIcon(packageName: String, iconLoader: IconLoader, size: Dp) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, packageName, px) { value = iconLoader(packageName, px) }
    Box(
        modifier = Modifier
            .size(size)
            .clip(Shapes.small)
            .background(Ink.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(bitmap = image, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Rounded.Apps, contentDescription = null, tint = Ink.TextTertiary, modifier = Modifier.size(size * 0.5f))
        }
    }
}
