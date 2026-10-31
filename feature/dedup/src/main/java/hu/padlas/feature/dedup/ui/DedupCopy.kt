package hu.padlas.feature.dedup.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.entity.DupKind
import hu.padlas.feature.dedup.R
import hu.padlas.feature.dedup.hash.HashPhase
import hu.padlas.feature.dedup.hash.HashProgress

/**
 * The sentences the screen puts together from the module's strings. Numbers go through
 * [Format]; a count that changes the noun is a plural.
 */
internal object DedupCopy {

    @StringRes
    fun sectionTitle(kind: DupKind): Int = when (kind) {
        DupKind.EXACT -> R.string.dedup_section_exact
        DupKind.BURST -> R.string.dedup_section_burst
        DupKind.SIMILAR -> R.string.dedup_section_similar
    }

    @StringRes
    fun sectionLead(kind: DupKind): Int = when (kind) {
        DupKind.EXACT -> R.string.dedup_lead_exact
        DupKind.BURST -> R.string.dedup_lead_burst
        DupKind.SIMILAR -> R.string.dedup_lead_similar
    }

    @Composable
    @ReadOnlyComposable
    fun groupTitle(kind: DupKind, memberCount: Int): String {
        val id = when (kind) {
            DupKind.EXACT -> R.plurals.dedup_group_exact
            DupKind.BURST -> R.plurals.dedup_group_burst
            DupKind.SIMILAR -> R.plurals.dedup_group_similar
        }
        return pluralStringResource(id, memberCount, Format.count(memberCount))
    }

    @Composable
    @ReadOnlyComposable
    fun groupSubtitle(loserCount: Int, bytes: Long): String =
        pluralStringResource(R.plurals.dedup_group_extra, loserCount, Format.count(loserCount), Format.bytes(bytes))

    @Composable
    @ReadOnlyComposable
    fun sectionTotals(groupCount: Int, loserCount: Int, bytes: Long): String = listOf(
        groups(groupCount),
        pluralStringResource(R.plurals.dedup_files, loserCount, Format.count(loserCount)),
        Format.bytes(bytes),
    ).joinToString(SEPARATOR)

    @Composable
    @ReadOnlyComposable
    fun reviewTotals(groupCount: Int, memberCount: Int, markedCount: Int): String = listOf(
        groups(groupCount),
        pluralStringResource(R.plurals.dedup_pictures, memberCount, Format.count(memberCount)),
        stringResource(R.string.dedup_selected, Format.count(markedCount)),
    ).joinToString(SEPARATOR)

    @Composable
    @ReadOnlyComposable
    fun reviewSubtitle(markedCount: Int, bytes: Long): String = if (markedCount == 0) {
        stringResource(R.string.dedup_nothing_selected)
    } else {
        stringResource(R.string.dedup_marked, Format.count(markedCount), Format.bytes(bytes))
    }

    @Composable
    @ReadOnlyComposable
    fun heroCaption(exactLosers: Int, exactGroups: Int, reviewGroups: Int): String = buildList {
        if (exactGroups > 0) {
            add(
                stringResource(
                    R.string.dedup_hero_exact,
                    pluralStringResource(R.plurals.dedup_hero_copies, exactLosers, Format.count(exactLosers)),
                    pluralStringResource(R.plurals.dedup_hero_in_groups, exactGroups, Format.count(exactGroups)),
                ),
            )
        }
        if (reviewGroups > 0) {
            add(pluralStringResource(R.plurals.dedup_hero_review, reviewGroups, Format.count(reviewGroups)))
        }
        add(stringResource(R.string.dedup_hero_zoom))
    }.joinToString(" ")

    @Composable
    @ReadOnlyComposable
    fun topSubtitle(state: DedupUiState, now: Long): String = when {
        state.scanning -> stringResource(R.string.dedup_searching)
        state.isEmpty -> lastScanSentence(state.lastScanAt, now)
        else -> groups(state.groupCount) + SEPARATOR + Format.bytes(state.reclaimableBytes)
    }

    @Composable
    @ReadOnlyComposable
    fun scanProgress(progress: HashProgress): String {
        val phase = stringResource(
            when (progress.phase) {
                HashPhase.QUICK -> R.string.dedup_phase_quick
                HashPhase.FULL -> R.string.dedup_phase_full
                HashPhase.PERCEPTUAL -> R.string.dedup_phase_perceptual
                HashPhase.IDLE -> R.string.dedup_phase_idle
                HashPhase.DONE -> R.string.dedup_phase_done
            },
        )
        if (progress.total <= 0) return phase
        return "$phase$SEPARATOR${Format.count(progress.done)} / ${Format.count(progress.total)}"
    }

    @Composable
    @ReadOnlyComposable
    fun emptyText(lastScanAt: Long, now: Long): String =
        stringResource(R.string.dedup_empty_text, lastScanSentence(lastScanAt, now))

    @Composable
    @ReadOnlyComposable
    fun lastScanSentence(lastScanAt: Long, now: Long): String {
        if (lastScanAt <= 0L) return stringResource(R.string.dedup_last_scan_never)
        val days = (now - lastScanAt) / DAY_MILLIS
        return when {
            days <= 0L -> stringResource(R.string.dedup_last_scan_today)
            days == 1L -> stringResource(R.string.dedup_last_scan_yesterday)
            else -> pluralStringResource(R.plurals.dedup_last_scan_days, days.toInt(), Format.count(days))
        }
    }

    @Composable
    @ReadOnlyComposable
    private fun groups(count: Int): String = pluralStringResource(R.plurals.dedup_groups, count, Format.count(count))

    private const val SEPARATOR = " · "
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
}
