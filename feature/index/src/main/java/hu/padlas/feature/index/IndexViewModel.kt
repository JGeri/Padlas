package hu.padlas.feature.index

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Immutable
internal data class IndexUiState(
    val running: Boolean = false,
    val finished: Boolean = false,
    val scanned: Int = 0,
    val total: Int = 0,
    val bytes: Long = 0L,
    val bucket: String = "",
    val durationMs: Long = 0L,
    val error: String? = null,
) {
    val fraction: Float get() = if (total > 0) (scanned.toFloat() / total).coerceIn(0f, 1f) else 0f
}

@HiltViewModel
internal class IndexViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val status: IndexStatus,
) : ViewModel() {

    val state: StateFlow<IndexUiState> = status.state
        .map { it.toUi() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
            initialValue = status.state.value.toUi(),
        )

    init {
        // The scan runs in a worker, not in this scope: leaving the screen must not abort
        // a 90 second pass, and a killed process has to be able to pick it up again.
        if (status.state.value is IndexState.Idle) IndexScheduler.enqueueFullScan(context)
    }

    fun retry() = IndexScheduler.enqueueFullScan(context)

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
    }
}

private fun IndexState.toUi(): IndexUiState = when (this) {
    is IndexState.Idle -> IndexUiState(running = true)
    is IndexState.Scanning -> IndexUiState(
        running = true,
        scanned = scanned,
        total = total,
        bytes = bytes,
        bucket = bucket,
    )
    is IndexState.Done -> IndexUiState(
        finished = true,
        scanned = count,
        total = count,
        bytes = bytes,
        durationMs = durationMs,
    )
    is IndexState.Failed -> IndexUiState(error = message)
}
