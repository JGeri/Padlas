package hu.padlas.feature.transcode

import android.content.Context
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.padlas.core.common.result.outcomeOf
import hu.padlas.core.database.entity.JobState
import hu.padlas.core.database.model.SizeAndCount
import hu.padlas.core.media.MediaOps
import hu.padlas.feature.transcode.data.TranscodeRepository
import hu.padlas.feature.transcode.data.VideoItem
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VideoUiState(
    val videos: List<VideoItem> = emptyList(),
    val saved: SizeAndCount = SizeAndCount(0, 0),
    val pendingJobs: Int = 0,
    val throttled: Boolean = false,
    val pendingRemovalIds: List<Long> = emptyList(),
    val busy: Boolean = false,
    val run: TranscodeScheduler.RunStatus = TranscodeScheduler.RunStatus(),
) {
    val running: Boolean get() = run.running

    val pendingRemovalBytes: Long
        get() = videos.filter { it.assetId in pendingRemovalIds }.sumOf { it.originalBytes }
}

@HiltViewModel
class VideoViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TranscodeRepository,
    private val mediaOps: MediaOps,
) : ViewModel() {

    private val pendingRemovals = MutableStateFlow<List<Long>>(emptyList())
    private val busy = MutableStateFlow(false)

    private val _removalRequest = MutableStateFlow<IntentSender?>(null)
    val removalRequest: StateFlow<IntentSender?> = _removalRequest.asStateFlow()

    val state: StateFlow<VideoUiState> = combine(
        repository.observeVideos(),
        repository.observeSaved(),
        repository.observePending(),
        repository.throttled,
        combine(TranscodeScheduler.observeRun(context), pendingRemovals, busy, ::Triple),
    ) { videos, saved, pending, throttled, live ->
        VideoUiState(
            videos = videos,
            saved = saved,
            pendingJobs = pending,
            throttled = throttled,
            pendingRemovalIds = live.second,
            busy = live.third,
            run = live.first,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), VideoUiState())

    init {
        refreshPendingRemovals()
    }

    fun startBatch() {
        viewModelScope.launch {
            repository.enqueueAll()
            TranscodeScheduler.enqueueBatch(context)
        }
    }

    fun stopBatch() {
        TranscodeScheduler.cancelAll(context)
    }

    fun retry(assetId: Long) {
        viewModelScope.launch { repository.retry(assetId) }
    }

    /**
     * Publishing puts the compressed copy in the gallery; the originals then leave through
     * one system dialog, never file by file and never behind the user's back.
     */
    fun replaceOriginals() {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            val finished = state.value.videos
                .filter { it.state == JobState.SUCCEEDED }
                .map { it.assetId }
            // One publish failing (a full disk, a file that vanished meanwhile) must not take
            // the screen down; whatever did go through is still offered below.
            val originals = outcomeOf { repository.publishFinished(finished) }.valueOrNull().orEmpty()
            busy.value = false
            if (originals.isEmpty()) {
                refreshPendingRemovals()
                return@launch
            }
            // Anything past one dialog stays in the pending removals for the next tap.
            _removalRequest.value = mediaOps.trashRequest(originals.take(MediaOps.MAX_REQUEST))
        }
    }

    fun removalRequestConsumed() {
        _removalRequest.value = null
    }

    fun refreshPendingRemovals() {
        viewModelScope.launch {
            val ids = repository.pendingRemovalIds()
            val settled = pendingRemovals.value.filterNot { it in ids }
            if (settled.isNotEmpty()) repository.settleRemovals(settled)
            pendingRemovals.value = ids
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
