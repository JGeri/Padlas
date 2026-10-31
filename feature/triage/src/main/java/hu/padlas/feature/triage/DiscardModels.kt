package hu.padlas.feature.triage

import android.content.IntentSender
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import hu.padlas.core.common.text.UiText
import hu.padlas.core.database.entity.ShotKind

@Immutable
data class ShotItem(
    val id: Long,
    val model: Uri,
    val sizeBytes: Long,
    /** SECRET and still protected: not selectable until the user lifts the protection. */
    val locked: Boolean,
)

@Immutable
data class TriageGroup(
    val key: String,
    val kind: ShotKind?,
    val title: UiText,
    val hint: UiText,
    val items: List<ShotItem>,
    val locked: Boolean,
    /** Set for a group built from the app the shots came from instead of their text. */
    val source: ShotSource? = null,
)

/** Three sizes, three places the bytes are still sitting. Spec M9. */
@Immutable
data class TrashState(
    val systemBytes: Long = 0,
    val galleryBytes: Long = 0,
)

@Immutable
data class GroupTally(
    val discardCount: Int = 0,
    val discardBytes: Long = 0,
    val keptCount: Int = 0,
)

@Immutable
data class DiscardSelection(
    val kept: Set<Long> = emptySet(),
    val tallies: Map<String, GroupTally> = emptyMap(),
    val discardCount: Int = 0,
    val discardBytes: Long = 0,
) {
    fun tally(key: String): GroupTally = tallies[key] ?: GroupTally()
}

@Immutable
data class DiscardUiState(
    val loading: Boolean = true,
    val groups: List<TriageGroup> = emptyList(),
    val trash: TrashState = TrashState(),
    val committing: Boolean = false,
    /** The grid is falling away after a committed batch; the screen is about to close. */
    val falling: Boolean = false,
    val message: UiText? = null,
)

sealed interface DiscardEvent {
    data class Confirm(val intentSender: IntentSender) : DiscardEvent
}

/** Group titles: what the pictures are, in the words a person would use for them. */
@StringRes
internal fun kindTitle(kind: ShotKind?): Int = when (kind) {
    ShotKind.SECRET -> R.string.triage_kind_secret_title
    ShotKind.TICKET -> R.string.triage_kind_ticket_title
    ShotKind.RECEIPT -> R.string.triage_kind_receipt_title
    ShotKind.PLACE -> R.string.triage_kind_place_title
    ShotKind.CHAT -> R.string.triage_kind_chat_title
    ShotKind.OTHER -> R.string.triage_kind_other_title
    null -> R.string.triage_kind_untriaged_title
}

@StringRes
internal fun kindHint(kind: ShotKind?): Int = when (kind) {
    ShotKind.SECRET -> R.string.triage_kind_secret_hint
    ShotKind.TICKET -> R.string.triage_kind_ticket_hint
    ShotKind.RECEIPT -> R.string.triage_kind_receipt_hint
    ShotKind.PLACE -> R.string.triage_kind_place_hint
    ShotKind.CHAT -> R.string.triage_kind_chat_hint
    ShotKind.OTHER -> R.string.triage_kind_other_hint
    null -> R.string.triage_kind_untriaged_hint
}

internal fun groupKey(kind: ShotKind?): String = kind?.name ?: "UNTRIAGED"
