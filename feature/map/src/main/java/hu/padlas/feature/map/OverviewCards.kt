package hu.padlas.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.ButtonSize
import hu.padlas.core.design.component.IconBubble
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.Pill
import hu.padlas.core.design.component.ProgressRing
import hu.padlas.core.design.component.RollingNumber
import hu.padlas.core.design.component.SegmentBar
import hu.padlas.core.design.theme.CategoryColor
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space
import kotlin.math.exp

@Composable
internal fun AllFilesGate(onGrant: () -> Unit, modifier: Modifier = Modifier) {
    GateCard(
        icon = Icons.Rounded.FolderOpen,
        tint = Ink.Accent,
        title = "Teljes fájlhozzáférés kell",
        text = "A tárhely-elemzőnek minden mappába be kell látnia, hogy megmutassa, " +
            "mi foglalja a helyet a telefonodon.",
        points = listOf(
            Icons.Rounded.PieChart to "Mappák és fájlok mérete egy pillantással",
            Icons.Rounded.DeleteOutline to "Törlés csak a te jóváhagyásoddal",
            Icons.Rounded.Lock to "Minden a telefonon marad, semmi nem kerül fel sehova",
        ),
        action = "Hozzáférés megadása",
        footnote = "A megnyíló beállításban kapcsold be a Padlásnál.",
        onAction = onGrant,
        modifier = modifier,
    )
}

@Composable
internal fun UsageGate(onGrant: () -> Unit, modifier: Modifier = Modifier) {
    GateCard(
        icon = Icons.Rounded.Apps,
        tint = CategoryColor.Apps,
        title = "Használati hozzáférés kell",
        text = "Az appok méretét és gyorsítótárát a rendszer csak ezzel az engedéllyel árulja el.",
        points = listOf(
            Icons.Rounded.PieChart to "Program, adat és gyorsítótár appokra bontva",
            Icons.Rounded.Lock to "Csak méreteket olvasunk, a használati szokásaidat nem",
        ),
        action = "Hozzáférés megadása",
        footnote = "A listában válaszd a Padlást, és kapcsold be.",
        onAction = onGrant,
        modifier = modifier,
    )
}

@Composable
private fun GateCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    text: String,
    points: List<Pair<ImageVector, String>>,
    action: String,
    footnote: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PadlasCard(modifier = modifier.fillMaxWidth(), contentPadding = Space.xl) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconBubble(icon = icon, tint = tint, size = 64.dp, iconSize = 30.dp)
            Spacer(Modifier.height(Space.l))
            Text(text = title, style = PadlasTheme.type.title, color = Ink.Text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Space.s))
            Text(text = text, style = PadlasTheme.type.body, color = Ink.TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Space.xl))
            Column(verticalArrangement = Arrangement.spacedBy(Space.m), modifier = Modifier.fillMaxWidth()) {
                points.forEach { (pointIcon, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(icon = pointIcon, tint = Ink.TextSecondary, background = Ink.SurfaceHigh, size = 32.dp, iconSize = 16.dp)
                        Spacer(Modifier.width(Space.m))
                        Text(text = label, style = PadlasTheme.type.label, color = Ink.Text)
                    }
                }
            }
            Spacer(Modifier.height(Space.xl))
            PadlasButton(text = action, onClick = onAction, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.m))
            Text(
                text = footnote,
                style = PadlasTheme.type.caption,
                color = Ink.TextTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun NoResultCard(onStart: () -> Unit, modifier: Modifier = Modifier) {
    PadlasCard(modifier = modifier.fillMaxWidth(), contentPadding = Space.xl) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            IconBubble(icon = Icons.Rounded.DataUsage, size = 64.dp, iconSize = 30.dp)
            Spacer(Modifier.height(Space.l))
            Text(text = "Még nincs elemzés", style = PadlasTheme.type.title, color = Ink.Text)
            Spacer(Modifier.height(Space.xs))
            Text(
                text = "Egy gyors bejárás megmutatja, mely mappák és fájlok foglalják a helyet.",
                style = PadlasTheme.type.body,
                color = Ink.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.l))
            PadlasButton(text = "Elemzés indítása", onClick = onStart, size = ButtonSize.Medium)
        }
    }
}

/**
 * Reads the progress itself, so the ten updates a second recompose this card and nothing
 * else on the screen.
 */
@Composable
internal fun ScanProgressCard(
    progressState: State<ScanProgress?>,
    rootPath: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = progressState.value ?: return
    PadlasCard(modifier = modifier.fillMaxWidth(), contentPadding = 20.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(fraction = scanFraction(progress), size = 60.dp, stroke = 5.dp) {
                    Icon(Icons.Rounded.TravelExplore, contentDescription = null, tint = Ink.Accent, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(Space.l))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Elemzés folyamatban", style = PadlasTheme.type.title, color = Ink.Text)
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RollingNumber(value = Format.count(progress.files), style = PadlasTheme.type.numeric, color = Ink.Text)
                        Text(
                            text = " fájl · ${Format.bytes(progress.bytes)}",
                            style = PadlasTheme.type.numeric,
                            color = Ink.TextSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.m))
            Text(
                text = if (progress.currentPath.isEmpty()) "Indul…" else displayPath(progress.currentPath, rootPath),
                style = PadlasTheme.type.caption,
                color = Ink.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Spacer(Modifier.height(Space.m))
            SegmentBar(
                segments = FileCategory.all.map { progress.categoryBytes.getOrElse(it.ordinal) { 0L } to it.color },
                total = progress.bytes.coerceAtLeast(1L),
                height = 8.dp,
            )
            Spacer(Modifier.height(Space.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Közben nyugodtan válthatsz másik fülre.",
                    style = PadlasTheme.type.micro,
                    color = Ink.TextTertiary,
                    modifier = Modifier.weight(1f),
                )
                PadlasTextButton(text = "Leállítás", onClick = onCancel, color = Ink.TextSecondary)
            }
        }
    }
}

/**
 * Without a reliable estimate the ring follows a curve that keeps moving but slows down,
 * and it never closes before the walk does: a full ring that keeps working is a lie.
 */
private fun scanFraction(progress: ScanProgress): Float {
    val raw = if (progress.expectedBytes > 0L) {
        progress.bytes.toDouble() / progress.expectedBytes
    } else {
        1.0 - exp(-progress.bytes / UNKNOWN_SCALE)
    }
    return raw.toFloat().coerceIn(0.02f, 0.96f)
}

private const val UNKNOWN_SCALE = 24.0 * 1024 * 1024 * 1024

@Composable
internal fun SummaryCard(summary: SummaryUi, modifier: Modifier = Modifier) {
    PadlasCard(modifier = modifier.fillMaxWidth(), contentPadding = 20.dp) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Foglalt tárhely", style = PadlasTheme.type.caption, color = Ink.TextSecondary)
                    Row(verticalAlignment = Alignment.Bottom) {
                        RollingNumber(
                            value = Format.bytesValue(summary.usedBytes),
                            style = PadlasTheme.type.displaySmall,
                            color = Ink.Text,
                        )
                        Text(
                            text = " " + Format.bytesUnit(summary.usedBytes),
                            style = PadlasTheme.type.title,
                            color = Ink.TextSecondary,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Text(
                        text = "Összesen ${Format.bytes(summary.totalBytes)}",
                        style = PadlasTheme.type.caption,
                        color = Ink.TextTertiary,
                    )
                }
                Pill(
                    text = "${Format.bytes(summary.freeBytes)} szabad",
                    color = Ink.Success,
                    background = Ink.SuccessSoft,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Spacer(Modifier.height(Space.l))
            SegmentBar(
                segments = summary.slices.map { it.bytes to it.color },
                total = summary.totalBytes,
                height = 14.dp,
            )
            Spacer(Modifier.height(Space.l))
            val legend = summary.slices.map { Triple(it.label, it.bytes, it.color) } +
                Triple("Szabad", summary.freeBytes, Ink.SurfaceHighest)
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                legend.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                        pair.forEach { (label, bytes, color) ->
                            LegendCell(label = label, value = Format.bytes(bytes), color = color, modifier = Modifier.weight(1f))
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** The legend dot with the figure under its label, so a long label never squeezes the number. */
@Composable
private fun LegendCell(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(10.dp)
                .clip(Shapes.pill)
                .background(color),
        )
        Spacer(Modifier.width(Space.s))
        Column {
            Text(
                text = label,
                style = PadlasTheme.type.caption,
                color = Ink.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = value, style = PadlasTheme.type.numeric, color = Ink.Text, maxLines = 1)
        }
    }
}
