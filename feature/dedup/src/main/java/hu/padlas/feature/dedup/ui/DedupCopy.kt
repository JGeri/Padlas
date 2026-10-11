package hu.padlas.feature.dedup.ui

import hu.padlas.core.common.format.Format
import hu.padlas.core.database.entity.DupKind
import hu.padlas.feature.dedup.hash.HashPhase
import hu.padlas.feature.dedup.hash.HashProgress

/** Every string the screen shows. Active verbs, sentence case, numbers through [Format]. */
internal object DedupCopy {

    const val TITLE = "Duplikátumok"
    const val RECLAIMABLE = "Felszabadítható"
    const val KEEP_BADGE = "Marad"
    const val PROTECTED_BADGE = "Védett"
    const val PICK_KEEPER = "Koppints arra a képre, amelyiket megtartanád."
    const val GROUP_ACTION = "Kukába"
    const val SECTION_ACTION = "Mind kukába"
    const val RESCAN = "Keresés újra"
    const val FIRST_SCAN = "Keresés indítása"
    const val EMPTY_TITLE = "Nincs duplikátum"
    const val SCANNING_TITLE = "Duplikátumok keresése"
    const val SCANNING_TEXT = "Átnézzük a galériát. Ez néhány percig is eltarthat, közben nyugodtan bezárhatod."

    /** INV-2 said out loud: this is the lie every other cleaner tells. */
    const val TRASH_TITLE = "A kuka is helyet foglal"
    const val TRASH_TEXT = "A kukába tett fájlok még 30 napig a telefonon maradnak, utána a rendszer törli őket."

    fun sectionTitle(kind: DupKind): String = when (kind) {
        DupKind.EXACT -> "Azonos"
        DupKind.BURST -> "Sorozat"
        DupKind.SIMILAR -> "Hasonló"
    }

    fun sectionLead(kind: DupKind): String = when (kind) {
        DupKind.EXACT -> "Bájtra egyező másolatok. Egy példány marad, a többi nyugodtan mehet."
        DupKind.BURST -> "Sorozatfelvételek. A legélesebb kép marad."
        DupKind.SIMILAR -> "Hasonló, de nem egyforma képek. Csoportonként te döntöd el, melyik marad."
    }

    fun groupTitle(kind: DupKind, memberCount: Int): String = when (kind) {
        DupKind.EXACT -> "${Format.count(memberCount)} egyforma fájl"
        DupKind.BURST -> "${Format.count(memberCount)} képes sorozat"
        DupKind.SIMILAR -> "${Format.count(memberCount)} hasonló kép"
    }

    fun groupSubtitle(loserCount: Int, bytes: Long): String =
        "${Format.count(loserCount)} felesleges · ${Format.bytes(bytes)}"

    fun sectionTotals(groupCount: Int, loserCount: Int, bytes: Long): String =
        "${Format.count(groupCount)} csoport · ${Format.count(loserCount)} fájl · ${Format.bytes(bytes)}"

    fun heroCaption(loserCount: Int, groupCount: Int): String =
        "${Format.count(loserCount)} felesleges másolat ${Format.count(groupCount)} csoportban. " +
            "A „$KEEP_BADGE” jelölésű képek megmaradnak."

    fun primaryAction(loserCount: Int, bytes: Long): String =
        "${Format.count(loserCount)} fájl kukába · ${Format.bytes(bytes)}"

    fun trashedValue(bytes: Long): String = Format.bytes(bytes)

    fun topSubtitle(state: DedupUiState, now: Long): String = when {
        state.scanning -> "Keresés folyamatban"
        state.isEmpty -> lastScanSentence(state.lastScanAt, now)
        else -> "${Format.count(state.groupCount)} csoport · ${Format.bytes(state.reclaimableBytes)}"
    }

    fun scanProgress(progress: HashProgress): String {
        val phase = when (progress.phase) {
            HashPhase.QUICK -> "Gyors összevetés"
            HashPhase.FULL -> "Pontos ellenőrzés"
            HashPhase.PERCEPTUAL -> "Hasonló képek keresése"
            HashPhase.IDLE -> "Keresés indul"
            HashPhase.DONE -> "Csoportosítás"
        }
        if (progress.total <= 0) return phase
        return "$phase · ${Format.count(progress.done)} / ${Format.count(progress.total)}"
    }

    fun emptyText(lastScanAt: Long, now: Long): String =
        "Nem találtunk egyforma vagy nagyon hasonló képeket. ${lastScanSentence(lastScanAt, now)}"

    fun lastScanSentence(lastScanAt: Long, now: Long): String {
        if (lastScanAt <= 0L) return "Még nem kerestünk."
        val days = (now - lastScanAt) / DAY_MILLIS
        return when {
            days <= 0L -> "Utoljára ma kerestünk."
            days == 1L -> "Utoljára tegnap kerestünk."
            else -> "Utoljára ${Format.count(days)} napja kerestünk."
        }
    }

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
}
