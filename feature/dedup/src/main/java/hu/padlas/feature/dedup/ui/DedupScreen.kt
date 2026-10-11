package hu.padlas.feature.dedup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hu.padlas.core.database.entity.DupKind
import hu.padlas.core.design.component.ButtonStyle
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.IconBubble
import hu.padlas.core.design.component.ListRow
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.PadlasIconButton
import hu.padlas.core.design.component.PadlasTopBar
import hu.padlas.core.design.component.ProgressStrip
import hu.padlas.core.design.component.SectionHeader
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space

@Composable
internal fun DedupScreen(
    state: DedupUiState,
    onBack: () -> Unit,
    onResolveGroup: (String) -> Unit,
    onResolveSection: (DupKind) -> Unit,
    onResolveAll: () -> Unit,
    onPickWinner: (String, Long) -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = remember(state.lastScanAt) { System.currentTimeMillis() }
    val busy = state.pendingGroupIds.isNotEmpty() && state.sections.any { section ->
        section.groups.any { it.id in state.pendingGroupIds }
    }
    val showBar = !state.loading && state.loserCount > 0
    val navBottom = navigationBarBottom()

    Box(modifier = modifier.fillMaxSize().background(Ink.Background)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            PadlasTopBar(
                title = DedupCopy.TITLE,
                onBack = onBack,
                subtitle = if (state.loading) null else DedupCopy.topSubtitle(state, now),
                action = {
                    PadlasIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = DedupCopy.RESCAN,
                        onClick = onRescan,
                        enabled = !state.scanning,
                        size = 44.dp,
                    )
                },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Space.page,
                    end = Space.page,
                    top = Space.s,
                    bottom = navBottom + if (showBar) PinnedBarClearance else Space.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                if (!state.isEmpty) {
                    item(key = "hero") {
                        ReclaimHero(
                            label = DedupCopy.RECLAIMABLE,
                            bytes = state.reclaimableBytes,
                            caption = DedupCopy.heroCaption(state.loserCount, state.groupCount),
                        ) {
                            if (state.scanning) {
                                Spacer(Modifier.height(Space.l))
                                ProgressStrip(
                                    fraction = state.progress.fraction,
                                    label = DedupCopy.scanProgress(state.progress),
                                )
                            }
                        }
                    }
                    item(key = "trash") { TrashNotice(trashedBytes = state.trashedBytes) }
                }

                if (!state.loading && state.isEmpty) {
                    item(key = "empty") {
                        if (state.scanning) {
                            ScanningCard(state)
                        } else {
                            EmptyState(
                                title = DedupCopy.EMPTY_TITLE,
                                text = DedupCopy.emptyText(state.lastScanAt, now),
                                icon = Icons.Rounded.CheckCircle,
                                tint = Ink.Success,
                                actionLabel = if (state.lastScanAt > 0L) DedupCopy.RESCAN else DedupCopy.FIRST_SCAN,
                                onAction = onRescan,
                                modifier = Modifier.padding(top = Space.xxl),
                            )
                        }
                    }
                }

                state.sections.filter { it.groups.isNotEmpty() }.forEach { section ->
                    item(key = "head-${section.kind}") {
                        SectionHead(
                            section = section,
                            enabled = !busy,
                            onResolveSection = { onResolveSection(section.kind) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    items(section.groups, key = { group -> group.id }) { group ->
                        DupGroupCard(
                            group = group,
                            pending = group.id in state.pendingGroupIds,
                            enabled = !busy,
                            onResolve = onResolveGroup,
                            onPickWinner = onPickWinner,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }

        PinnedActionBar(visible = showBar, modifier = Modifier.align(Alignment.BottomCenter)) {
            PadlasButton(
                text = DedupCopy.primaryAction(state.loserCount, state.reclaimableBytes),
                onClick = onResolveAll,
                enabled = !busy,
                icon = Icons.Rounded.Delete,
                style = ButtonStyle.Destructive,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TrashNotice(trashedBytes: Long) {
    ListRow(
        title = DedupCopy.TRASH_TITLE,
        subtitle = DedupCopy.TRASH_TEXT,
        icon = Icons.Rounded.DeleteOutline,
        iconTint = Ink.Info,
        value = if (trashedBytes > 0) DedupCopy.trashedValue(trashedBytes) else null,
    )
}

@Composable
private fun ScanningCard(state: DedupUiState) {
    PadlasCard(modifier = Modifier.fillMaxWidth().padding(top = Space.l), contentPadding = Space.xl) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            IconBubble(icon = Icons.Rounded.ImageSearch, size = 56.dp, iconSize = 28.dp)
            Spacer(Modifier.height(Space.l))
            Text(text = DedupCopy.SCANNING_TITLE, style = PadlasTheme.type.title, color = Ink.Text)
            Spacer(Modifier.height(Space.xs))
            Text(
                text = DedupCopy.SCANNING_TEXT,
                style = PadlasTheme.type.body,
                color = Ink.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.xl))
            ProgressStrip(fraction = state.progress.fraction, label = DedupCopy.scanProgress(state.progress))
        }
    }
}

@Composable
private fun SectionHead(
    section: DedupSectionUi,
    enabled: Boolean,
    onResolveSection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = Space.l)) {
        SectionHeader(
            title = DedupCopy.sectionTitle(section.kind),
            actionLabel = if (enabled && section.loserCount > 0) DedupCopy.SECTION_ACTION else null,
            onAction = onResolveSection,
        )
        Text(
            text = DedupCopy.sectionLead(section.kind),
            style = PadlasTheme.type.body,
            color = Ink.TextSecondary,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = DedupCopy.sectionTotals(section.groups.size, section.loserCount, section.reclaimableBytes),
            style = PadlasTheme.type.numeric,
            color = Ink.TextTertiary,
            maxLines = 1,
        )
    }
}
