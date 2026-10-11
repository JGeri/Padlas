package hu.padlas.feature.audit

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.ButtonSize
import hu.padlas.core.design.component.ButtonStyle
import hu.padlas.core.design.component.ConfirmDialog
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.IconBubble
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.PadlasIconButton
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.PadlasTopBar
import hu.padlas.core.design.component.Toast
import hu.padlas.core.design.component.hairlineBottom
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.auroraBackground
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

    val undoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onUndoResult(result.resultCode == Activity.RESULT_OK) }

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
                viewModel.message("A napló elmentve.")
            } else {
                viewModel.message("Az exportálás nem sikerült.", ok = false)
            }
        }
    }

    LaunchedEffect(state.undoRequest) {
        val request = state.undoRequest ?: return@LaunchedEffect
        undoLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
    }

    Box(modifier = modifier.fillMaxSize().auroraBackground()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            PadlasTopBar(
                title = "Napló",
                onBack = onBack,
                subtitle = if (state.loading) null else "${Format.bytes(state.reclaimedBytes)} felszabadítva összesen",
                action = {
                    PadlasIconButton(
                        icon = Icons.Rounded.FileDownload,
                        contentDescription = "Napló mentése CSV-fájlba",
                        onClick = { exportLauncher.launch("padlas-naplo.csv") },
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
                    bottom = navBottom + Space.xl,
                ),
            ) {
                item(key = "hero") {
                    ReclaimHero(
                        label = "Eddig felszabadítva",
                        bytes = state.reclaimedBytes,
                        caption = "Itt látod, mit csinált az app a fájljaiddal. " +
                            "A kukába tett fájlok 30 napig visszaállíthatók.",
                    )
                }

                if (state.days.isEmpty() && !state.loading) {
                    item(key = "empty") {
                        EmptyState(
                            title = "Még üres a napló",
                            text = "Amint az app bármit csinál a fájljaiddal, itt megjelenik.",
                            icon = Icons.Rounded.History,
                        )
                    }
                }

                state.days.forEach { day ->
                    item(key = "day-${day.key}") { DayHeader(day) }
                    itemsIndexed(day.rows, key = { _, row -> row.key }) { index, row ->
                        LogRowItem(
                            row = row,
                            first = index == 0,
                            last = index == day.rows.lastIndex,
                            undoEnabled = state.undoRequest == null,
                            onUndo = { viewModel.undo(row.entries) },
                        )
                    }
                }

                item(key = "kill") {
                    KillSwitchCard(onStop = { confirmStop = true }, modifier = Modifier.padding(top = Space.xxl))
                }
            }
        }

        Toast(
            message = state.message,
            onShown = viewModel::dismissMessage,
            icon = if (state.messageOk) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
            tint = if (state.messageOk) Ink.Success else Ink.Info,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = navBottom + Space.l),
        )
    }

    if (confirmStop) {
        ConfirmDialog(
            title = "Leállítasz mindent?",
            text = "A folyamatban lévő beolvasás, feltöltés és tömörítés megszakad. " +
                "Később bármikor újraindíthatod őket.",
            confirmLabel = "Minden leállítása",
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
private fun DayHeader(day: LogDay) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.xl, bottom = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = day.label,
            style = PadlasTheme.type.title,
            color = Ink.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (day.bytes > 0) {
            Spacer(Modifier.width(Space.s))
            Text(
                text = "${Format.bytes(day.bytes)} felszabadítva",
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
        modifier = Modifier
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
                text = LogCopy.title(row.action, row.count),
                style = PadlasTheme.type.bodyStrong,
                color = Ink.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = LogCopy.subtitle(row, time),
                style = PadlasTheme.type.caption,
                color = Ink.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.undoable) {
                PadlasTextButton(
                    text = "Visszaállítás",
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
                    Text(text = "Vészleállítás", style = PadlasTheme.type.bodyStrong, color = Ink.Text)
                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        text = "Azonnal leállít minden háttérmunkát: beolvasást, keresést, feltöltést és tömörítést.",
                        style = PadlasTheme.type.caption,
                        color = Ink.TextSecondary,
                    )
                }
            }
            Spacer(Modifier.height(Space.l))
            PadlasButton(
                text = "Minden háttérmunka leállítása",
                onClick = onStop,
                style = ButtonStyle.Secondary,
                size = ButtonSize.Medium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
