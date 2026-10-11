package hu.padlas.feature.dedup.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.entity.DupKind
import hu.padlas.core.design.component.BarFigures
import hu.padlas.core.design.component.ButtonStyle
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.GlassScreen
import hu.padlas.core.design.component.ListRow
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.PadlasIconButton
import hu.padlas.core.design.component.PadlasTopBar
import hu.padlas.core.design.component.Pill
import hu.padlas.core.design.component.PinnedActionBar
import hu.padlas.core.design.component.PinnedBarClearance
import hu.padlas.core.design.component.ProgressRing
import hu.padlas.core.design.component.ProgressStrip
import hu.padlas.core.design.component.ReclaimHero
import hu.padlas.core.design.component.RollingNumber
import hu.padlas.core.design.component.SectionHeader
import hu.padlas.core.design.component.ZoomHost
import hu.padlas.core.design.component.ZoomTarget
import hu.padlas.core.design.component.navigationBarBottom
import hu.padlas.core.design.component.rememberZoomState
import hu.padlas.core.design.component.topBarClearance
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.entrance
import hu.padlas.core.design.motion.finite
import hu.padlas.core.design.motion.haptics
import hu.padlas.core.design.motion.itemMotion
import hu.padlas.core.design.motion.popIn
import hu.padlas.core.design.motion.rememberEntrance
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
    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    val zoom = rememberZoomState()
    val entrance = rememberEntrance()
    val hapticFeedback = haptics()

    ZoomHost(
        state = zoom,
        picture = { target, placeholder, pictureModifier -> ZoomPicture(target, placeholder, pictureModifier) },
        modifier = modifier.fillMaxSize(),
        actions = { target -> ZoomVerdict(target = target, state = state, onPickWinner = onPickWinner) },
    ) {
        GlassScreen(
            scrolled = scrolled,
            topBar = {
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
            },
            overlay = { haze ->
                PinnedActionBar(visible = showBar, haze = haze, modifier = Modifier.align(Alignment.BottomCenter)) {
                    BarFigures(
                        count = Format.count(state.loserCount),
                        noun = DedupCopy.BAR_NOUN,
                        value = Format.bytesValue(state.reclaimableBytes),
                        unit = Format.bytesUnit(state.reclaimableBytes),
                    )
                    PadlasButton(
                        text = DedupCopy.ALL_ACTION,
                        onClick = {
                            hapticFeedback?.collapse()
                            onResolveAll()
                        },
                        enabled = !busy,
                        icon = Icons.Rounded.Delete,
                        style = ButtonStyle.Destructive,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Space.page,
                    end = Space.page,
                    top = topBarClearance() + Space.s,
                    bottom = navBottom + if (showBar) PinnedBarClearance else Space.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                if (!state.isEmpty) {
                    item(key = "hero") {
                        ReclaimHero(
                            label = DedupCopy.RECLAIMABLE,
                            value = Format.bytesValue(state.reclaimableBytes),
                            unit = Format.bytesUnit(state.reclaimableBytes),
                            caption = DedupCopy.heroCaption(state.loserCount, state.groupCount),
                            modifier = Modifier.entrance(entrance).then(itemMotion()),
                        ) {
                            AnimatedVisibility(
                                visible = state.scanning,
                                enter = expandVertically(finite(LocalReducedMotion.current)) + fadeIn(),
                                exit = shrinkVertically(finite(LocalReducedMotion.current)) + fadeOut(),
                            ) {
                                Column {
                                    Spacer(Modifier.height(Space.l))
                                    ProgressStrip(
                                        fraction = state.progress.fraction,
                                        label = DedupCopy.scanProgress(state.progress),
                                    )
                                }
                            }
                        }
                    }
                    item(key = "trash") {
                        TrashNotice(
                            trashedBytes = state.trashedBytes,
                            modifier = Modifier.entrance(entrance).then(itemMotion()),
                        )
                    }
                }

                if (!state.loading && state.isEmpty) {
                    item(key = "empty") {
                        Box(modifier = Modifier.entrance(entrance).then(itemMotion())) {
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
                }

                state.sections.filter { it.groups.isNotEmpty() }.forEach { section ->
                    item(key = "head-${section.kind}") {
                        SectionHead(
                            section = section,
                            enabled = !busy,
                            onResolveSection = {
                                hapticFeedback?.collapse()
                                onResolveSection(section.kind)
                            },
                            modifier = Modifier.entrance(entrance).then(itemMotion()),
                        )
                    }
                    items(section.groups, key = { group -> group.id }) { group ->
                        DupGroupCard(
                            group = group,
                            pending = group.id in state.pendingGroupIds,
                            enabled = !busy,
                            onResolve = onResolveGroup,
                            onPickWinner = onPickWinner,
                            onView = { member ->
                                zoom.open(
                                    ZoomTarget(
                                        key = DedupZoomKey(group.id, member.assetId),
                                        model = member.uri,
                                        title = member.displayName,
                                        caption = Format.bytes(member.sizeBytes),
                                    ),
                                )
                            },
                            modifier = Modifier.entrance(entrance).then(itemMotion()),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Under the enlarged picture: where it stands now, and for a similar group the one choice
 * that matters, made without going back to the grid.
 */
@Composable
private fun ColumnScope.ZoomVerdict(
    target: ZoomTarget,
    state: DedupUiState,
    onPickWinner: (String, Long) -> Unit,
) {
    val key = target.key as? DedupZoomKey ?: return
    val group = state.sections.firstNotNullOfOrNull { section -> section.groups.firstOrNull { it.id == key.groupId } } ?: return
    val member = group.members.firstOrNull { it.assetId == key.assetId } ?: return
    val hapticFeedback = haptics()
    val reduced = LocalReducedMotion.current
    val verdict = when {
        member.isWinner -> Verdict.KEEP
        member.isProtected -> Verdict.PROTECTED
        else -> Verdict.TRASH
    }
    AnimatedContent(
        targetState = verdict,
        transitionSpec = {
            (popIn(reduced) togetherWith fadeOut(finite(reduced))).using(SizeTransform(clip = false))
        },
        label = "verdict",
        modifier = Modifier.align(Alignment.CenterHorizontally),
    ) { shown ->
        when (shown) {
            Verdict.KEEP -> Pill(
                text = DedupCopy.KEEP_BADGE,
                icon = Icons.Rounded.Check,
                color = Ink.Background,
                background = Ink.Success,
            )
            Verdict.PROTECTED -> Pill(
                text = DedupCopy.PROTECTED_BADGE,
                icon = Icons.Rounded.Shield,
                color = Ink.Text,
                background = Ink.Info.copy(alpha = 0.85f),
            )
            Verdict.TRASH -> Pill(
                text = DedupCopy.WILL_TRASH,
                icon = Icons.Rounded.DeleteOutline,
                color = Ink.Text,
                background = Ink.Danger.copy(alpha = 0.85f),
            )
        }
    }
    if (group.kind == DupKind.SIMILAR && verdict == Verdict.TRASH && group.id !in state.pendingGroupIds) {
        PadlasButton(
            text = DedupCopy.VIEW_KEEP,
            onClick = {
                hapticFeedback?.keep()
                onPickWinner(group.id, member.assetId)
            },
            icon = Icons.Rounded.Check,
            style = ButtonStyle.Success,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private enum class Verdict { KEEP, PROTECTED, TRASH }

/** The full picture, on top of the tile's cached thumbnail until it has loaded. */
@Composable
private fun ZoomPicture(target: ZoomTarget, placeholder: Any?, modifier: Modifier) {
    val context = LocalContext.current
    val request = remember(target.model, placeholder) {
        ImageRequest.Builder(context)
            .data(target.model)
            .placeholderMemoryCacheKey(placeholder as? MemoryCache.Key)
            .crossfade(true)
            .build()
    }
    AsyncImage(model = request, contentDescription = target.title, contentScale = ContentScale.Crop, modifier = modifier)
}

/** Scoped to the group: the same picture can sit in more than one group, but flies from one tile. */
internal data class DedupZoomKey(val groupId: String, val assetId: Long)

@Composable
private fun TrashNotice(trashedBytes: Long, modifier: Modifier = Modifier) {
    ListRow(
        title = DedupCopy.TRASH_TITLE,
        subtitle = DedupCopy.TRASH_TEXT,
        icon = Icons.Rounded.DeleteOutline,
        iconTint = Ink.Info,
        value = if (trashedBytes > 0) DedupCopy.trashedValue(trashedBytes) else null,
        modifier = modifier,
    )
}

/**
 * The first search on a full gallery takes minutes; the ring and the rolling count are
 * what make that read as progress rather than as a hang.
 */
@Composable
private fun ScanningCard(state: DedupUiState) {
    PadlasCard(modifier = Modifier.fillMaxWidth().padding(top = Space.l), contentPadding = Space.xl) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to Ink.Accent.copy(alpha = 0.32f),
                            1f to Color.Transparent,
                            radius = size.minDimension * 0.75f,
                        ),
                        radius = size.minDimension * 0.75f,
                    )
                },
            ) {
                ProgressRing(fraction = state.progress.fraction, size = 132.dp, stroke = 8.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (state.progress.total > 0) {
                            RollingNumber(
                                value = Format.count(state.progress.done),
                                style = PadlasTheme.type.numericLarge,
                                color = Ink.Text,
                            )
                            Text(
                                text = "/ ${Format.count(state.progress.total)}",
                                style = PadlasTheme.type.caption,
                                color = Ink.TextSecondary,
                                maxLines = 1,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.ImageSearch,
                                contentDescription = null,
                                tint = Ink.Accent,
                                modifier = Modifier.padding(Space.xs),
                            )
                        }
                    }
                }
            }
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
