package hu.padlas.feature.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.entity.RuleAction
import hu.padlas.core.database.entity.RuleEntity
import hu.padlas.core.design.component.Chip
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.NumericText
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.PadlasTopBar
import hu.padlas.core.design.component.SectionLabel
import hu.padlas.core.design.component.StaggeredColumnItem
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space

/**
 * Rules are the reason the app is opened a second month. They are also the part that runs
 * without supervision, which is why every switch here is gated behind a dry run.
 */
@Composable
fun RulesRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: RulesViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { RulesScheduler.enqueueWeekly(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = Space.l),
    ) {
        PadlasTopBar(
            title = "SZABÁLYOK",
            onBack = onBack,
            action = { PadlasTextButton(text = "Mérés most", onClick = viewModel::refreshDeltas) },
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            if (state.rules.isEmpty() && !state.loading) {
                item { EmptyState(text = "Nincs szabály. A beépítettek a következő indításkor jönnek létre.") }
            }

            itemsIndexed(state.rules, key = { _, rule -> rule.id }) { index, rule ->
                StaggeredColumnItem(index = index) {
                    RuleCard(
                        rule = rule,
                        dryRun = state.dryRuns[rule.id],
                        expanded = state.openDryRun == rule.id,
                        busy = state.busyRuleId == rule.id,
                        onDryRun = { viewModel.dryRun(rule) },
                        onClose = viewModel::closeDryRun,
                        onToggle = { viewModel.setEnabled(rule, !rule.enabled) },
                        onRunNow = { viewModel.runNow(rule) },
                        onDelete = { viewModel.delete(rule) },
                    )
                }
            }

            if (state.deltas.isNotEmpty()) {
                item { SectionLabel(text = "HETI VÁLTOZÁS", modifier = Modifier.padding(top = Space.xl)) }
                items(state.deltas, key = { it.label }) { delta -> DeltaRow(delta) }
            }
        }

        state.message?.let { message ->
            Text(
                text = message,
                style = PadlasTheme.type.label,
                color = Ink.TextSecondary,
                modifier = Modifier.padding(vertical = Space.s),
            )
            LaunchedEffect(message) { viewModel.dismissMessage() }
        }
    }
}

@Composable
private fun RuleCard(
    rule: RuleEntity,
    dryRun: DryRunResult?,
    expanded: Boolean,
    busy: Boolean,
    onDryRun: () -> Unit,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onRunNow: () -> Unit,
    onDelete: () -> Unit,
) {
    PadlasCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = rule.name, style = PadlasTheme.type.body, color = Ink.Text)
                Chip(
                    text = if (rule.enabled) "aktív" else "kikapcsolva",
                    selected = rule.enabled,
                    onClick = onToggle,
                )
            }

            Text(
                text = buildString {
                    append("HA ")
                    append(PredicateJson.decode(rule.predicateJson)?.describe() ?: "hibás szabály")
                    append(" AKKOR ")
                    append(rule.action.hungarian())
                },
                style = PadlasTheme.type.label,
                color = Ink.TextSecondary,
            )

            if (dryRun != null) {
                NumericText(
                    text = "próba: ${Format.count(dryRun.matchCount)} fájl · ${Format.bytes(dryRun.matchBytes)}",
                )
            } else if (rule.lastRunAt != null) {
                NumericText(
                    text = "utolsó futás: ${Format.count(rule.lastMatchCount)} fájl · " +
                        Format.bytes(rule.lastMatchBytes),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                PadlasTextButton(text = if (busy) "Számolás…" else "Próba", onClick = onDryRun)
                if (dryRun != null && dryRun.matchCount > 0) {
                    PadlasTextButton(text = "Futtatás most", onClick = onRunNow)
                }
                if (!rule.builtIn) {
                    PadlasTextButton(text = "Törlés", onClick = onDelete)
                }
            }

            if (expanded && dryRun != null) {
                DryRunSamples(dryRun = dryRun, onClose = onClose)
            }
        }
    }
}

/** Twenty random samples, because a dry run that only shows totals is not a dry run. */
@Composable
private fun DryRunSamples(dryRun: DryRunResult, onClose: () -> Unit) {
    Column(modifier = Modifier.padding(top = Space.s)) {
        SectionLabel(text = "MINTA")
        dryRun.samples.take(20).forEach { asset ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = asset.relativePath + asset.displayName,
                    style = PadlasTheme.type.micro,
                    color = Ink.TextTertiary,
                )
                NumericText(text = Format.bytes(asset.sizeBytes), color = Ink.TextTertiary)
            }
        }
        PadlasTextButton(text = "Bezárás", onClick = onClose)
    }
}

@Composable
private fun DeltaRow(delta: BucketDelta) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = delta.label, style = PadlasTheme.type.body, color = Ink.Text)
        NumericText(
            text = (if (delta.delta >= 0) "+" else "") + Format.bytes(delta.delta),
            color = if (delta.delta >= 0) Ink.Danger else Ink.Success,
        )
    }
}

private fun RuleAction.hungarian(): String = when (this) {
    RuleAction.ARCHIVE -> "padlásra"
    RuleAction.TRASH -> "kidobóba"
    RuleAction.PROTECT -> "védetté tesz"
    RuleAction.TRANSCODE -> "tömörítésre jelöl"
}
