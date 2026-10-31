package hu.padlas.feature.audit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.padlas.core.common.format.Format
import hu.padlas.core.common.text.uiText
import hu.padlas.core.design.component.ButtonSize
import hu.padlas.core.design.component.ButtonStyle
import hu.padlas.core.design.component.ConfirmDialog
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.GlassScreen
import hu.padlas.core.design.component.IconBubble
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.PadlasIconButton
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.PadlasTopBar
import hu.padlas.core.design.component.Toast
import hu.padlas.core.design.component.asString
import hu.padlas.core.design.component.hairlineBottom
import hu.padlas.core.design.component.navigationBarBottom
import hu.padlas.core.design.component.rememberSystemRequest
import hu.padlas.core.design.component.topBarClearance
import hu.padlas.core.design.motion.entrance
import hu.padlas.core.design.motion.itemMotion
import hu.padlas.core.design.motion.rememberEntrance
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Radius
import hu.padlas.core.design.theme.Space
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * Every operation that touched a file is here, and it can leave the device as CSV (INV-5).
 * The kill switch lives at the bottom because it is the one control that has to work even
 * when the user no longer trusts the rest of the app.
 */
@Composable
fun LogRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: AuditViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmStop by remember { mutableStateOf(false) }
    val navBottom = navigationBarBottom()
    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    val entrance = rememberEntrance()

    val launchUndo = rememberSystemRequest(viewModel::onUndoResult)
    val exportName = stringResource(R.string.audit_export_file_name)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val csv = viewModel.exportCsv()
            val written = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
            }.isSuccess
            if (written) {
                viewModel.message(uiText(R.string.audit_export_saved))
            } else {
                viewModel.message(uiText(R.string.audit_export_failed), ok = false)
            }
        }
    }

    LaunchedEffect(state.undoRequest) {
        val request = state.undoRequest ?: return@LaunchedEffect
        launchUndo(request.intentSender)
    }


    GlassScreen(
        scrolled = scrolled,
        modifier = modifier,
        topBar = {
            PadlasTopBar(
                title = stringResource(R.string.audit_title),
                onBack = onBack,
                subtitle = if (state.loading) {
                    null
                } else {
                    stringResource(R.string.audit_subtitle_total, Format.bytes(state.reclaimedBytes))
                },
                action = {
                    PadlasIconButton(
                        icon = Icons.Rounded.FileDownload,
                        contentDescription = stringResource(R.string.audit_export_description),
                        onClick = { exportLauncher.launch(exportName) },
                        size = 44.dp,
                    )
                },
            )
        },
        overlay = {
            Toast(
                message = state.message?.asString(),
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
        ) {
            item(key = "hero") {
                ReclaimHero(
                    label = stringResource(R.string.audit_hero_label),
                    bytes = state.reclaimedBytes,
                    caption = stringResource(R.string.audit_hero_caption),
                    modifier = Modifier.entrance(entrance),
                )
            }

            if (state.days.isEmpty() && !state.loading) {
                item(key = "empty") {
                    EmptyState(
                        title = stringResource(R.string.audit_empty_title),
                        text = stringResource(R.string.audit_empty_text),
                        icon = Icons.Rounded.History,
                        modifier = Modifier.entrance(entrance),
                    )
                }
            }

            state.days.forEach { day ->
                item(key = "day-${day.key}") {
                    DayHeader(day, modifier = Modifier.entrance(entrance).then(itemMotion()))
                }
                itemsIndexed(day.rows, key = { _, row -> row.key }) { index, row ->
                    LogRowItem(
                        row = row,
                        first = index == 0,
                        last = index == day.rows.lastIndex,
                        undoEnabled = state.undoRequest == null,
                        onUndo = { viewModel.undo(row.entries) },
                        modifier = Modifier.entrance(entrance).then(itemMotion()),
                    )
                }
            }

            item(key = "kill") {
                KillSwitchCard(
                    onStop = { confirmStop = true },
                    modifier = Modifier.entrance(entrance).then(itemMotion()).padding(top = Space.xxl),
                )
            }
        }
    }


    if (confirmStop) {
        ConfirmDialog(
            title = stringResource(R.string.audit_stop_confirm_title),
            text = stringResource(R.string.audit_stop_confirm_text),
            confirmLabel = stringResource(R.string.audit_stop_confirm_action),
            onConfirm = {
                confirmStop = false
                viewModel.stopAllWork()
            },
            onDismiss = { confirmStop = false },
            destructive = true,
            icon = Icons.Rounded.StopCircle,
        )
    }
}

@Composable
private fun DayHeader(day: LogDay, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Space.xl, bottom = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = day.label.asString(),
            style = PadlasTheme.type.title,
            color = Ink.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (day.bytes > 0) {
            Spacer(Modifier.width(Space.s))
            Text(
                text = stringResource(R.string.audit_day_freed, Format.bytes(day.bytes)),
                style = PadlasTheme.type.numeric,
                color = Ink.Success,
                maxLines = 1,
            )
        }
    }
}

/** The day reads as one grouped list: rounded at its ends, hairlines in between. */
@Composable
private fun LogRowItem(
    row: LogRow,
    first: Boolean,
    last: Boolean,
    undoEnabled: Boolean,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(
        topStart = if (first) Radius.card else 0.dp,
        topEnd = if (first) Radius.card else 0.dp,
        bottomStart = if (last) Radius.card else 0.dp,
        bottomEnd = if (last) Radius.card else 0.dp,
    )
    val look = LogCopy.look(row.action)
    val time = TIME.format(Instant.ofEpochMilli(row.timestamp).atZone(ZoneId.systemDefault()))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.Surface)
            .then(if (!last) Modifier.hairlineBottom() else Modifier)
            .padding(start = Space.l, end = Space.l, top = Space.m, bottom = if (row.undoable) Space.xs else Space.m),
        verticalAlignment = Alignment.Top,
    ) {
        IconBubble(icon = look.icon, tint = look.tint, size = 36.dp, iconSize = 18.dp)
        Spacer(Modifier.width(Space.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = LogCopy.title(row.action, row.count).asString(),
                style = PadlasTheme.type.bodyStrong,
                color = Ink.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = LogCopy.subtitle(row, time).asString(),
                style = PadlasTheme.type.caption,
                color = Ink.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.undoable) {
                PadlasTextButton(
                    text = stringResource(R.string.audit_restore),
                    onClick = onUndo,
                    icon = Icons.Rounded.Restore,
                    enabled = undoEnabled,
                    // Pulled left by its own padding so the label lines up with the title.
                    modifier = Modifier.heightIn(min = 44.dp).offset(x = -Space.m),
                )
            }
        }
        LogCopy.size(row)?.let { size ->
            Spacer(Modifier.width(Space.s))
            Text(text = size, style = PadlasTheme.type.numeric, color = Ink.TextSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun KillSwitchCard(onStop: () -> Unit, modifier: Modifier = Modifier) {
    PadlasCard(modifier = modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                IconBubble(icon = Icons.Rounded.StopCircle, tint = Ink.Danger)
                Spacer(Modifier.width(Space.m))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.audit_kill_title),
                        style = PadlasTheme.type.bodyStrong,
                        color = Ink.Text,
                    )
                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        text = stringResource(R.string.audit_kill_text),
                        style = PadlasTheme.type.caption,
                        color = Ink.TextSecondary,
                    )
                }
            }
            Spacer(Modifier.height(Space.l))
            PadlasButton(
                text = stringResource(R.string.audit_kill_action),
                onClick = onStop,
                style = ButtonStyle.Secondary,
                size = ButtonSize.Medium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The day's name in the app's language. The patterns live with the strings so each language
 * keeps its own order and punctuation ("Október 31., péntek" against "Friday, October 31").
 */
@Composable
private fun DayLabel.asString(): String = when (this) {
    DayLabel.Today -> stringResource(R.string.audit_day_today)
    DayLabel.Yesterday -> stringResource(R.string.audit_day_yesterday)
    is DayLabel.Date -> {
        val pattern = stringResource(
            if (sameYear) R.string.audit_day_pattern_same_year else R.string.audit_day_pattern_other_year,
        )
        val locale = LocalConfiguration.current.locales[0]
        remember(date, pattern, locale) {
            DateTimeFormatter.ofPattern(pattern, locale).format(date).replaceFirstChar { it.titlecase(locale) }
        }
    }
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
