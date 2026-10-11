package hu.padlas.feature.audit

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

    fun title(action: AuditAction, count: Int): String {
        val many = count > 1
        val n = Format.count(count)
        return when (action) {
            AuditAction.INDEXED -> "Galéria beolvasva"
            AuditAction.CLASSIFIED -> if (many) "$n fájl besorolva" else "Besorolva"
            AuditAction.HASHED -> "Duplikátumkeresés"
            AuditAction.OCR -> "Képernyőképek átnézve"
            AuditAction.ARCHIVED -> if (many) "$n fájl feltöltve a felhőbe" else "Feltöltve a felhőbe"
            AuditAction.GHOSTED -> if (many) "$n fájl csak a felhőben maradt" else "Csak a felhőben maradt"
            AuditAction.RESTORED -> if (many) "$n fájl visszatöltve" else "Visszatöltve a felhőből"
            AuditAction.TRASHED -> if (many) "$n fájl a kukába került" else "Kukába került"
            AuditAction.DELETED -> if (many) "$n fájl véglegesen törölve" else "Véglegesen törölve"
            AuditAction.UNTRASHED -> if (many) "$n fájl visszaállítva" else "Visszaállítva a kukából"
            AuditAction.TRANSCODED -> if (many) "$n videó tömörítve" else "Videó tömörítve"
            AuditAction.PROTECTED -> if (many) "$n fájl védett lett" else "Védetté téve"
            AuditAction.UNPROTECTED -> if (many) "$n fájl védelme feloldva" else "Védelem feloldva"
            AuditAction.RULE_RUN -> "Szabály lefutott"
            AuditAction.CARD_WRITTEN -> if (many) "$n adatlap mentve" else "Adatlap mentve"
            AuditAction.RECONCILED -> "Eltérés a Drive-val"
        }
    }

    fun subtitle(row: LogRow, time: String): String {
        val detail = row.note?.takeIf { it.isNotBlank() }?.let(::friendlyNote) ?: fallback(row.action)
        return if (detail == null) time else "$time · $detail"
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

    private fun fallback(action: AuditAction): String? = when (action) {
        AuditAction.CARD_WRITTEN -> "A törölt fájlok adatai megmaradnak"
        AuditAction.GHOSTED -> "Egy kis előnézet maradt a telefonon"
        AuditAction.TRASHED -> "30 napig visszaállítható"
        else -> null
    }

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
