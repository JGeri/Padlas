package hu.padlas.feature.sort

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.dao.AuditDao
import hu.padlas.core.database.dao.UploadJobDao
import hu.padlas.core.database.entity.AuditAction
import hu.padlas.core.database.entity.AuditEntry
import hu.padlas.core.database.entity.UploadJobEntity
import hu.padlas.core.database.entity.ValueClass
import hu.padlas.core.media.MediaOps
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One system dialog for the whole batch (INV-3); nothing is trashed before it returns. */
internal class TrashBatchRequest(
    val intentSender: IntentSender,
    val items: List<PendingTrash>,
) {
    val bytes: Long get() = items.sumOf { it.sizeBytes }
}

@HiltViewModel
internal class SortViewModel @Inject constructor(
    private val assetDao: AssetDao,
    private val uploadJobDao: UploadJobDao,
    private val auditDao: AuditDao,
    private val mediaOps: MediaOps,
    private val placeLabeler: PlaceLabeler,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(SortUiState())
    val state: StateFlow<SortUiState> = _state.asStateFlow()

    private val _trashRequest = MutableStateFlow<TrashBatchRequest?>(null)
    val trashRequest: StateFlow<TrashBatchRequest?> = _trashRequest.asStateFlow()

    /** The upload rows are already queued; the app module only has to wake the worker. */
    private val _archiveRequests = MutableSharedFlow<List<Long>>(extraBufferCapacity = 16)
    val archiveRequests: SharedFlow<List<Long>> = _archiveRequests.asSharedFlow()

    private val pending = mutableListOf<PendingTrash>()
    private val undoStack = ArrayDeque<Decision>()

    /** A write and its undo must reach the database in the order the user made them. */
    private val writeLock = Mutex()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val cutoff = System.currentTimeMillis() - FreshWindowMs
            val assets = withContext(io) { assetDao.sortingDeck(cutoff, DeckLimit) }
            val sessions = EventGrouper.group(assets.map { it.toSortCard() })
            _state.update {
                it.copy(
                    loading = false,
                    sessions = sessions,
                    sessionIndex = 0,
                    cardIndex = 0,
                    totalCount = assets.size,
                    decidedCount = 0,
                    finished = sessions.isEmpty(),
                )
            }
            labelPlaces(sessions)
        }
    }

    fun onSwipe(card: SortCard, direction: SwipeDirection) {
        when (direction) {
            SwipeDirection.LEFT -> stageTrash(card)
            SwipeDirection.RIGHT -> keep(card)
            SwipeDirection.UP -> sendToAttic(card)
            SwipeDirection.DOWN -> decideLater(card)
        }
        advance()
    }

    fun onProtect(card: SortCard) {
        val decision = push(Decision(card, DecisionKind.PROTECT))
        write { now ->
            assetDao.setProtected(listOf(card.id), true)
            decision.auditId = auditDao.insert(entry(now, AuditAction.PROTECTED, card, "Véglegesen védett"))
        }
        advance()
    }

    /** Ends the run: whatever is still staged goes into the one system dialog now. */
    fun finish() {
        _state.update { it.copy(finished = true) }
        requestTrash()
    }

    fun undo() {
        val decision = undoStack.removeLastOrNull() ?: return
        val card = decision.card
        when (decision.kind) {
            DecisionKind.TRASH -> pending.removeAll { it.id == card.id }

            DecisionKind.KEEP -> write {
                assetDao.defer(listOf(card.id), 0)
                assetDao.markTriaged(card.id, card.triagedAt)
            }

            DecisionKind.LATER -> write { assetDao.defer(listOf(card.id), 0) }

            DecisionKind.ATTIC -> write {
                assetDao.setValueClassManually(listOf(card.id), card.valueClass)
                uploadJobDao.delete(listOf(card.id))
                decision.auditId?.let { auditDao.markConsumed(it) }
            }

            DecisionKind.PROTECT -> write {
                assetDao.setProtected(listOf(card.id), false)
                decision.auditId?.let { auditDao.markConsumed(it) }
            }
        }
        stepBack(decision)
    }

    fun onTrashResult(confirmed: Boolean) {
        val request = _trashRequest.value ?: return
        _trashRequest.value = null
        if (!confirmed) {
            message("A törlés megszakadt. A köteg megmaradt.")
            return
        }
        val items = request.items
        val staged = items.mapTo(mutableSetOf()) { it.id }
        pending.removeAll { it.id in staged }
        undoStack.removeAll { it.kind == DecisionKind.TRASH && it.card.id in staged }
        publishBookkeeping()
        write { now ->
            assetDao.markTrashed(items.map { it.id }, now)
            auditDao.insertAll(
                items.map {
                    AuditEntry(
                        timestamp = now,
                        action = AuditAction.TRASHED,
                        assetId = it.id,
                        sizeBytes = it.sizeBytes,
                        reversible = true,
                        note = "Válogató",
                    )
                },
            )
        }
        message("${Format.count(items.size)} fájl a kukába került. Még mindig helyet foglal.")
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    // ---- decisions ----------------------------------------------------------

    private fun stageTrash(card: SortCard) {
        pending += PendingTrash(card.id, card.isVideo, card.sizeBytes)
        push(Decision(card, DecisionKind.TRASH))
        if (pending.size >= TrashBatchSize) requestTrash()
    }

    private fun keep(card: SortCard) {
        push(Decision(card, DecisionKind.KEEP))
        // The deck query filters on deferredAt, not on triagedAt, so parking the asset is
        // the only way to stop offering a kept picture again tomorrow.
        write { now ->
            assetDao.markTriaged(card.id, now)
            assetDao.defer(listOf(card.id), now)
        }
    }

    private fun sendToAttic(card: SortCard) {
        val decision = push(Decision(card, DecisionKind.ATTIC))
        write { now ->
            assetDao.setValueClassManually(listOf(card.id), ValueClass.IRREPLACEABLE)
            uploadJobDao.upsert(UploadJobEntity(assetId = card.id, totalBytes = card.sizeBytes, updatedAt = now))
            decision.auditId = auditDao.insert(entry(now, AuditAction.CLASSIFIED, card, "Padlásra sorolva"))
        }
        _archiveRequests.tryEmit(listOf(card.id))
    }

    private fun decideLater(card: SortCard) {
        push(Decision(card, DecisionKind.LATER))
        write { now -> assetDao.defer(listOf(card.id), now) }
    }

    private fun requestTrash() {
        if (pending.isEmpty() || _trashRequest.value != null) return
        val batch = pending.toList()
        val sender = runCatching {
            mediaOps.trashRequest(batch.map { mediaOps.uriFor(it.id, it.isVideo) })
        }.getOrNull()
        if (sender == null) {
            message("A rendszer kukája nem nyílt meg. A köteg megmaradt.")
            return
        }
        _trashRequest.value = TrashBatchRequest(sender, batch)
    }

    // ---- cursor -------------------------------------------------------------

    private fun advance() {
        _state.update { current ->
            val session = current.session ?: return@update current.copy(finished = true)
            var sessionIndex = current.sessionIndex
            var cardIndex = current.cardIndex + 1
            if (cardIndex >= session.cards.size) {
                sessionIndex++
                cardIndex = 0
            }
            current.copy(
                sessionIndex = sessionIndex,
                cardIndex = cardIndex,
                decidedCount = current.decidedCount + 1,
                finished = sessionIndex >= current.sessions.size,
                undoDepth = undoStack.size,
                pendingCount = pending.size,
                pendingBytes = pending.sumOf { it.sizeBytes },
            )
        }
        if (_state.value.finished) requestTrash()
    }

    private fun stepBack(decision: Decision) {
        _state.update { current ->
            var sessionIndex = current.sessionIndex
            var cardIndex = current.cardIndex
            when {
                cardIndex > 0 -> cardIndex--
                sessionIndex > 0 -> {
                    sessionIndex--
                    cardIndex = current.sessions[sessionIndex].cards.lastIndex.coerceAtLeast(0)
                }
            }
            current.copy(
                sessionIndex = sessionIndex,
                cardIndex = cardIndex,
                decidedCount = (current.decidedCount - 1).coerceAtLeast(0),
                finished = false,
                undoDepth = undoStack.size,
                undoTicket = current.undoTicket + 1,
                undoneDirection = decision.kind.direction,
                pendingCount = pending.size,
                pendingBytes = pending.sumOf { it.sizeBytes },
            )
        }
    }

    private fun publishBookkeeping() = _state.update { current ->
        current.copy(
            pendingCount = pending.size,
            pendingBytes = pending.sumOf { it.sizeBytes },
            undoDepth = undoStack.size,
        )
    }

    // ---- plumbing -----------------------------------------------------------

    private fun labelPlaces(sessions: List<SortSession>) {
        viewModelScope.launch {
            sessions.take(PlaceLookupLimit).forEachIndexed { index, session ->
                val located = session.cards.filter { it.latitude != null && it.longitude != null }
                if (located.isEmpty()) return@forEachIndexed
                val latitude = located.mapNotNull { it.latitude }.average()
                val longitude = located.mapNotNull { it.longitude }.average()
                val place = placeLabeler.label(latitude, longitude) ?: return@forEachIndexed
                _state.update { current ->
                    val existing = current.sessions.getOrNull(index)
                    if (existing == null || existing.id != session.id) return@update current
                    val updated = current.sessions.toMutableList()
                    updated[index] = existing.copy(place = place)
                    current.copy(sessions = updated)
                }
            }
        }
    }

    private fun push(decision: Decision): Decision {
        undoStack.addLast(decision)
        if (undoStack.size > UndoDepth) undoStack.removeFirst()
        return decision
    }

    private fun write(block: suspend (now: Long) -> Unit) {
        viewModelScope.launch(io) {
            writeLock.withLock { block(System.currentTimeMillis()) }
        }
    }

    private fun entry(now: Long, action: AuditAction, card: SortCard, note: String) = AuditEntry(
        timestamp = now,
        action = action,
        assetId = card.id,
        sizeBytes = card.sizeBytes,
        reversible = true,
        note = note,
    )

    private fun message(text: String) = _state.update { it.copy(message = text) }

    private class Decision(val card: SortCard, val kind: DecisionKind) {
        /** Filled in when the insert returns; undo marks that entry consumed. */
        var auditId: Long? = null
    }

    private enum class DecisionKind(val direction: SwipeDirection) {
        TRASH(SwipeDirection.LEFT),
        KEEP(SwipeDirection.RIGHT),
        ATTIC(SwipeDirection.UP),
        LATER(SwipeDirection.DOWN),
        PROTECT(SwipeDirection.RIGHT),
    }

    private companion object {
        const val DeckLimit = 400
        const val UndoDepth = 60
        const val PlaceLookupLimit = 12

        /** INV-4: DCIM/Camera from the last thirty days is never offered. */
        const val FreshWindowMs = 30L * 24 * 60 * 60 * 1000

        /** Small enough that one dialog stays readable, large enough to stay out of the way. */
        const val TrashBatchSize = 24
    }
}
