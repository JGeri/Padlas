package hu.padlas.feature.triage

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.sqlite.db.SimpleSQLiteQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.padlas.core.common.format.Format
import hu.padlas.core.common.result.Outcome
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.database.entity.ShotKind
import hu.padlas.core.database.model.ShotKindAggregate
import hu.padlas.core.media.MediaOps
import hu.padlas.core.media.OneDriveGuard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Inverse selection: everything in the ephemeral set is already on its way out, and the
 * only thing the user does is tap the survivors. A 4 000 item set is then a scroll and a
 * few taps instead of 4 000 decisions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class DiscardViewModel @Inject constructor(
    private val assetDao: AssetDao,
    private val committer: DiscardCommitter,
    private val mediaOps: MediaOps,
    private val oneDriveGuard: OneDriveGuard,
) : ViewModel() {

    private val reload = MutableStateFlow(0)
    private val kept = MutableStateFlow<Set<Long>>(emptySet())
    private val committing = MutableStateFlow(false)
    private val falling = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private val eventChannel = Channel<DiscardEvent>(Channel.BUFFERED)
    val events: Flow<DiscardEvent> = eventChannel.receiveAsFlow()

    private var pending: PendingDelete? = null

    private val groups: StateFlow<List<TriageGroup>> =
        combine(assetDao.observeByShotKind(), reload) { aggregates, _ -> aggregates }
            .mapLatest { aggregates -> loadGroups(aggregates) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    private val trash: Flow<TrashState> = reload.map {
        TrashState(
            systemBytes = runCatching { mediaOps.trashedBytes() }.getOrDefault(0L),
            galleryBytes = runCatching { oneDriveGuard.samsungGalleryTrashBytes() }.getOrDefault(0L),
        )
    }

    val state: StateFlow<DiscardUiState> =
        combine(groups, trash, committing, falling, message) { loaded, bins, busy, leaving, note ->
            DiscardUiState(
                loading = false,
                groups = loaded,
                trash = bins,
                committing = busy,
                falling = leaving,
                message = note,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), DiscardUiState())

    val selection: StateFlow<DiscardSelection> =
        combine(groups, kept) { loaded, keepers -> tally(loaded, keepers) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), DiscardSelection())

    fun toggleKeep(id: Long) {
        kept.update { current -> if (id in current) current - id else current + id }
    }

    fun keepAll(key: String) {
        val ids = groups.value.firstOrNull { it.key == key }?.items?.map { it.id } ?: return
        kept.update { it + ids }
    }

    fun discardAll(key: String) {
        val group = groups.value.firstOrNull { it.key == key } ?: return
        val ids = group.items.filterNot { it.locked }.map { it.id }.toSet()
        kept.update { it - ids }
    }

    /**
     * INV-1c: taking a SECRET shot out of protection is a separate, deliberate act. Once
     * unlocked it lands in the keep set anyway, so the user has to tap it a second time
     * to actually throw it away.
     */
    fun unlock(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            assetDao.setProtected(ids, false)
            kept.update { it + ids }
            reload.update { it + 1 }
        }
    }

    fun requestDelete() {
        if (committing.value || falling.value) return
        val ids = discardIds()
        if (ids.isEmpty()) return
        committing.value = true
        viewModelScope.launch {
            when (val outcome = committer.prepare(ids)) {
                is Outcome.Success -> {
                    pending = outcome.value
                    eventChannel.send(DiscardEvent.Confirm(outcome.value.intentSender))
                }

                is Outcome.Failure -> {
                    committing.value = false
                    message.value = outcome.message
                }
            }
        }
    }

    fun onDeleteResult(confirmed: Boolean) {
        val batch = pending
        pending = null
        if (!confirmed || batch == null) {
            committing.value = false
            message.value = if (batch != null) "A törlés elmaradt. Semmi nem változott." else null
            return
        }
        viewModelScope.launch {
            val bytes = committer.finish(batch)
            kept.update { it - batch.ids.toSet() }
            committing.value = false
            message.value = "${Format.count(batch.count)} fájl törölve · ${Format.bytes(bytes)} szabadult fel"
            falling.value = true
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    private fun discardIds(): List<Long> {
        val keptNow = kept.value
        return groups.value.flatMap { group ->
            group.items.filter { !it.locked && it.id !in keptNow }.map { it.id }
        }
    }

    private suspend fun loadGroups(aggregates: List<ShotKindAggregate>): List<TriageGroup> {
        val loaded = ArrayList<TriageGroup>(aggregates.size)
        for (aggregate in aggregates) {
            val kind = aggregate.shotKind
            val assets = if (kind == ShotKind.SECRET) secretAssets() else assetDao.ephemeralOfKind(kind)
            if (assets.isEmpty()) continue
            loaded += TriageGroup(
                key = groupKey(kind),
                kind = kind,
                title = kindTitle(kind),
                hint = kindHint(kind),
                items = assets.map { it.toItem() },
                locked = kind == ShotKind.SECRET,
            )
        }
        // The DAO already orders by reclaimable bytes; this only pushes the two groups
        // nobody can act on to the bottom, and the sort is stable.
        return loaded.sortedBy { order(it.kind) }
    }

    /**
     * `ephemeralOfKind` deliberately hides protected rows, and a SECRET shot is always
     * protected - so the locked group has to be read past that filter to be shown at all.
     */
    private suspend fun secretAssets(): List<Asset> = assetDao.rawAssets(
        SimpleSQLiteQuery(
            "SELECT * FROM asset WHERE valueClass = 'EPHEMERAL' AND shotKind = 'SECRET' " +
                "AND state IN ('LOCAL', 'PROTECTED') ORDER BY dateTaken DESC",
        ),
    )

    private fun tally(loaded: List<TriageGroup>, keepers: Set<Long>): DiscardSelection {
        val tallies = HashMap<String, GroupTally>(loaded.size)
        var count = 0
        var bytes = 0L
        for (group in loaded) {
            var discardCount = 0
            var discardBytes = 0L
            var keptCount = 0
            for (item in group.items) {
                if (item.locked || item.id in keepers) {
                    keptCount++
                } else {
                    discardCount++
                    discardBytes += item.sizeBytes
                }
            }
            tallies[group.key] = GroupTally(discardCount, discardBytes, keptCount)
            count += discardCount
            bytes += discardBytes
        }
        return DiscardSelection(keepers, tallies, count, bytes)
    }

    private fun Asset.toItem(): ShotItem = ShotItem(
        id = mediaStoreId,
        model = Uri.parse(uri),
        sizeBytes = sizeBytes,
        locked = protectedManually || state == AssetState.PROTECTED,
    )

    private fun order(kind: ShotKind?): Int = when (kind) {
        ShotKind.SECRET -> 2
        null -> 1
        else -> 0
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
