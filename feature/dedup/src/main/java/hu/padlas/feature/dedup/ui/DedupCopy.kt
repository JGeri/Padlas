package hu.padlas.feature.dedup.ui

import hu.padlas.core.common.format.Format
import hu.padlas.core.database.entity.DupKind
import hu.padlas.feature.dedup.hash.HashPhase
import hu.padlas.feature.dedup.hash.HashProgress

/** Every string the screen shows. Active verbs, sentence case, numbers through [Format]. */
internal object DedupCopy {

    const val TITLE = "Duplikátumok"
    const val RECLAIMABLE = "Felszabadítható"
    const val WINNER_BADGE = "Marad"
    const val PICK_KEEPER = "Koppints arra, amelyik marad."
    const val RESCAN = "Újra"
    const val FIRST_SCAN = "Beolvasás"

    /** INV-2 said out loud: this is the lie every other cleaner tells. */
    const val TRASH_NOTICE =
        "A kukába tett fájlok a rendszer kukájában maradnak, és 30 napig helyet foglalnak."

    fun sectionTitle(kind: DupKind): String = when (kind) {
        DupKind.EXACT -> "Azonos"
        DupKind.BURST -> "Sorozat"
        DupKind.SIMILAR -> "Hasonló"
    }

    fun sectionLead(kind: DupKind): String = when (kind) {
        DupKind.EXACT -> "Bájtra egyezik. Csak megerősítés kell."
        DupKind.BURST -> "Sorozatfelvétel. A legélesebb marad, egy koppintás elfogadja."
        DupKind.SIMILAR -> "Hasonló kompozíció. Csoportonként egy döntés."
    }

    fun groupAction(kind: DupKind): String = when (kind) {
        DupKind.EXACT -> "Megerősítem"
        DupKind.BURST -> "Elfogadom"
        DupKind.SIMILAR -> "A többi a kukába"
    }

    fun groupSummary(memberCount: Int, loserCount: Int): String =
        "${Format.count(memberCount)} fájl, ${Format.count(loserCount)} vesztes"

    fun batchAction(loserCount: Int): String = "Mind a ${Format.count(loserCount)} vesztes a kukába"

    fun sectionTotals(loserCount: Int, bytes: Long): String =
        "${Format.count(loserCount)} vesztes · ${Format.bytes(bytes)}"

    fun trashedNotice(bytes: Long): String =
        "A kukában ${Format.bytes(bytes)} vár az ürítésre."

    fun scanProgress(progress: HashProgress): String {
        val phase = when (progress.phase) {
            HashPhase.QUICK -> "Gyors ujjlenyomat"
            HashPhase.FULL -> "Teljes ellenőrzés"
            HashPhase.PERCEPTUAL -> "Képi ujjlenyomat"
            HashPhase.IDLE -> "Beolvasás indul"
            HashPhase.DONE -> "Csoportosítás"
        }
        if (progress.total <= 0) return phase
        return "$phase · ${Format.count(progress.done)} / ${Format.count(progress.total)}"
    }

    /** Empty state is an instruction, not a mood. */
    fun emptyState(lastScanAt: Long, now: Long): String = buildString {
        append("Nincs duplikátum. ")
        append(lastScanSentence(lastScanAt, now))
    }

    fun lastScanSentence(lastScanAt: Long, now: Long): String {
        if (lastScanAt <= 0L) return "A beolvasás még nem futott."
        val days = (now - lastScanAt) / DAY_MILLIS
        return when {
            days <= 0L -> "A beolvasás ma futott."
            days == 1L -> "A beolvasás tegnap futott."
            else -> "A beolvasás ${Format.count(days)} napja futott."
        }
    }

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
}
