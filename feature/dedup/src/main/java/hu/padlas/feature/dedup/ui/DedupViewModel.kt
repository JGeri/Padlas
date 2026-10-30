package hu.padlas.feature.dedup.ui

import android.content.Context
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.common.result.outcomeOf
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.dao.AuditDao
import hu.padlas.core.database.dao.DupDao
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.database.entity.AuditAction
import hu.padlas.core.database.entity.AuditEntry
import hu.padlas.core.database.entity.DupKind
import hu.padlas.core.database.model.DupGroupWithMembers
import hu.padlas.core.media.MediaOps
import hu.padlas.feature.dedup.group.isProtected
import hu.padlas.feature.dedup.group.toDupCandidate
import hu.padlas.feature.dedup.hash.HashPipeline
import hu.padlas.feature.dedup.work.DedupScheduler
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
internal class DedupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assetDao: AssetDao,
    private val dupDao: DupDao,
    private val auditDao: AuditDao,
    private val mediaOps: MediaOps,
    @IoDispatcher private val io: CoroutineDispatcher,
    hashPipeline: HashPipeline,
) : ViewModel() {

    private val pendingGroups = MutableStateFlow<Set<String>>(emptySet())
    private val trashedBytes = MutableStateFlow(0L)

    /** The batch waiting behind the system dialog. Written only from the main thread. */
    private var pending: PendingTrash? = null

    // A channel, not a shared flow: a request sent while the screen is between two
    // compositions must wait for it, not vanish and leave [pending] stuck.
    private val _trashRequests = Channel<IntentSender>(Channel.BUFFERED)
    val trashRequests: Flow<IntentSender> = _trashRequests.receiveAsFlow()

    /** Losers already sent to the bin from this screen, so a following round never asks twice. */
    private val trashed = HashSet<Long>()

    private val groups: Flow<List<DupGroupUi>> = dupDao.observeOpenGroups()
        .map { open -> open.toUi() }
        .flowOn(io)

    private val lastScanAt: Flow<Long> = auditDao.observeRecent(AUDIT_WINDOW)
        .map { entries -> entries.firstOrNull { it.action == AuditAction.HASHED }?.timestamp ?: 0L }
        .flowOn(io)

    private val local: Flow<Pair<Long, Set<String>>> = combine(trashedBytes, pendingGroups, ::Pair)

    private val scanning: Flow<Boolean> = DedupScheduler.observeScan(context)
        .catch { emit(false) }

    val state: StateFlow<DedupUiState> =
        combine(groups, hashPipeline.progress, scanning, lastScanAt, local) { open, progress, running, scannedAt, session ->
            val (trashed, waiting) = session
            DedupUiState(
                loading = false,
                scanning = running || progress.running,
                progress = progress,
                exact = DedupSectionUi(DupKind.EXACT, open.filter { it.kind == DupKind.EXACT }),
                burst = DedupSectionUi(DupKind.BURST, open.filter { it.kind == DupKind.BURST }),
                similar = DedupSectionUi(DupKind.SIMILAR, open.filter { it.kind == DupKind.SIMILAR }),
                trashedBytes = trashed,
                lastScanAt = scannedAt,
                pendingGroupIds = waiting,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), DedupUiState())

    init {
        refreshTrashedBytes()
    }

    fun rescan() {
        DedupScheduler.enqueueScan(context)
    }

    /** The similar section is the only one where the keeper is a human decision. */
    fun pickWinner(groupId: String, assetId: Long) {
        viewModelScope.launch(io) { outcomeOf { dupDao.setWinner(groupId, assetId) } }
    }

    /**
     * INV-3: one system dialog for the whole batch, whether that is one group or every
     * loser of a section. Nothing is written to the database here - the files are not gone
     * until the system says they are.
     */
    fun requestTrash(groupIds: List<String>) {
        if (groupIds.isEmpty() || pending != null) return
        val wanted = groupIds.toSet()
        val open = state.value.sections.flatMap { it.groups }
            .filter { it.id in wanted }
            .map { group -> group.id to group.losers.filter { it.assetId !in trashed } }
            .filter { (_, losers) -> losers.isNotEmpty() }
        if (open.isEmpty()) return
        val round = planTrashRound(open, MediaOps.MAX_REQUEST)
        pending = PendingTrash(
            groupIds = round.resolved,
            losers = round.losers.map { it.assetId to it.sizeBytes },
            rest = round.rest,
        )
        // A cut group is the first of the rest; its cards fold while the dialog is open too.
        pendingGroups.value = round.resolved.ifEmpty { round.rest.take(1) }.toSet()
        viewModelScope.launch {
            val sender = mediaOps.trashRequest(round.losers.map { it.uri })
            if (sender == null) {
                pending = null
                pendingGroups.value = emptySet()
                return@launch
            }
            _trashRequests.send(sender)
        }
    }

    fun onTrashResult(accepted: Boolean) {
        val batch = pending ?: return
        pending = null
        if (!accepted) {
            pendingGroups.value = emptySet()
            return
        }
        val ids = batch.losers.map { it.first }
        trashed += ids
        viewModelScope.launch {
            // The files are in the bin either way; a failed write (a full disk) is left for
            // the next scan to square instead of taking the screen down.
            withContext(io) {
                outcomeOf {
                    val now = System.currentTimeMillis()
                    ids.chunked(SQLITE_ARGUMENT_LIMIT).forEach { chunk -> assetDao.markTrashed(chunk, now) }
                    if (batch.groupIds.isNotEmpty()) dupDao.markResolvedAll(batch.groupIds, now)
                    auditDao.insertAll(
                        batch.losers.map { (assetId, sizeBytes) ->
                            AuditEntry(
                                timestamp = now,
                                action = AuditAction.TRASHED,
                                assetId = assetId,
                                sizeBytes = sizeBytes,
                                reversible = true,
                                note = "duplikátum-vesztes",
                            )
                        },
                    )
                }
                trashedBytes.value = mediaOps.trashedBytes()
            }
            requestTrash(batch.rest)
        }
    }

    private fun refreshTrashedBytes() {
        viewModelScope.launch(io) { trashedBytes.value = mediaOps.trashedBytes() }
    }

    private suspend fun List<DupGroupWithMembers>.toUi(): List<DupGroupUi> {
        val ids = flatMap { group -> group.members.map { it.assetId } }.distinct()
        val assets = ids.chunked(SQLITE_ARGUMENT_LIMIT)
            .flatMap { chunk -> assetDao.byIds(chunk) }
            .associateBy { it.mediaStoreId }
        val now = System.currentTimeMillis()
        return mapNotNull { group -> group.toUi(assets, now) }
    }

    private fun DupGroupWithMembers.toUi(assets: Map<Long, Asset>, now: Long): DupGroupUi? {
        val live = members.mapNotNull { member ->
            val asset = assets[member.assetId] ?: return@mapNotNull null
            if (asset.state == AssetState.TRASHED || asset.state == AssetState.GHOST) return@mapNotNull null
            DupMemberUi(
                assetId = asset.mediaStoreId,
                uri = mediaOps.uriFor(asset.mediaStoreId, asset.isVideo),
                displayName = asset.displayName,
                sizeBytes = asset.sizeBytes,
                isWinner = member.isWinner,
                // Re-checked here and not only in the grouper: a file can become a
                // favourite between the scan and this screen.
                isProtected = asset.toDupCandidate().isProtected(now),
                distance = member.distance,
            )
        }
        val ui = DupGroupUi(id = group.id, kind = group.kind, members = live)
        val hasWinner = ui.winner != null
        return if (live.size > 1 && hasWinner && ui.losers.isNotEmpty()) ui else null
    }

    private data class PendingTrash(
        /** Groups that are fully resolved once this round goes through. */
        val groupIds: List<String>,
        val losers: List<Pair<Long, Long>>,
        /** Groups asked for that did not fit in this round. */
        val rest: List<String>,
    )

    private companion object {
        const val SQLITE_ARGUMENT_LIMIT = 900
        const val AUDIT_WINDOW = 64
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
