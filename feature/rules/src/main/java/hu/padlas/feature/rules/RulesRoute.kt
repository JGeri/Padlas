package hu.padlas.feature.rules

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.RuleEntity
import hu.padlas.core.design.component.ButtonSize
import hu.padlas.core.design.component.ButtonStyle
import hu.padlas.core.design.component.ConfirmDialog
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.IconBubble
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.PadlasSwitch
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.PadlasTopBar
import hu.padlas.core.design.component.Pill
import hu.padlas.core.design.component.RollingNumber
import hu.padlas.core.design.component.SectionHeader
import hu.padlas.core.design.component.Toast
import hu.padlas.core.design.component.hairlineBottom
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.haptics
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space
import kotlinx.coroutines.delay

/**
 * Rules are the reason the app is opened a second month. They are also the part that runs
 * without supervision, which is why every switch here is gated behind a dry run.
 */
@Composable
fun RulesRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: RulesViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navBottom = navigationBarBottom()
    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    val entrance = rememberEntrance()
    var deleting by remember { mutableStateOf<RuleEntity?>(null) }

    LaunchedEffect(Unit) { RulesScheduler.enqueueWeekly(context) }

    val active = state.rules.count { it.enabled }

    GlassScreen(
        scrolled = scrolled,
        modifier = modifier,
        topBar = {
            PadlasTopBar(
                title = "Automatikus szabályok",
                onBack = onBack,
                subtitle = when {
                    state.loading -> null
                    active == 0 -> "Mind kikapcsolva"
                    else -> "${Format.count(active)} bekapcsolva · hetente futnak"
                },
            )
        },
        overlay = {
            Toast(
                message = state.message,
                onShown = viewModel::dismissMessage,
                icon = if (state.messageOk) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                tint = if (state.messageOk) Ink.Success else Ink.Info,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = navBottom + Space.l),
            )
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.page,
                end = Space.page,
                top = topBarClearance() + Space.s,
                bottom = navBottom + Space.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            item(key = "intro") { Intro(modifier = Modifier.entrance(entrance)) }

            if (state.rules.isEmpty() && !state.loading) {
                item(key = "empty") {
                    EmptyState(
                        title = "Még nincs szabály",
                        text = "A beépített szabályok az app következő indításakor jelennek meg.",
                        icon = Icons.Rounded.AutoMode,
                        modifier = Modifier.entrance(entrance),
                    )
                }
            }

            itemsIndexed(state.rules, key = { _, rule -> rule.id }) { _, rule ->
                RuleCard(
                    rule = rule,
                    dryRun = state.dryRuns[rule.id],
                    expanded = state.openDryRun == rule.id,
                    busy = state.busyRuleId == rule.id,
                    onDryRun = { viewModel.dryRun(rule) },
                    onClose = viewModel::closeDryRun,
                    onToggle = { enabled ->
                        viewModel.setEnabled(rule, enabled)
                        // The switch cannot go on before a dry run (INV-6), so the
                        // refusal comes with the dry run it asks for.
                        if (enabled && state.dryRuns[rule.id] == null) viewModel.dryRun(rule)
                    },
                    onRunNow = { viewModel.runNow(rule) },
                    onDelete = { deleting = rule },
                    modifier = Modifier.entrance(entrance).then(itemMotion()),
                )
            }

            item(key = "delta-head") {
                Column(modifier = Modifier.entrance(entrance).padding(top = Space.l)) {
                    SectionHeader(
                        title = "Heti változás",
                        actionLabel = "Mérés most",
                        onAction = viewModel::refreshDeltas,
                    )
                    Text(
                        text = "Mely mappák és appok nőttek vagy fogytak az elmúlt héten.",
                        style = PadlasTheme.type.body,
                        color = Ink.TextSecondary,
                    )
                }
            }
            item(key = "deltas") {
                Box(modifier = Modifier.entrance(entrance).animateContentSize(finite(LocalReducedMotion.current))) {
                    DeltaCard(deltas = state.deltas)
                }
            }
        }
    }

    deleting?.let { rule ->
        ConfirmDialog(
            title = "Törlöd a szabályt?",
            text = "„${RuleCopy.displayName(rule)}” többé nem fut le. A fájljaidhoz nem nyúlunk.",
            confirmLabel = "Szabály törlése",
            onConfirm = {
                deleting = null
                viewModel.delete(rule)
            },
            onDismiss = { deleting = null },
            destructive = true,
            icon = Icons.Rounded.DeleteOutline,
        )
    }
}

@Composable
private fun Intro(modifier: Modifier = Modifier) {
    Text(
        text = "A bekapcsolt szabályok hetente maguktól lefutnak. Véglegesen soha nem törölnek, " +
            "és bekapcsolás előtt mindig megnézheted, mit érintenének.",
        style = PadlasTheme.type.body,
        color = Ink.TextSecondary,
        modifier = modifier,
    )
}

private sealed interface RuleResult {
    data object Busy : RuleResult
    data class Dry(val result: DryRunResult) : RuleResult
    data class Last(val line: String) : RuleResult
    data object Empty : RuleResult
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleCard(
    rule: RuleEntity,
    dryRun: DryRunResult?,
    expanded: Boolean,
    busy: Boolean,
    onDryRun: () -> Unit,
    onClose: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val haptic = haptics()
    PadlasCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.animateContentSize(finite(LocalReducedMotion.current))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(icon = RuleCopy.actionIcon(rule.action), tint = RuleCopy.actionTint(rule.action))
                Spacer(Modifier.width(Space.m))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = RuleCopy.displayName(rule),
                        style = PadlasTheme.type.bodyStrong,
                        color = Ink.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = RuleCopy.actionSentence(rule.action),
                        style = PadlasTheme.type.caption,
                        color = Ink.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(Space.s))
                PadlasSwitch(
                    checked = rule.enabled,
                    onCheckedChange = { enabled ->
                        haptic?.tick(0.3f)
                        onToggle(enabled)
                    },
                )
            }

            Spacer(Modifier.height(Space.m))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                RuleCopy.conditions(rule).forEach { condition ->
                    Pill(text = condition, color = Ink.TextSecondary, background = Ink.SurfaceHigh)
                }
            }

            val result = when {
                busy -> RuleResult.Busy
                dryRun != null -> RuleResult.Dry(dryRun)
                else -> RuleCopy.lastRunLine(rule)?.let { RuleResult.Last(it) } ?: RuleResult.Empty
            }
            AnimatedContent(
                targetState = result,
                contentKey = { it::class },
                transitionSpec = {
                    (fadeIn(finite(reduced)) togetherWith fadeOut(finite(reduced))).using(SizeTransform(clip = false))
                },
                label = "ruleResult",
            ) { shown ->
                when (shown) {
                    RuleResult.Busy -> ResultLine(text = "Számolás…", color = Ink.TextTertiary)
                    is RuleResult.Dry -> DryRunPanel(dryRun = shown.result, expanded = expanded, onClose = onClose)
                    is RuleResult.Last -> ResultLine(text = shown.line, color = Ink.TextTertiary)
                    RuleResult.Empty -> Spacer(Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.height(Space.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                PadlasButton(
                    text = if (dryRun == null) "Mit érintene?" else "Újraszámolás",
                    onClick = onDryRun,
                    enabled = !busy,
                    style = ButtonStyle.Secondary,
                    size = ButtonSize.Medium,
                    modifier = Modifier.weight(1f),
                )
                AnimatedVisibility(
                    visible = dryRun != null && dryRun.matchCount > 0,
                    enter = popIn(reduced),
                    exit = fadeOut(finite(reduced)),
                    modifier = Modifier.weight(1f),
                ) {
                    PadlasButton(
                        text = "Futtatás most",
                        onClick = onRunNow,
                        enabled = !busy,
                        size = ButtonSize.Medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (!rule.builtIn) {
                PadlasTextButton(
                    text = "Szabály törlése",
                    onClick = onDelete,
                    color = Ink.Danger,
                    modifier = Modifier.padding(top = Space.xs).offset(x = -Space.m),
                )
            }
        }
    }
}

@Composable
private fun ResultLine(text: String, color: Color) {
    Text(
        text = text,
        style = PadlasTheme.type.caption,
        color = color,
        modifier = Modifier.padding(top = Space.m),
    )
}

/** Twenty random samples, because a dry run that only shows totals is not a dry run. */
@Composable
private fun DryRunPanel(dryRun: DryRunResult, expanded: Boolean, onClose: () -> Unit) {
    val reduced = LocalReducedMotion.current
    Column(
        modifier = Modifier
            .padding(top = Space.m)
            .fillMaxWidth()
            .clip(Shapes.tile)
            .background(Ink.SurfaceHigh)
            .padding(Space.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon = Icons.Rounded.QueryStats, tint = Ink.Accent, size = 28.dp, iconSize = 16.dp)
            Spacer(Modifier.width(Space.s))
            if (dryRun.matchCount == 0) {
                Text(
                    text = RuleCopy.dryRunLine(dryRun.matchCount, dryRun.matchBytes),
                    style = PadlasTheme.type.label,
                    color = Ink.Text,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Most ", style = PadlasTheme.type.label, color = Ink.Text, maxLines = 1)
                    RollingNumber(value = Format.count(dryRun.matchCount), style = PadlasTheme.type.label, color = Ink.Text)
                    Text(text = " fájlt érintene · ", style = PadlasTheme.type.label, color = Ink.Text, maxLines = 1)
                    RollingNumber(value = Format.bytes(dryRun.matchBytes), style = PadlasTheme.type.label, color = Ink.Text)
                }
            }
        }
        AnimatedVisibility(
            visible = expanded && dryRun.samples.isNotEmpty(),
            enter = expandVertically(finite(reduced)) + fadeIn(finite(reduced)),
            exit = shrinkVertically(finite(reduced)) + fadeOut(finite(reduced)),
        ) {
            Column {
                Spacer(Modifier.height(Space.m))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    itemsIndexed(dryRun.samples, key = { _, asset -> asset.mediaStoreId }) { index, asset ->
                        SampleThumb(asset = asset, order = index)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Véletlen minta a találatokból",
                        style = PadlasTheme.type.caption,
                        color = Ink.TextTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    PadlasTextButton(text = "Elrejtés", onClick = onClose)
                }
            }
        }
    }
}

/** The samples deal out one after the other, so the row reads as a hand of cards. */
@Composable
private fun SampleThumb(asset: Asset, order: Int) {
    val reduced = LocalReducedMotion.current
    val appear = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (appear.value >= 1f) return@LaunchedEffect
        delay(minOf(order, Motion.StaggerCap).toLong() * SampleStepMs)
        appear.animateTo(1f, Motion.bouncy())
    }
    Box(
        modifier = Modifier
            .graphicsLayer {
                val p = appear.value
                alpha = p.coerceIn(0f, 1f)
                scaleX = 0.6f + 0.4f * p
                scaleY = scaleX
            }
            .size(56.dp)
            .clip(Shapes.small)
            .background(Ink.SurfaceHighest),
    ) {
        AsyncImage(
            model = Uri.parse(asset.uri),
            contentDescription = asset.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp),
        )
    }
}

private const val SampleStepMs = 30L

@Composable
private fun DeltaCard(deltas: List<BucketDelta>) {
    if (deltas.isEmpty()) {
        PadlasCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                IconBubble(icon = Icons.Rounded.QueryStats, tint = Ink.TextSecondary)
                Spacer(Modifier.width(Space.m))
                Text(
                    text = "Még nincs mihez hasonlítani. Az app hetente feljegyzi, mennyi helyet foglal " +
                        "egy-egy mappa, és egy hét múlva itt látod, mi nőtt.",
                    style = PadlasTheme.type.caption,
                    color = Ink.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        return
    }
    PadlasCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
        Column {
            deltas.forEachIndexed { index, delta ->
                val grew = delta.delta >= 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (index < deltas.lastIndex) Modifier.hairlineBottom() else Modifier)
                        .padding(horizontal = Space.l, vertical = Space.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBubble(
                        icon = if (grew) {
                            Icons.AutoMirrored.Rounded.TrendingUp
                        } else {
                            Icons.AutoMirrored.Rounded.TrendingDown
                        },
                        tint = if (grew) Ink.Danger else Ink.Success,
                        size = 32.dp,
                        iconSize = 16.dp,
                    )
                    Spacer(Modifier.width(Space.m))
                    Text(
                        text = delta.label,
                        style = PadlasTheme.type.body,
                        color = Ink.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Space.s))
                    Text(
                        text = RuleCopy.deltaText(delta.delta),
                        style = PadlasTheme.type.numeric,
                        color = if (grew) Ink.Danger else Ink.Success,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
