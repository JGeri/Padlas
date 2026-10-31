package hu.padlas.feature.sort.session

import android.content.IntentSender
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.padlas.core.common.format.Format
import hu.padlas.core.common.text.UiText
import hu.padlas.core.common.text.uiPlural
import hu.padlas.core.common.text.uiText
import hu.padlas.core.database.entity.SwipeChoice
import hu.padlas.core.database.model.SizeAndCount
import hu.padlas.feature.sort.DeleteBatch
import hu.padlas.feature.sort.DeleteMode
import hu.padlas.feature.sort.DeleteTally
import hu.padlas.feature.sort.FavoriteBatch
import hu.padlas.feature.sort.MediaFilter
import hu.padlas.feature.sort.R
import hu.padlas.feature.sort.SwipeAction
import hu.padlas.feature.sort.SwipeCard
import hu.padlas.feature.sort.SwipeDates
import hu.padlas.feature.sort.SwipeFilter
import hu.padlas.feature.sort.SwipePreferences
import hu.padlas.feature.sort.SwipeRepository
import hu.padlas.feature.sort.joinedByDots
import hu.padlas.feature.sort.withNextBatch
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class UndoEvent(val ticket: Long, val cardId: Long, val action: SwipeAction)

@Immutable
internal data class SessionUiState(
    val loading: Boolean = true,
    val title: UiText? = null,
    val cards: List<SwipeCard> = emptyList(),
    val index: Int = 0,
    val kept: Int = 0,
    val favorited: Int = 0,
    val deleted: Int = 0,
    val deletedBytes: Long = 0,
    val pending: SizeAndCount = SizeAndCount(0, 0),
    val canUndo: Boolean = false,
    val undo: UndoEvent? = null,
    val busy: Boolean = false,
    val showHint: Boolean = false,
    /** Set once the batch went through; the summary shows what it won back. */
    val freedBytes: Long = 0,
    val freedPermanently: Boolean = false,
    val unsyncedFavorites: Int = 0,
    val message: UiText? = null,
    val deleteMode: DeleteMode = DeleteMode.TRASH,
    val skipsSystemDialog: Boolean = false,
    /** The element this deck grew out of; the same key shrinks it back on close. */
    val heroKey: String? = null,
    /** Swiped cards whose files left mid-session: out of [cards], still part of the count. */
    val removed: Int = 0,
) {
    val total: Int get() = cards.size + removed
    val position: Int get() = index + removed
    val finished: Boolean get() = !loading && index >= cards.size
    val current: SwipeCard? get() = cards.getOrNull(index)

    /** The card under the finger and the two behind it; nothing else is ever composed. */
    val window: List<SwipeCard> get() = if (index >= cards.size) emptyList() else cards.subList(index, minOf(index + 3, cards.size))
}

internal sealed interface SessionEvent {
    data class Launch(val sender: IntentSender, val kind: RequestKind) : SessionEvent
}

internal enum class RequestKind { DELETE, FAVORITE }

@HiltViewModel
internal class SwipeSessionViewModel @Inject constructor(
    private val repository: SwipeRepository,
    private val preferences: SwipePreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionUiState(heroKey = preferences.heroKey))
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private val _events = Channel<SessionEvent>(Channel.BUFFERED)
    val events: Flow<SessionEvent> = _events.receiveAsFlow()

    private val history = ArrayDeque<Swiped>()
    private val sessionFavorites = LinkedHashSet<Long>()
    private var pendingBatch: DeleteBatch? = null
    private val tally = DeleteTally()
    private var pendingFavorites: FavoriteBatch? = null
    private var autoDeleteDone = false
    private var undoTicket = 0L

    init {
        viewModelScope.launch {
            repository.pending.collect { pending -> _state.update { it.copy(pending = pending) } }
        }
        viewModelScope.launch {
            preferences.deleteMode.collect { mode -> _state.update { it.copy(deleteMode = mode) } }
        }
        load()
    }

    fun load() {
        val filter = preferences.lastFilter ?: SwipeFilter()
        val hero = preferences.heroKey
        _state.update { SessionUiState(pending = it.pending, deleteMode = it.deleteMode, heroKey = hero) }
        history.clear()
        sessionFavorites.clear()
        autoDeleteDone = false
        // Read ahead by the screen that opened the deck: set synchronously, so the very first
        // frame already shows the cards the tapped tile grows into.
        val prepared = repository.takePrepared(filter)
        if (prepared != null) {
            dealt(filter, prepared)
            return
        }
        viewModelScope.launch { dealt(filter, repository.deck(filter)) }
    }

    private fun dealt(filter: SwipeFilter, cards: List<SwipeCard>) = _state.update {
        it.copy(
            loading = false,
            title = titleOf(filter),
            cards = cards,
            index = 0,
            showHint = !preferences.hintSeen && cards.isNotEmpty(),
            skipsSystemDialog = repository.canSkipSystemDialog(),
        )
    }

    fun dismissHint() {
        preferences.hintSeen = true
        _state.update { it.copy(showHint = false) }
    }

    fun onSwiped(card: SwipeCard, action: SwipeAction) {
        val current = _state.value
        if (current.current?.id != card.id) return
        history.addLast(Swiped(card, action))
        if (history.size > UNDO_DEPTH) history.removeFirst()
        if (action == SwipeAction.FAVORITE) sessionFavorites += card.id
        _state.update {
            it.copy(
                index = it.index + 1,
                kept = it.kept + if (action == SwipeAction.KEEP) 1 else 0,
                favorited = it.favorited + if (action == SwipeAction.FAVORITE) 1 else 0,
                deleted = it.deleted + if (action == SwipeAction.DELETE) 1 else 0,
                deletedBytes = it.deletedBytes + if (action == SwipeAction.DELETE) card.sizeBytes else 0,
                canUndo = true,
                showHint = false,
            )
        }
        if (current.showHint) preferences.hintSeen = true
        viewModelScope.launch {
            if (!repository.record(card.id, action)) _state.update { it.copy(message = uiText(NOT_SAVED)) }
            if (_state.value.finished) onFinished()
        }
    }

    fun undo() {
        val last = history.removeLastOrNull() ?: return
        val card = last.card
        if (last.action == SwipeAction.FAVORITE) sessionFavorites -= card.id
        undoTicket++
        _state.update {
            it.copy(
                index = (it.index - 1).coerceAtLeast(0),
                kept = it.kept - if (last.action == SwipeAction.KEEP) 1 else 0,
                favorited = it.favorited - if (last.action == SwipeAction.FAVORITE) 1 else 0,
                deleted = it.deleted - if (last.action == SwipeAction.DELETE) 1 else 0,
                deletedBytes = it.deletedBytes - if (last.action == SwipeAction.DELETE) card.sizeBytes else 0,
                canUndo = history.isNotEmpty(),
                undo = UndoEvent(undoTicket, card.id, last.action),
            )
        }
        viewModelScope.launch { repository.restore(card.id, card.previous) }
    }

    /** "Törlés most": everything marked so far leaves through one system dialog. */
    fun deleteNow() {
        if (_state.value.busy || pendingBatch != null) return
        tally.reset()
        nextRound()
    }

    private fun nextRound() {
        if (_state.value.busy || pendingBatch != null) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val pending = repository.pendingNow()
            val batch = repository.deleteRequest(pending)
            if (batch == null) {
                _state.update {
                    it.copy(busy = false, message = uiText(if (pending.isEmpty()) R.string.sort_session_nothing_to_delete else REFUSED))
                }
                return@launch
            }
            pendingBatch = batch
            _events.send(SessionEvent.Launch(batch.intentSender, RequestKind.DELETE))
        }
    }

    fun onRequestResult(kind: RequestKind, confirmed: Boolean) {
        when (kind) {
            RequestKind.DELETE -> onDeleteResult(confirmed)
            RequestKind.FAVORITE -> onFavoriteResult(confirmed)
        }
    }

    fun syncFavorites() {
        if (pendingFavorites != null) return
        viewModelScope.launch {
            val batch = repository.favoriteRequest(sessionFavorites.toList()) ?: run {
                _state.update { it.copy(unsyncedFavorites = 0) }
                return@launch
            }
            pendingFavorites = batch
            _events.send(SessionEvent.Launch(batch.intentSender, RequestKind.FAVORITE))
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun onDeleteResult(confirmed: Boolean) {
        val batch = pendingBatch
        pendingBatch = null
        if (batch == null) {
            _state.update { it.copy(busy = false) }
            return
        }
        if (!confirmed) {
            _state.update { it.copy(busy = false, message = uiText(R.string.sort_session_cancelled)) }
            return
        }
        // A card whose file already left cannot be swiped back or dealt again: it leaves the
        // deck, so undo lands on the last card that is still there and a card marked in an
        // earlier session never comes up after its file is gone.
        val gone = batch.ids.toSet()
        history.removeAll { it.card.id in gone }
        _state.update { current ->
            val behind = current.cards.take(current.index).count { it.id in gone }
            current.copy(
                cards = current.cards.filterNot { it.id in gone },
                index = current.index - behind,
                removed = current.removed + behind,
                canUndo = history.isNotEmpty(),
            )
        }
        viewModelScope.launch {
            val recorded = repository.onDeleted(batch)
            val text = tally.add(batch)
            _state.update {
                it.copy(
                    busy = false,
                    freedBytes = it.freedBytes + batch.bytes,
                    freedPermanently = batch.permanent,
                    message = when {
                        !recorded -> uiText(NOT_RECORDED)
                        batch.truncated -> text.withNextBatch()
                        else -> text
                    },
                )
            }
            // More was marked than one dialog carries: the next round follows on its own.
            // Each round clears its own decisions, so the pile shrinks until it is empty.
            if (recorded && batch.truncated) {
                nextRound()
            } else if (_state.value.finished) {
                offerFavoriteSync()
            }
        }
    }

    private fun onFavoriteResult(confirmed: Boolean) {
        val batch = pendingFavorites
        pendingFavorites = null
        if (batch == null || !confirmed) return
        viewModelScope.launch {
            repository.onFavorited(batch)
            sessionFavorites -= batch.ids.toSet()
            _state.update {
                it.copy(
                    unsyncedFavorites = sessionFavorites.size,
                    message = uiPlural(R.plurals.sort_session_favorites_synced, batch.ids.size, Format.count(batch.ids.size)),
                )
            }
        }
    }

    private suspend fun onFinished() {
        if (autoDeleteDone) return
        autoDeleteDone = true
        val pending = repository.pendingNow()
        if (pending.isNotEmpty() && preferences.deleteAtEnd.value) {
            deleteNow()
        } else {
            offerFavoriteSync()
        }
    }

    /** Without "manage media" the favourite request shows its own dialog, so it waits for a tap. */
    private fun offerFavoriteSync() {
        _state.update { it.copy(unsyncedFavorites = sessionFavorites.size) }
        if (sessionFavorites.isNotEmpty() && repository.canSkipSystemDialog()) syncFavorites()
    }

    private fun titleOf(filter: SwipeFilter): UiText {
        val months = filter.months.sortedDescending()
        val media = when (filter.media) {
            MediaFilter.ALL -> null
            MediaFilter.PHOTOS -> uiText(R.string.sort_title_photos)
            MediaFilter.VIDEOS -> uiText(R.string.sort_title_videos)
        }
        val folders = uiPlural(R.plurals.sort_title_folders, filter.buckets.size, Format.count(filter.buckets.size))
        val scope = when {
            months.size == 1 -> UiText.Raw(SwipeDates.month(months.first()))
            months.size > 1 -> uiPlural(R.plurals.sort_title_months, months.size, Format.count(months.size))
            filter.buckets.isNotEmpty() -> folders
            else -> uiText(R.string.sort_title_whole_gallery)
        }
        val extraFolders = if (months.isNotEmpty() && filter.buckets.isNotEmpty()) folders else null
        return listOfNotNull(scope, extraFolders, media).joinedByDots()
    }

    private data class Swiped(val card: SwipeCard, val action: SwipeAction)

    private companion object {
        const val UNDO_DEPTH = 200
        val REFUSED = R.string.sort_session_refused
        val NOT_SAVED = R.string.sort_session_not_saved
        val NOT_RECORDED = R.string.sort_not_recorded
    }
}

@StringRes
internal fun SwipeChoice.label(): Int = when (this) {
    SwipeChoice.KEEP -> R.string.sort_kept
    SwipeChoice.DELETE -> R.string.sort_marked_for_deletion
    SwipeChoice.FAVORITE -> R.string.sort_favorite
}
