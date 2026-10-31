package hu.padlas.feature.triage

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.sqlite.db.SimpleSQLiteQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.padlas.core.common.format.Format
import hu.padlas.core.common.result.outcomeOf
import hu.padlas.core.common.text.UiText
import hu.padlas.core.common.text.uiPlural
import hu.padlas.core.common.text.uiText
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
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
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
    private val installedApps: InstalledApps,
) : ViewModel() {

    private val reload = MutableStateFlow(0)
    private val kept = MutableStateFlow<Set<Long>>(emptySet())

    /** Unrecognised items already offered once; a later reload must not re-add them to keep. */
    private val seenUnrecognised = HashSet<Long>()
    private val committing = MutableStateFlow(false)
    private val falling = MutableStateFlow(false)
    private val message = MutableStateFlow<UiText?>(null)

    private val eventChannel = Channel<DiscardEvent>(Channel.BUFFERED)
    val events: Flow<DiscardEvent> = eventChannel.receiveAsFlow()

    private var pending: PendingDelete? = null

    /** What the rounds of one delete have freed so far; the last round reports the total. */
    private var doneCount = 0
    private var doneBytes = 0L

    /**
     * Null until the first load lands, so the screen never flashes its empty state.
     * Conflated, not latest-wins: while the classifier writes, the aggregates change every
     * few milliseconds, and cancelling each load for the next one means none ever lands.
     */
    private val groups: StateFlow<List<TriageGroup>?> =
        combine(assetDao.observeByShotKind(), reload) { aggregates, _ -> aggregates }
            .conflate()
            .map<List<ShotKindAggregate>, List<TriageGroup>?> { aggregates ->
                loadGroups(aggregates).also(::keepStartKept)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    private val trash: Flow<TrashState> = reload.map {
        TrashState(
            systemBytes = runCatching { mediaOps.trashedBytes() }.getOrDefault(0L),
            galleryBytes = runCatching { oneDriveGuard.samsungGalleryTrashBytes() }.getOrDefault(0L),
        )
    }

    val state: StateFlow<DiscardUiState> =
        combine(groups, trash, committing, falling, message) { loaded, bins, busy, leaving, note ->
            DiscardUiState(
                loading = loaded == null,
                groups = loaded.orEmpty(),
                trash = bins,
                committing = busy,
                falling = leaving,
                message = note,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), DiscardUiState())

    val selection: StateFlow<DiscardSelection> =
        combine(groups, kept) { loaded, keepers -> tally(loaded.orEmpty(), keepers) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), DiscardSelection())

    fun toggleKeep(id: Long) {
        kept.update { current -> if (id in current) current - id else current + id }
    }

    fun keepAll(key: String) {
        val ids = groups.value.orEmpty().firstOrNull { it.key == key }?.items?.map { it.id } ?: return
        kept.update { it + ids }
    }

    fun discardAll(key: String) {
        val group = groups.value.orEmpty().firstOrNull { it.key == key } ?: return
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
            if (outcomeOf { ids.chunked(SQLITE_ARGUMENT_LIMIT).forEach { assetDao.setProtected(it, false) } }.isSuccess) {
                kept.update { it + ids }
                reload.update { it + 1 }
            } else {
                message.value = uiText(R.string.triage_unlock_failed)
            }
        }
    }

    fun requestDelete() {
        if (committing.value || falling.value) return
        doneCount = 0
        doneBytes = 0L
        nextRound()
    }

    private fun nextRound() {
        if (committing.value || falling.value) return
        val ids = discardIds()
        if (ids.isEmpty()) return
        committing.value = true
        viewModelScope.launch {
            when (val prepared = committer.prepare(ids)) {
                is Prepared.Ready -> {
                    pending = prepared.batch
                    eventChannel.send(DiscardEvent.Confirm(prepared.batch.intentSender))
                }

                is Prepared.Failed -> {
                    committing.value = false
                    message.value = prepared.reason
                }
            }
        }
    }

    fun onDeleteResult(confirmed: Boolean) {
        val batch = pending
        pending = null
        if (!confirmed || batch == null) {
            committing.value = false
            message.value = if (batch != null) uiText(R.string.triage_nothing_deleted) else null
            return
        }
        viewModelScope.launch {
            val bytes = committer.finish(batch)
            kept.update { it - batch.ids.toSet() }
            committing.value = false
            if (bytes == null) {
                message.value = uiText(R.string.triage_deleted_not_recorded)
                return@launch
            }
            doneCount += batch.count
            doneBytes += bytes
            if (batch.more) {
                // More was marked than one dialog carries. The finished rows are gone from
                // the index, so the next round can only ever pick up what is left.
                message.value = uiPlural(R.plurals.triage_deleted_next_batch, doneCount, Format.count(doneCount))
                nextRound()
                return@launch
            }
            message.value = uiPlural(
                R.plurals.triage_deleted_freed,
                doneCount,
                Format.count(doneCount),
                Format.bytes(doneBytes),
            )
            falling.value = true
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    private fun discardIds(): List<Long> {
        val keptNow = kept.value
        return groups.value.orEmpty().flatMap { group ->
            group.items.filter { !it.locked && it.id !in keptNow }.map { it.id }
        }
    }

    /**
     * Pictures the recognition pass has not read yet are only guessed to be throwaway from
     * their folder, so they start on the keep side: thousands of them must never go out
     * with one tap on a button the user did not mean for them. Social and media shots are
     * certain about their app but may still be memories, so they start kept as well.
     */
    private fun keepStartKept(loaded: List<TriageGroup>) {
        val fresh = loaded
            .filter { group -> group.source?.startsKept ?: (group.kind == null) }
            .flatMap { group -> group.items.map { it.id } }
            .filter { seenUnrecognised.add(it) }
        if (fresh.isNotEmpty()) kept.update { it + fresh }
    }

    /**
     * Shots whose file name says which app they came from, and that app is a navigation,
     * home-screen, game, media or social one, are grouped by the app: that is certain the
     * moment the file exists, while the text recognition takes a while to get round to
     * seven thousand pictures and reads a Waze screen as an address. SECRET still wins:
     * anything the recognition locked stays locked, whatever app it came from.
     */
    private suspend fun loadGroups(aggregates: List<ShotKindAggregate>): List<TriageGroup> {
        val installed = installedApps.sources()
        val loaded = ArrayList<TriageGroup>(aggregates.size)
        val sourced = ArrayList<SourcedShot<Asset>>()
        for (aggregate in aggregates) {
            val kind = aggregate.shotKind
            val assets = if (kind == ShotKind.SECRET) secretAssets() else assetDao.ephemeralOfKind(kind)
            val byText = if (kind == ShotKind.SECRET) {
                assets
            } else {
                assets.filter { asset ->
                    val app = ShotSources.appOf(asset.displayName)
                    val source = app?.let { ShotSources.sourceOf(it, installed) }
                    if (app != null && source != null) sourced += SourcedShot(asset, app, source, asset.sizeBytes)
                    source == null
                }
            }
            if (byText.isEmpty()) continue
            loaded += TriageGroup(
                key = groupKey(kind),
                kind = kind,
                title = uiText(kindTitle(kind)),
                hint = uiText(kindHint(kind)),
                items = byText.map { it.toItem() },
                locked = kind == ShotKind.SECRET,
            )
        }
        val bySource = ShotSources.group(sourced).map { group ->
            TriageGroup(
                key = group.key,
                kind = null,
                title = group.title,
                hint = uiText(
                    R.string.triage_source_group_hint,
                    if (group.apps.size == 1) uiText(group.source.title) else UiText.Raw(group.apps.joinToString(", ")),
                    uiText(group.source.hint),
                ),
                items = group.items.sortedByDescending { it.dateTaken }.map { it.toItem() },
                locked = false,
                source = group.source,
            )
        }
        // The DAO already orders by reclaimable bytes; this only pushes the two groups
        // nobody can act on to the bottom, and the sort is stable.
        return bySource + loaded.sortedBy { order(it.kind) }
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
        const val SQLITE_ARGUMENT_LIMIT = 900
    }
}
