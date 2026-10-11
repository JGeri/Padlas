package hu.padlas.feature.audit

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.entity.AuditAction
import hu.padlas.core.database.entity.AuditEntry
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.NumericText
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.PadlasTopBar
import hu.padlas.core.design.component.SectionLabel
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space
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
            viewModel.message(if (written) "A napló exportálva." else "Az export nem sikerült.")
        }
    }

    LaunchedEffect(state.undoRequest) {
        val request = state.undoRequest ?: return@LaunchedEffect
        undoLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = Space.l),
    ) {
        PadlasTopBar(
            title = "NAPLÓ",
            onBack = onBack,
            action = {
                PadlasTextButton(
                    text = "CSV",
                    onClick = { exportLauncher.launch("padlas-naplo.csv") },
                )
            },
        )

        NumericText(
            text = "${Format.bytes(state.reclaimedBytes)} felszabadítva összesen",
            modifier = Modifier.padding(vertical = Space.s),
            large = true,
        )

        if (state.days.isEmpty() && !state.loading) {
            EmptyState(text = "Még nincs naplóbejegyzés.")
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            state.days.forEach { day ->
                item(key = day.label) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Space.l, bottom = Space.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionLabel(text = day.label)
                        NumericText(text = Format.bytes(day.bytes), color = Ink.TextTertiary)
                    }
                }
                items(
                    count = day.entries.size,
                    key = { index -> day.entries[index].id },
                ) { index ->
                    LogRow(entry = day.entries[index], onUndo = { viewModel.undo(day.entries[index]) })
                }
            }
        }

        PadlasButton(
            text = "Minden háttérmunka leállítása",
            onClick = viewModel::stopAllWork,
            destructive = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Space.m),
        )

        state.message?.let { message ->
            Text(
                text = message,
                style = PadlasTheme.type.label,
                color = Ink.TextSecondary,
                modifier = Modifier.padding(bottom = Space.s),
            )
            LaunchedEffect(message) { viewModel.dismissMessage() }
        }
    }
}

@Composable
private fun LogRow(entry: AuditEntry, onUndo: () -> Unit) {
    PadlasCard(modifier = Modifier.fillMaxWidth(), contentPadding = Space.m) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = entry.action.hungarian(),
                    style = PadlasTheme.type.body,
                    color = Ink.Text,
                )
                entry.note?.let { note ->
                    Text(text = note, style = PadlasTheme.type.micro, color = Ink.TextTertiary)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (entry.sizeBytes > 0) {
                    NumericText(text = Format.bytes(entry.sizeBytes))
                }
                if (entry.reversible && entry.action == AuditAction.TRASHED) {
                    PadlasTextButton(text = "Vissza", onClick = onUndo)
                }
            }
        }
    }
}

private fun AuditAction.hungarian(): String = when (this) {
    AuditAction.INDEXED -> "Beolvasás"
    AuditAction.CLASSIFIED -> "Besorolás"
    AuditAction.HASHED -> "Hash"
    AuditAction.OCR -> "Szövegfelismerés"
    AuditAction.ARCHIVED -> "Padlásra került"
    AuditAction.GHOSTED -> "Helyi másolat felszabadítva"
    AuditAction.RESTORED -> "Visszatöltve"
    AuditAction.TRASHED -> "Kukába került"
    AuditAction.DELETED -> "Véglegesen törölve"
    AuditAction.UNTRASHED -> "Kukából visszaállítva"
    AuditAction.TRANSCODED -> "Tömörítve"
    AuditAction.PROTECTED -> "Védetté téve"
    AuditAction.UNPROTECTED -> "Védelem levéve"
    AuditAction.RULE_RUN -> "Szabály lefutott"
    AuditAction.CARD_WRITTEN -> "Cédula készült"
    AuditAction.RECONCILED -> "Drive eltérés"
}
