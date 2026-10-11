package hu.padlas.feature.dedup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.entity.DupKind
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.NumericText
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasTopBar
import hu.padlas.core.design.component.ProgressStrip
import hu.padlas.core.design.component.RollingNumber
import hu.padlas.core.design.component.SectionLabel
import hu.padlas.core.design.component.StaggeredColumnItem
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space

@Composable
internal fun DedupScreen(
    state: DedupUiState,
    onBack: () -> Unit,
    onResolveGroup: (String) -> Unit,
    onResolveSection: (DupKind) -> Unit,
    onPickWinner: (String, Long) -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        PadlasTopBar(title = DedupCopy.TITLE, onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Space.l),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            item(key = "summary") { DedupSummary(state) }

            if (!state.loading && state.isEmpty) {
                item(key = "empty") { DedupEmpty(state, onRescan) }
            }

            state.sections.filter { it.groups.isNotEmpty() }.forEach { section ->
                item(key = "head-${section.kind}") { SectionHead(section) }

                itemsIndexed(section.groups, key = { _, group -> group.id }) { index, group ->
                    StaggeredColumnItem(index = index) {
                        DupGroupCard(
                            group = group,
                            resetToken = state.resetToken,
                            onResolve = onResolveGroup,
                            onPickWinner = onPickWinner,
                        )
                    }
                }

                item(key = "batch-${section.kind}") {
                    PadlasButton(
                        text = DedupCopy.batchAction(section.loserCount),
                        onClick = { onResolveSection(section.kind) },
                        destructive = true,
                        enabled = section.loserCount > 0,
                        modifier = Modifier.fillMaxWidth().padding(bottom = Space.l),
                    )
                }
            }
        }
    }
}

/** The whole point of the screen in two lines, plus the sentence other cleaners hide. */
@Composable
private fun DedupSummary(state: DedupUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        SectionLabel(text = DedupCopy.RECLAIMABLE)
        Row(verticalAlignment = Alignment.Bottom) {
            RollingNumber(
                value = Format.bytesValue(state.reclaimableBytes),
                style = PadlasTheme.type.display,
                color = Ink.Text,
            )
            Text(
                text = " ${Format.bytesUnit(state.reclaimableBytes)}",
                style = PadlasTheme.type.title,
                color = Ink.TextSecondary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        NumericText(text = "${Format.count(state.loserCount)} vesztes")

        if (state.scanning) {
            ProgressStrip(
                fraction = state.progress.fraction,
                label = DedupCopy.scanProgress(state.progress),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text(
            text = DedupCopy.TRASH_NOTICE,
            style = PadlasTheme.type.body,
            color = Ink.TextSecondary,
        )
        if (state.trashedBytes > 0) {
            Text(
                text = DedupCopy.trashedNotice(state.trashedBytes),
                style = PadlasTheme.type.label,
                color = Ink.Accent,
            )
        }
    }
}

@Composable
private fun SectionHead(section: DedupSectionUi) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        SectionLabel(text = DedupCopy.sectionTitle(section.kind))
        Text(
            text = DedupCopy.sectionLead(section.kind),
            style = PadlasTheme.type.body,
            color = Ink.TextSecondary,
        )
        if (section.kind == DupKind.SIMILAR) {
            Text(text = DedupCopy.PICK_KEEPER, style = PadlasTheme.type.label, color = Ink.TextTertiary)
        }
        NumericText(text = DedupCopy.sectionTotals(section.loserCount, section.reclaimableBytes))
    }
}

@Composable
private fun DedupEmpty(state: DedupUiState, onRescan: () -> Unit) {
    val now = remember(state.lastScanAt) { System.currentTimeMillis() }
    EmptyState(
        text = DedupCopy.emptyState(state.lastScanAt, now),
        actionLabel = if (state.lastScanAt > 0L) DedupCopy.RESCAN else DedupCopy.FIRST_SCAN,
        onAction = onRescan,
        modifier = Modifier.fillMaxWidth(),
    )
}
