package hu.padlas.feature.triage

import android.content.IntentSender
import android.net.Uri
import androidx.compose.runtime.Immutable
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
    val title: String,
    val hint: String,
    val items: List<ShotItem>,
    val locked: Boolean,
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
    val message: String? = null,
)

sealed interface DiscardEvent {
    data class Confirm(val intentSender: IntentSender) : DiscardEvent
}

/** Group titles: what the pictures are, in the words a person would use for them. */
internal fun kindTitle(kind: ShotKind?): String = when (kind) {
    ShotKind.SECRET -> "Védett képek"
    ShotKind.TICKET -> "Jegyek és kódok"
    ShotKind.RECEIPT -> "Nyugták és számlák"
    ShotKind.PLACE -> "Címek és térképek"
    ShotKind.CHAT -> "Chatek"
    ShotKind.OTHER -> "Egyéb képernyőképek"
    null -> "Még át nem nézett képek"
}

internal fun kindHint(kind: ShotKind?): String = when (kind) {
    ShotKind.SECRET -> "Jelszót, kódot vagy bankadatot tartalmazhatnak, ezért nem ajánljuk fel őket törlésre."
    ShotKind.TICKET -> "Beszállókártyák, belépők, QR-kódok. A lejártakra már nincs szükség."
    ShotKind.RECEIPT -> "Vásárlások, utalások és számlák képernyőképei."
    ShotKind.PLACE -> "Címek, útvonalak és térképrészletek."
    ShotKind.CHAT -> "Beszélgetésekről készült képernyőképek."
    ShotKind.OTHER -> "Kevés szöveg, semmi fontos nem látszik rajtuk."
    null -> "A felismerés még nem futott le rajtuk, de eldobhatónak tűnnek."
}

internal fun groupKey(kind: ShotKind?): String = kind?.name ?: "UNTRIAGED"
