package hu.padlas.feature.sort

import hu.padlas.core.common.format.Format
import hu.padlas.core.common.text.UiText
import hu.padlas.core.common.text.uiPlural
import hu.padlas.core.common.text.uiText

/** Adds up the rounds of one delete, so the message after the last one speaks for all of them. */
internal class DeleteTally {
    private var count = 0
    private var bytes = 0L

    fun reset() {
        count = 0
        bytes = 0L
    }

    /** Counts [batch] in and returns the line for the toast. */
    fun add(batch: DeleteBatch): UiText {
        count += batch.ids.size
        bytes += batch.bytes
        return if (batch.permanent) {
            uiText(R.string.sort_tally_freed, Format.bytes(bytes))
        } else {
            uiPlural(R.plurals.sort_tally_trashed, count, Format.count(count))
        }
    }
}

/** The toast after a round that leaves more for the next one. */
internal fun UiText.withNextBatch(): UiText = uiText(R.string.sort_next_batch, this)

/** "2023. február · 3 mappa · fotók": the parts of a scope, in the separator of the language. */
internal fun List<UiText>.joinedByDots(): UiText = reduce { joined, part -> uiText(R.string.sort_join, joined, part) }
