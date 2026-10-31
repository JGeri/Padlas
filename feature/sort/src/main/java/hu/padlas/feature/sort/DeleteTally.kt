package hu.padlas.feature.sort

import hu.padlas.core.common.format.Format

/** Adds up the rounds of one delete, so the message after the last one speaks for all of them. */
internal class DeleteTally {
    private var count = 0
    private var bytes = 0L

    fun reset() {
        count = 0
        bytes = 0L
    }

    /** Counts [batch] in and returns the line for the toast. */
    fun add(batch: DeleteBatch): String {
        count += batch.ids.size
        bytes += batch.bytes
        return if (batch.permanent) {
            "${Format.bytes(bytes)} felszabadítva"
        } else {
            "${Format.count(count)} elem a kukába került"
        }
    }
}
