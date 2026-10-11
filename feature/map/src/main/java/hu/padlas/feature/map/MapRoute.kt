package hu.padlas.feature.map

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.NumericText
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.PadlasTopBar
import hu.padlas.core.design.component.SectionLabel
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space
import hu.padlas.core.design.theme.Spectrum

/**
 * The map runs before anything else, because it is entirely possible that the photos are
 * not the problem and eighteen gigabytes of chat cache are.
 */
@Composable
fun MapRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: MapViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = Space.l),
    ) {
        PadlasTopBar(
            title = "TÉRKÉP",
            onBack = { if (!viewModel.up()) onBack() },
            action = { PadlasTextButton(text = "Újramérés", onClick = viewModel::refresh) },
        )

        val map = state.map
        val node = state.node
        if (map == null || node == null) {
            EmptyState(text = "Mérés folyamatban.")
            return@Column
        }

        Breadcrumb(chain = state.chain, onJump = viewModel::jumpTo)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Space.s),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = node.label, style = PadlasTheme.type.title, color = Ink.Text)
            NumericText(
                text = "${Format.bytes(node.sizeBytes)} · ${Format.bytes(map.freeBytes)} szabad",
            )
        }

        if (node.children.isEmpty()) {
            EmptyState(text = "Ez a szint már nem bontható tovább.")
        } else {
            Treemap(
                node = node,
                onDrill = viewModel::drill,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }

        CacheTour(
            stops = map.cacheStops,
            index = state.cacheTourIndex,
            hasUsageStats = map.hasUsageStats,
            onOpen = { packageName ->
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            onNext = viewModel::advanceCacheTour,
            onRestart = viewModel::resetCacheTour,
            onGrantUsageStats = {
                context.startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
    }
}

/**
 * The breadcrumb is not text: every level is a segment as wide as its share of its parent,
 * so the navigation bar is itself a reading of the data.
 */
@Composable
private fun Breadcrumb(chain: List<MapNode>, onJump: (Int) -> Unit) {
    if (chain.size < 2) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .padding(vertical = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        chain.forEachIndexed { index, node ->
            val parent = chain.getOrNull(index - 1)
            val share = when {
                parent == null -> 1f
                parent.sizeBytes <= 0 -> 1f
                else -> (node.sizeBytes.toFloat() / parent.sizeBytes).coerceIn(0.05f, 1f)
            }
            Box(
                modifier = Modifier
                    .weight(share)
                    .fillMaxSize()
                    .background(Spectrum[node.colorIndex % Spectrum.size].copy(alpha = 0.7f))
                    .clickable(indication = null, interactionSource = null) { onJump(index) },
            )
        }
    }
}

/**
 * Other apps' caches cannot be cleared programmatically, so the app queues the deep links
 * instead and keeps the place in the queue. Honest assistance beats a fake progress bar.
 */
@Composable
private fun CacheTour(
    stops: List<CacheStop>,
    index: Int,
    hasUsageStats: Boolean,
    onOpen: (String) -> Unit,
    onNext: () -> Unit,
    onRestart: () -> Unit,
    onGrantUsageStats: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = Space.m)) {
        SectionLabel(text = "CACHE-TÚRA")
        if (!hasUsageStats) {
            Text(
                text = "A cache mérete csak használati hozzáféréssel látszik.",
                style = PadlasTheme.type.body,
                color = Ink.TextSecondary,
            )
            PadlasTextButton(text = "Megadom", onClick = onGrantUsageStats)
            return@Column
        }
        if (stops.isEmpty()) {
            Text(
                text = "Nincs számottevő cache.",
                style = PadlasTheme.type.body,
                color = Ink.TextSecondary,
            )
            return@Column
        }

        val current = stops.getOrNull(index)
        if (current == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Végigmentél a listán.", style = PadlasTheme.type.body, color = Ink.TextSecondary)
                PadlasTextButton(text = "Újra", onClick = onRestart)
            }
            return@Column
        }

        PadlasCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(text = current.label, style = PadlasTheme.type.body, color = Ink.Text)
                NumericText(text = "${Format.bytes(current.cacheBytes)} cache")
                Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                    PadlasTextButton(text = "Megnyitom", onClick = { onOpen(current.packageName) })
                    PadlasTextButton(text = "Kihagyom", onClick = onNext)
                }
                Text(
                    text = "${Format.count(index + 1)} / ${Format.count(stops.size)} · a cache-t a rendszer üríti, nem az app.",
                    style = PadlasTheme.type.micro,
                    color = Ink.TextTertiary,
                )
            }
        }
    }
}
