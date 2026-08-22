package hu.padlas.feature.index

import androidx.compose.runtime.Immutable

/**
 * The whole index pipeline reports through this one type, so the first-run screen and the
 * Vault read the same numbers from the same place instead of each polling the database.
 */
@Immutable
sealed interface IndexState {

    data object Idle : IndexState

    data class Scanning(
        val scanned: Int,
        val total: Int,
        val bytes: Long,
        /** The bucket the last batch landed in; the screen needs a concrete name to show. */
        val bucket: String = "",
    ) : IndexState {
        val fraction: Float
            get() = if (total > 0) (scanned.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    data class Done(val count: Int, val bytes: Long, val durationMs: Long) : IndexState

    data class Failed(val message: String) : IndexState
}
