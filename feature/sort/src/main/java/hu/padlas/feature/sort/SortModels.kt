package hu.padlas.feature.sort

import androidx.compose.runtime.Immutable
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.ValueClass

/** Four directions, four fates. Down is the only one that is not a decision. */
internal enum class SwipeDirection { LEFT, RIGHT, UP, DOWN }

/**
 * The deck never touches [Asset] rows while it draws: a flat, fully immutable card keeps
 * recomposition out of the 8,33 ms frame budget at 120 Hz.
 */
@Immutable
internal data class SortCard(
    val id: Long,
    val uri: String,
    val displayName: String,
    val bucketName: String,
    val sizeBytes: Long,
    val dateTaken: Long,
    val isVideo: Boolean,
    val durationMs: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val valueClass: ValueClass,
    val triagedAt: Long,
) {
    val sizeLabel: String get() = Format.bytes(sizeBytes)

    val durationLabel: String? get() = durationMs?.takeIf { it > 0 }?.let(Format::duration)
}

internal fun Asset.toSortCard(): SortCard = SortCard(
    id = mediaStoreId,
    uri = uri,
    displayName = displayName,
    bucketName = bucketName,
    sizeBytes = sizeBytes,
    dateTaken = dateTaken,
    isVideo = isVideo,
    durationMs = durationMs,
    latitude = latitude,
    longitude = longitude,
    valueClass = valueClass,
    triagedAt = triagedAt,
)

/**
 * One event: a run of shots with no gap longer than four hours, or one GPS cluster.
 * The header is what makes the deck feel like a shoebox instead of a queue.
 */
@Immutable
internal data class SortSession(
    val id: String,
    val cards: List<SortCard>,
    val period: String,
    val place: String? = null,
) {
    val bytes: Long get() = cards.sumOf { it.sizeBytes }

    /** "47 kép · Balaton · 2023 július" */
    val header: String
        get() = listOfNotNull("${Format.count(cards.size)} $noun", place ?: fallbackPlace, period)
            .joinToString(" · ")

    private val noun: String
        get() = when {
            cards.all { it.isVideo } -> "videó"
            cards.none { it.isVideo } -> "kép"
            else -> "elem"
        }

    /** Without a geocoded name the folder is the most honest label we have. */
    private val fallbackPlace: String?
        get() = cards.groupingBy { it.bucketName }.eachCount()
            .maxByOrNull { it.value }?.key
            ?.takeIf { it.isNotBlank() && !it.equals("Camera", ignoreCase = true) }
}

/** A left swipe only ever lands here. Bytes are kept so the footer can name the batch. */
@Immutable
internal data class PendingTrash(
    val id: Long,
    val isVideo: Boolean,
    val sizeBytes: Long,
)

@Immutable
internal data class SortUiState(
    val loading: Boolean = true,
    val sessions: List<SortSession> = emptyList(),
    val sessionIndex: Int = 0,
    val cardIndex: Int = 0,
    val decidedCount: Int = 0,
    val totalCount: Int = 0,
    val pendingCount: Int = 0,
    val pendingBytes: Long = 0,
    val undoDepth: Int = 0,
    val undoTicket: Long = 0,
    val undoneDirection: SwipeDirection? = null,
    val finished: Boolean = false,
    val message: String? = null,
) {
    val session: SortSession? get() = sessions.getOrNull(sessionIndex)

    /** At most three cards are ever composed, whatever the session size. */
    val visibleCards: List<SortCard>
        get() = session?.cards.orEmpty().let { cards ->
            if (cardIndex >= cards.size) emptyList() else cards.subList(cardIndex, minOf(cardIndex + 3, cards.size))
        }
}
