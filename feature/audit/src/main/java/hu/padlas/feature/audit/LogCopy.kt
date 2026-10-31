package hu.padlas.feature.audit

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.RemoveModerator
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import hu.padlas.core.common.format.Format
import hu.padlas.core.common.text.UiText
import hu.padlas.core.common.text.uiPlural
import hu.padlas.core.common.text.uiText
import hu.padlas.core.database.entity.AuditAction
import hu.padlas.core.design.theme.Ink

internal data class ActionLook(val icon: ImageVector, val tint: Color)

/** What each log line says. A batch reads as "12 fájl …", a single file without a count. */
internal object LogCopy {

    fun look(action: AuditAction): ActionLook = when (action) {
        AuditAction.INDEXED -> ActionLook(Icons.Rounded.PhotoLibrary, Ink.Info)
        AuditAction.CLASSIFIED -> ActionLook(Icons.AutoMirrored.Rounded.Label, Ink.Accent)
        AuditAction.HASHED -> ActionLook(Icons.Rounded.ContentCopy, Ink.Accent)
        AuditAction.OCR -> ActionLook(Icons.Rounded.DocumentScanner, Ink.Info)
        AuditAction.ARCHIVED -> ActionLook(Icons.Rounded.CloudUpload, Ink.Info)
        AuditAction.GHOSTED -> ActionLook(Icons.Rounded.CloudDone, Ink.Success)
        AuditAction.RESTORED -> ActionLook(Icons.Rounded.CloudDownload, Ink.Info)
        AuditAction.TRASHED -> ActionLook(Icons.Rounded.Delete, Ink.Warning)
        AuditAction.DELETED -> ActionLook(Icons.Rounded.DeleteForever, Ink.Danger)
        AuditAction.UNTRASHED -> ActionLook(Icons.Rounded.RestoreFromTrash, Ink.Success)
        AuditAction.TRANSCODED -> ActionLook(Icons.Rounded.Compress, Ink.Accent)
        AuditAction.PROTECTED -> ActionLook(Icons.Rounded.Shield, Ink.Success)
        AuditAction.UNPROTECTED -> ActionLook(Icons.Rounded.RemoveModerator, Ink.Warning)
        AuditAction.RULE_RUN -> ActionLook(Icons.Rounded.AutoMode, Ink.Accent)
        AuditAction.CARD_WRITTEN -> ActionLook(Icons.Rounded.Description, Ink.TextSecondary)
        AuditAction.RECONCILED -> ActionLook(Icons.Rounded.SyncProblem, Ink.Warning)
    }

    fun title(action: AuditAction, count: Int): UiText {
        fun counted(@PluralsRes many: Int, @StringRes one: Int): UiText =
            if (count > 1) uiPlural(many, count, Format.count(count)) else uiText(one)
        return when (action) {
            AuditAction.INDEXED -> uiText(R.string.audit_action_indexed)
            AuditAction.CLASSIFIED -> counted(R.plurals.audit_action_classified_many, R.string.audit_action_classified)
            AuditAction.HASHED -> uiText(R.string.audit_action_hashed)
            AuditAction.OCR -> uiText(R.string.audit_action_ocr)
            AuditAction.ARCHIVED -> counted(R.plurals.audit_action_archived_many, R.string.audit_action_archived)
            AuditAction.GHOSTED -> counted(R.plurals.audit_action_ghosted_many, R.string.audit_action_ghosted)
            AuditAction.RESTORED -> counted(R.plurals.audit_action_restored_many, R.string.audit_action_restored)
            AuditAction.TRASHED -> counted(R.plurals.audit_action_trashed_many, R.string.audit_action_trashed)
            AuditAction.DELETED -> counted(R.plurals.audit_action_deleted_many, R.string.audit_action_deleted)
            AuditAction.UNTRASHED -> counted(R.plurals.audit_action_untrashed_many, R.string.audit_action_untrashed)
            AuditAction.TRANSCODED -> counted(R.plurals.audit_action_transcoded_many, R.string.audit_action_transcoded)
            AuditAction.PROTECTED -> counted(R.plurals.audit_action_protected_many, R.string.audit_action_protected)
            AuditAction.UNPROTECTED ->
                counted(R.plurals.audit_action_unprotected_many, R.string.audit_action_unprotected)
            AuditAction.RULE_RUN -> uiText(R.string.audit_action_rule_run)
            AuditAction.CARD_WRITTEN ->
                counted(R.plurals.audit_action_card_written_many, R.string.audit_action_card_written)
            AuditAction.RECONCILED -> uiText(R.string.audit_action_reconciled)
        }
    }

    /** A note is stored with its row in the words it was written in; only a missing one is ours. */
    fun subtitle(row: LogRow, time: String): UiText {
        val detail = row.note?.takeIf { it.isNotBlank() }?.let { note ->
            STORED_NOTES[note]?.let { uiText(it) } ?: UiText.Raw(friendlyNote(note))
        }
            ?: fallback(row.action)?.let { uiText(it) }
        return if (detail == null) UiText.Raw(time) else uiText(R.string.audit_row_subtitle, time, detail)
    }

    /** Only actions that moved or freed bytes carry a size; an index total is not a saving. */
    fun size(row: LogRow): String? {
        if (row.sizeBytes <= 0L) return null
        return when (row.action) {
            AuditAction.DELETED, AuditAction.GHOSTED, AuditAction.TRANSCODED -> "−" + Format.bytes(row.sizeBytes)
            AuditAction.TRASHED, AuditAction.ARCHIVED, AuditAction.RESTORED, AuditAction.RULE_RUN ->
                Format.bytes(row.sizeBytes)
            else -> null
        }
    }

    @StringRes
    private fun fallback(action: AuditAction): Int? = when (action) {
        AuditAction.CARD_WRITTEN -> R.string.audit_note_card_written
        AuditAction.GHOSTED -> R.string.audit_note_ghosted
        AuditAction.TRASHED -> R.string.audit_note_trashed
        else -> null
    }

    /**
     * Fixed notes the other modules store in Hungarian, whatever the app's language was:
     * they are keys, so they show in the language the log is read in.
     */
    private val STORED_NOTES: Map<String, Int> = mapOf(
        "Válogatás" to R.string.audit_note_sort,
        "Kuka" to R.string.audit_note_bin,
        "duplikátum-vesztes" to R.string.audit_note_extra_copy,
        "felesleges másolat" to R.string.audit_note_extra_copy,
        "Teljes beolvasás" to R.string.audit_note_full_scan,
        "Új és módosult fájlok" to R.string.audit_note_incremental,
        "Növekményes beolvasás" to R.string.audit_note_incremental,
        "Eltűnt fájlok egyeztetése" to R.string.audit_note_reconciled,
    )

    /** Notes written before the copy was reworded still use the old internal words. */
    private fun friendlyNote(note: String): String = note
        .replace("duplikátum-vesztes", "felesleges másolat")
        .replace("vesztes", "felesleges másolat")
        .replace("Padlásra sorolva", "Felhőbe sorolva")
        .replace("a padlásra", "a felhőbe")
        .replace("padlásra", "a felhőbe")
        .replace("a Kidobóban", "az Eldobható képek között")
        .replace("a Kidobóba", "az eldobhatók közé")
        .replace("efemer", "eldobható")
        .replace("Növekményes beolvasás", "Új és módosult fájlok")
}
