package hu.padlas.feature.dedup.ui

import android.net.Uri
import androidx.compose.runtime.Immutable
import hu.padlas.core.database.entity.DupKind
import hu.padlas.feature.dedup.hash.HashProgress

@Immutable
internal data class DupMemberUi(
    val assetId: Long,
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val isWinner: Boolean,
    /** INV-4: may sit in the group, may never be thrown away. */
    val isProtected: Boolean,
    val distance: Int,
)

@Immutable
internal data class DupGroupUi(
    val id: String,
    val kind: DupKind,
    val members: List<DupMemberUi>,
) {
    val winner: DupMemberUi? get() = members.firstOrNull { it.isWinner }

    val losers: List<DupMemberUi> get() = members.filter { !it.isWinner && !it.isProtected }

    val reclaimableBytes: Long get() = losers.sumOf { it.sizeBytes }
}

@Immutable
internal data class DedupSectionUi(
    val kind: DupKind,
    val groups: List<DupGroupUi> = emptyList(),
) {
    val loserCount: Int get() = groups.sumOf { it.losers.size }

    val reclaimableBytes: Long get() = groups.sumOf { it.reclaimableBytes }
}

@Immutable
internal data class DedupUiState(
    val loading: Boolean = true,
    val scanning: Boolean = false,
    val progress: HashProgress = HashProgress(),
    val exact: DedupSectionUi = DedupSectionUi(DupKind.EXACT),
    val burst: DedupSectionUi = DedupSectionUi(DupKind.BURST),
    val similar: DedupSectionUi = DedupSectionUi(DupKind.SIMILAR),
    /** MediaStore trash: already thrown away, still occupying space (INV-2). */
    val trashedBytes: Long = 0,
    val lastScanAt: Long = 0,
    /**
     * Groups whose losers sit behind the system dialog. The cards fold their losers away
     * while it is open and unfold them again if the user cancels.
     */
    val pendingGroupIds: Set<String> = emptySet(),
) {
    val sections: List<DedupSectionUi> get() = listOf(exact, burst, similar)

    val isEmpty: Boolean get() = exact.groups.isEmpty() && burst.groups.isEmpty() && similar.groups.isEmpty()

    val reclaimableBytes: Long get() = sections.sumOf { it.reclaimableBytes }

    val loserCount: Int get() = sections.sumOf { it.loserCount }

    val groupCount: Int get() = sections.sumOf { it.groups.size }

    val allGroupIds: List<String> get() = sections.flatMap { section -> section.groups.map { it.id } }
}
