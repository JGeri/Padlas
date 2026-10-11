package hu.padlas.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.padlas.core.common.format.Format
import hu.padlas.core.media.StorageProbe
import hu.padlas.core.media.VolumeInfo
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class StorageViewModel @Inject constructor(
    private val analyzer: StorageAnalyzer,
    private val appsRepository: AppsRepository,
    private val probe: StorageProbe,
) : ViewModel() {

    private val _access = MutableStateFlow(Access(probe.hasAllFilesAccess(), appsRepository.hasAccess()))
    val access: StateFlow<Access> = _access.asStateFlow()

    private val snapshots: Flow<StorageSnapshot?> = analyzer.state.map { it.snapshot }.distinctUntilChanged()

    val progress: StateFlow<ScanProgress?> = analyzer.state.map { it.progress }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), analyzer.state.value.progress)

    val scanning: StateFlow<Boolean> = analyzer.state.map { it.progress != null }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), analyzer.state.value.progress != null)

    val hasResult: StateFlow<Boolean> = analyzer.state.map { it.snapshot != null }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), analyzer.state.value.snapshot != null)

    private val _view = MutableStateFlow(StorageView.MAP)
    val view: StateFlow<StorageView> = _view.asStateFlow()

    /** Null means the root, whatever its absolute path turns out to be. */
    private val navPath = MutableStateFlow<String?>(null)

    private val _filter = MutableStateFlow<FileCategory?>(null)
    private val _cleanup = MutableStateFlow<Cleanup?>(null)

    private val _selection = MutableStateFlow(SelectionUi())
    val selection: StateFlow<SelectionUi> = _selection.asStateFlow()

    private val _focus = MutableStateFlow<Focus?>(null)
    val focus: StateFlow<Focus?> = _focus.asStateFlow()

    private val _confirm = MutableStateFlow<DeleteRequest?>(null)
    val confirm: StateFlow<DeleteRequest?> = _confirm.asStateFlow()

    private val _deleting = MutableStateFlow(false)
    val deleting: StateFlow<Boolean> = _deleting.asStateFlow()

    private val _toast = MutableStateFlow<ToastUi?>(null)
    val toast: StateFlow<ToastUi?> = _toast.asStateFlow()

    private val volume = MutableStateFlow<VolumeInfo?>(null)
    private val tourIndex = MutableStateFlow(0)

    val rootPath: String get() = analyzer.rootPath

    /** "12 perce" has to keep counting while the screen is open. */
    private val minuteTicks: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(60_000L)
        }
    }

    val header: StateFlow<String> = combine(snapshots, scanning, _access, minuteTicks) { snapshot, isScanning, access, _ ->
        when {
            !access.allFiles -> "Nézd meg, mi foglalja a helyet"
            snapshot != null -> "Utoljára elemezve: ${TimeText.relative(snapshot.scannedAt)} · " +
                "${Format.count(snapshot.index.totalFiles)} fájl"
            isScanning -> "Elemzés folyamatban…"
            else -> "Még nincs elemzés"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), "")

    val directory: StateFlow<DirectoryUi?> = combine(snapshots, navPath) { snapshot, path -> snapshot to path }
        .mapLatest { (snapshot, path) ->
            if (snapshot == null) {
                null
            } else {
                analyzer.read { DirectoryListing.build(it.root, path ?: it.root.path, it.version) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

    val summary: StateFlow<SummaryUi?> = combine(snapshots, volume, appsRepository.apps, _access) { snapshot, vol, apps, access ->
        buildSummary(snapshot, vol, apps?.takeIf { access.usage })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

    val types: StateFlow<TypesUi?> = snapshots.map { snapshot ->
        snapshot?.index?.let { TypesUi(it.totalBytes, it.categories, it.extensions) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

    val files: StateFlow<FilesUi?> = combine(snapshots, _filter, _cleanup) { snapshot, filter, cleanup ->
        snapshot?.index?.let { buildFiles(it, filter, cleanup) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

    val apps: StateFlow<AppsUi> = combine(
        appsRepository.apps,
        appsRepository.loading,
        _access,
        tourIndex,
    ) { snapshot, loading, access, tour ->
        val list = if (access.usage) snapshot?.apps.orEmpty() else emptyList()
        AppsUi(
            hasAccess = access.usage,
            loading = loading,
            apps = list,
            cacheStops = list.filter { it.cacheBytes >= CACHE_TOUR_FLOOR }.sortedByDescending { it.cacheBytes },
            tourIndex = tour,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), AppsUi(false, false, emptyList(), emptyList(), 0))

    init {
        refreshVolume()
    }

    /** Both permissions are granted in system settings, so every return can change them. */
    fun onResume() {
        val now = Access(probe.hasAllFilesAccess(), appsRepository.hasAccess())
        _access.value = now
        if (now.allFiles && analyzer.state.value.snapshot == null && analyzer.state.value.progress == null) {
            analyzer.scan()
        }
        if (now.usage) viewModelScope.launch { appsRepository.refresh(force = false) }
        refreshVolume()
    }

    fun rescan() {
        if (!_access.value.allFiles) return
        _focus.value = null
        analyzer.scan()
        refreshVolume()
        if (_access.value.usage) viewModelScope.launch { appsRepository.refresh(force = true) }
    }

    fun cancelScan() = analyzer.cancelScan()

    fun selectView(view: StorageView) {
        if (_view.value == view) return
        _view.value = view
        _focus.value = null
        _selection.value = SelectionUi()
    }

    fun open(path: String) {
        _focus.value = null
        _selection.value = SelectionUi()
        navPath.value = path
    }

    fun jumpTo(path: String) = open(path)

    /** False at the root, so the caller knows to leave the screen instead. */
    fun navigateUp(): Boolean {
        val parent = directory.value?.parentPath ?: return false
        open(parent)
        return true
    }

    /** The first tap on a tile selects it, a second tap on a selected folder opens it. */
    fun onTileTap(tile: MapTile) {
        val dir = directory.value ?: return
        val item = tile.item
        if (item == null) {
            _focus.value = Focus.Rest(tile.restCount, tile.bytes, dir.bytes)
            return
        }
        val current = _focus.value
        if (current is Focus.Item && current.item.path == item.path && item.isDirectory) {
            open(item.path)
        } else {
            _focus.value = Focus.Item(item, dir.bytes)
        }
    }

    fun focus(item: StorageItem, parentBytes: Long) {
        _focus.value = Focus.Item(item, parentBytes)
    }

    fun clearFocus() {
        _focus.value = null
    }

    fun toggleSelection(item: StorageItem) {
        if (StoragePaths.isProtected(item.path, rootPath)) return
        _focus.value = null
        _selection.update { current ->
            val next = LinkedHashMap(current.items)
            if (next.remove(item.path) == null) next[item.path] = item
            SelectionUi(next)
        }
    }

    fun selectAll(items: List<StorageItem>) {
        _focus.value = null
        _selection.value = SelectionUi(
            items.filterNot { StoragePaths.isProtected(it.path, rootPath) }.associateByTo(LinkedHashMap()) { it.path },
        )
    }

    fun clearSelection() {
        _selection.value = SelectionUi()
    }

    fun setFilter(category: FileCategory?) {
        _filter.value = category
        _selection.value = SelectionUi()
    }

    fun openCleanup(kind: Cleanup) {
        _cleanup.value = kind
        _selection.value = SelectionUi()
    }

    fun closeCleanup() {
        _cleanup.value = null
        _selection.value = SelectionUi()
    }

    fun showCategory(category: FileCategory) {
        _cleanup.value = null
        _filter.value = category
        selectView(StorageView.FILES)
    }

    fun showAllInFolder() {
        selectView(StorageView.FOLDERS)
    }

    fun requestDelete(items: List<StorageItem>) {
        val deletable = items.filterNot { StoragePaths.isProtected(it.path, rootPath) }
        if (deletable.isEmpty()) return
        _confirm.value = DeleteRequest(deletable)
    }

    fun requestDeleteSelection() = requestDelete(_selection.value.items.values.toList())

    fun dismissDelete() {
        _confirm.value = null
    }

    fun confirmDelete() {
        val request = _confirm.value ?: return
        _confirm.value = null
        _deleting.value = true
        viewModelScope.launch {
            val outcome = try {
                analyzer.delete(request.items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DeleteOutcome(freedBytes = 0L, removed = 0, failed = request.items.size)
            } finally {
                _deleting.value = false
            }
            _focus.value = null
            _selection.value = SelectionUi()
            _toast.value = toastFor(outcome)
            refreshVolume()
        }
    }

    fun toastShown() {
        _toast.value = null
    }

    fun advanceTour() = tourIndex.update { it + 1 }

    fun restartTour() {
        tourIndex.value = 0
    }

    suspend fun appIcon(packageName: String, sizePx: Int) = appsRepository.icon(packageName, sizePx)

    private fun refreshVolume() {
        viewModelScope.launch {
            volume.value = runCatching { probe.volume() }.getOrNull()
        }
    }

    private fun toastFor(outcome: DeleteOutcome): ToastUi = when {
        outcome.removed == 0 -> ToastUi("Nem sikerült törölni", ToastTone.ERROR)
        outcome.failed > 0 -> ToastUi(
            "${Format.bytes(outcome.freedBytes)} felszabadítva · ${Format.count(outcome.failed)} elem maradt",
            ToastTone.WARNING,
        )
        else -> ToastUi("${Format.bytes(outcome.freedBytes)} felszabadítva", ToastTone.SUCCESS)
    }

    private fun buildFiles(index: StorageIndex, filter: FileCategory?, cleanup: Cleanup?): FilesUi {
        val shortcuts = listOf(
            CleanupSummary(Cleanup.INSTALLERS, index.installers.size, index.installers.sumOf { it.bytes }),
            CleanupSummary(Cleanup.OLD_DOWNLOADS, index.oldDownloads.size, index.oldDownloads.sumOf { it.bytes }),
            CleanupSummary(Cleanup.THUMBNAILS, index.thumbnailDirs.size, index.thumbnailDirs.sumOf { it.bytes }),
            CleanupSummary(Cleanup.EMPTY_DIRS, index.emptyDirs.size, 0L),
        )
        val items = when (cleanup) {
            null -> if (filter == null) index.largest else index.largestByCategory[filter].orEmpty()
            Cleanup.INSTALLERS -> index.installers
            Cleanup.OLD_DOWNLOADS -> index.oldDownloads
            Cleanup.THUMBNAILS -> index.thumbnailDirs
            Cleanup.EMPTY_DIRS -> index.emptyDirs
        }
        return FilesUi(filter, cleanup, items, shortcuts)
    }

    private fun buildSummary(snapshot: StorageSnapshot?, vol: VolumeInfo?, apps: AppsSnapshot?): SummaryUi? {
        if (snapshot == null || vol == null || vol.totalBytes <= 0L) return null
        val index = snapshot.index
        val slices = ArrayList<SummarySlice>()
        index.categories.forEach { slices += SummarySlice(it.category.label, it.bytes, SliceKind.FILES, it.category) }
        // App data already includes each app's Android/media folder, which the walk counted as files.
        val appBytes = apps?.apps?.sumOf { it.totalBytes }?.minus(index.appMediaBytes)?.coerceAtLeast(0L)
        if (appBytes != null && appBytes > 0L) slices += SummarySlice("Appok", appBytes, SliceKind.APPS)
        val rest = (vol.usedBytes - index.totalBytes - (appBytes ?: 0L)).coerceAtLeast(0L)
        if (rest > 0L) {
            val label = if (appBytes == null) "Appok és rendszer" else "Rendszer és egyéb"
            slices += SummarySlice(label, rest, SliceKind.SYSTEM)
        }
        return SummaryUi(vol.totalBytes, vol.usedBytes, vol.freeBytes, slices)
    }

    private companion object {
        const val STOP_MS = 5_000L

        /** A cache this small is not worth a trip to the settings screen. */
        const val CACHE_TOUR_FLOOR = 8L * 1024 * 1024
    }
}
