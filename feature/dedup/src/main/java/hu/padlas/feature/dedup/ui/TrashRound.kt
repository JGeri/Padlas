package hu.padlas.feature.dedup.ui

/**
 * One system dialog's worth of a clean-up. Whole groups go in while they fit and the rest
 * waits for the next round. A single group bigger than one dialog is cut: its first `max`
 * losers go, and the group stays open until the last of them is gone.
 */
internal class TrashRound<T>(
    /** Groups that are closed once this round goes through. */
    val resolved: List<String>,
    val losers: List<T>,
    /** Groups asked for that this round does not finish. */
    val rest: List<String>,
)

/** [open] is each group's id with the losers it still has, in the order they are shown. */
internal fun <T> planTrashRound(open: List<Pair<String, List<T>>>, max: Int): TrashRound<T> {
    val round = ArrayList<Pair<String, List<T>>>()
    var count = 0
    for (entry in open) {
        if (round.isNotEmpty() && count + entry.second.size > max) break
        round += entry
        count += entry.second.size
    }
    val ids = round.map { it.first }
    val later = open.drop(round.size).map { it.first }
    val losers = round.flatMap { it.second }.take(max)
    return if (count > losers.size) {
        TrashRound(resolved = emptyList(), losers = losers, rest = ids + later)
    } else {
        TrashRound(resolved = ids, losers = losers, rest = later)
    }
}
