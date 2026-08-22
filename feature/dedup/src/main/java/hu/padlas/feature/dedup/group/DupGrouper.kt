package hu.padlas.feature.dedup.group

import hu.padlas.core.database.entity.DupGroupEntity
import hu.padlas.core.database.entity.DupKind
import hu.padlas.core.database.entity.DupMemberEntity
import hu.padlas.core.media.Hasher
import javax.inject.Inject
import kotlin.math.abs

/** Same scene: a burst frame or a re-save of the same shot. */
const val BURST_MAX_DISTANCE: Int = 6

/** Similar composition: the same subject, a different moment. One human decision. */
const val SIMILAR_MIN_DISTANCE: Int = 7
const val SIMILAR_MAX_DISTANCE: Int = 12

/** Frames further apart than this were not fired by holding the shutter down. */
const val BURST_WINDOW_MILLIS: Long = 10_000L

/**
 * Turns hashed assets into duplicate groups. Pure: same input, same output, no clock, no
 * database, no dispatcher. Everything that needs the world happens in DupWorker.
 */
class DupGrouper @Inject constructor() {

    fun group(candidates: List<DupCandidate>, now: Long): DupGrouping {
        val exact = exactClusters(candidates)
        val claimed = exact.flatMapTo(HashSet()) { cluster -> cluster.members.map { it.assetId } }
        val perceptual = perceptualClusters(candidates.filter { it.assetId !in claimed })

        val groups = ArrayList<DupGroupEntity>(exact.size + perceptual.size)
        val members = ArrayList<DupMemberEntity>()
        for (cluster in exact + perceptual) {
            val resolved = resolve(cluster, now) ?: continue
            groups += resolved.group
            members += resolved.members
        }
        return DupGrouping(groups, members)
    }

    // ---- exact --------------------------------------------------------------

    private fun exactClusters(candidates: List<DupCandidate>): List<Cluster> {
        val clusters = ArrayList<Cluster>()
        val withSha = candidates.filter { !it.sha256.isNullOrBlank() }
        withSha.groupBy { it.sha256 }.values
            .filter { it.size > 1 }
            .forEach { clusters += Cluster(DupKind.EXACT, it) }

        // The quickHash fallback only applies where the full hash is missing for the whole
        // set; a mixed set means phase 2 already ran and disagreed. The size is part of the
        // key because a quickHash is size + head + tail, and two files of different length
        // that share one are a collision, not a duplicate.
        val shaKnownFor = withSha.mapNotNullTo(HashSet()) { it.quickHash }
        candidates
            .filter { it.sha256.isNullOrBlank() && !it.quickHash.isNullOrBlank() && it.quickHash !in shaKnownFor }
            .groupBy { "${it.quickHash}|${it.sizeBytes}" }
            .values
            .filter { it.size > 1 }
            .forEach { clusters += Cluster(DupKind.EXACT, it) }
        return clusters
    }

    // ---- perceptual ---------------------------------------------------------

    /**
     * Comparing every dHash with every other one is 312 million comparisons at 25 000
     * assets: minutes of CPU for a result nobody is waiting for. Bucketing by the top 16
     * bits first turns it into a handful of comparisons per bucket, because near-identical
     * images share their high bits. The boundary case - the pair whose differing bits fall
     * inside the prefix - is recovered by probing the 16 buckets one bit away, which costs
     * 16 map lookups instead of a quadratic scan.
     */
    private fun perceptualClusters(candidates: List<DupCandidate>): List<Cluster> {
        val hashed = candidates.mapNotNull { candidate -> candidate.dHash?.let { Hashed(candidate, it) } }
        if (hashed.size < 2) return emptyList()

        val buckets = hashed.groupBy { prefix16(it.dHash) }
        val burst = UnionFind()
        val similar = UnionFind()

        for ((key, bucket) in buckets) {
            val neighbours = ArrayList<Hashed>(bucket)
            for (bit in 0 until PREFIX_BITS) {
                buckets[key xor (1 shl bit)]?.let { neighbours += it }
            }
            for (left in bucket) {
                for (right in neighbours) {
                    if (right.candidate.assetId <= left.candidate.assetId) continue
                    val distance = Hasher.hammingDistance(left.dHash, right.dHash)
                    val leftId = left.candidate.assetId
                    val rightId = right.candidate.assetId
                    when {
                        distance <= BURST_MAX_DISTANCE && withinBurstWindow(left, right) ->
                            burst.union(leftId, rightId)

                        distance in SIMILAR_MIN_DISTANCE..SIMILAR_MAX_DISTANCE ->
                            similar.union(leftId, rightId)
                    }
                }
            }
        }

        val byId = hashed.associateBy { it.candidate.assetId }
        val burstClusters = burst.components()
            .map { ids -> ids.mapNotNull { byId[it]?.candidate } }
            .filter { it.size > 1 }
            .map { Cluster(DupKind.BURST, it) }

        // A burst frame is never offered a second time as a loose lookalike.
        val inBurst = burstClusters.flatMapTo(HashSet()) { cluster -> cluster.members.map { it.assetId } }
        val similarClusters = similar.components()
            .map { ids -> ids.filter { it !in inBurst }.mapNotNull { byId[it]?.candidate } }
            .filter { it.size > 1 }
            .map { Cluster(DupKind.SIMILAR, it) }

        return burstClusters + similarClusters
    }

    private fun withinBurstWindow(left: Hashed, right: Hashed): Boolean =
        abs(left.candidate.dateTaken - right.candidate.dateTaken) <= BURST_WINDOW_MILLIS

    // ---- winner and losers --------------------------------------------------

    /**
     * The sharpest frame wins, and sharpness is only ever compared inside the group: an
     * absolute threshold would throw away every photo of a foggy morning.
     */
    private fun resolve(cluster: Cluster, now: Long): Resolved? {
        val members = cluster.members
        if (members.size < 2) return null

        val protectedMembers = members.filter { it.isProtected(now) }
        // Only one member can win, so a second protected member would have to be a loser.
        // INV-4 forbids that, and a group without a loser has nothing to offer.
        if (protectedMembers.size > 1) return null

        val winner = protectedMembers.singleOrNull() ?: members.maxWith(WINNER_ORDER)
        val losers = members.filter { it.assetId != winner.assetId }
        if (losers.isEmpty()) return null

        val id = groupId(cluster.kind, members.map { it.assetId })
        val group = DupGroupEntity(
            id = id,
            kind = cluster.kind,
            representativeAssetId = winner.assetId,
            memberCount = members.size,
            reclaimableBytes = losers.sumOf { it.sizeBytes },
            createdAt = now,
        )
        val rows = members.map { member ->
            DupMemberEntity(
                groupId = id,
                assetId = member.assetId,
                isWinner = member.assetId == winner.assetId,
                sharpness = member.blurScore,
                distance = distance(cluster.kind, winner, member),
            )
        }
        return Resolved(group, rows)
    }

    private fun distance(kind: DupKind, winner: DupCandidate, member: DupCandidate): Int {
        if (kind == DupKind.EXACT) return 0
        val a = winner.dHash ?: return 0
        val b = member.dHash ?: return 0
        return Hasher.hammingDistance(a, b)
    }

    /**
     * A group id has to survive a rescan: the same members must produce the same id, or a
     * group the user already resolved comes back as a brand new one.
     */
    private fun groupId(kind: DupKind, assetIds: List<Long>): String {
        var hash = FNV_OFFSET
        for (id in assetIds.sorted()) {
            var value = id
            repeat(Long.SIZE_BYTES) {
                hash = (hash xor (value and 0xFF)) * FNV_PRIME
                value = value ushr 8
            }
        }
        return "${kind.name.lowercase()}-${java.lang.Long.toHexString(hash)}"
    }

    private fun prefix16(dHash: Long): Int =
        (dHash ushr (Long.SIZE_BITS - PREFIX_BITS)).toInt() and 0xFFFF

    private data class Hashed(val candidate: DupCandidate, val dHash: Long)

    private data class Cluster(val kind: DupKind, val members: List<DupCandidate>)

    private data class Resolved(val group: DupGroupEntity, val members: List<DupMemberEntity>)

    private companion object {
        const val PREFIX_BITS = 16
        const val FNV_OFFSET = -3750763034362895579L
        const val FNV_PRIME = 1099511628211L

        val WINNER_ORDER: Comparator<DupCandidate> = compareBy<DupCandidate>(
            { it.blurScore ?: -Float.MAX_VALUE },
            { it.sizeBytes },
            { it.dateTaken },
            { -it.assetId },
        )
    }
}

data class DupGrouping(
    val groups: List<DupGroupEntity>,
    val members: List<DupMemberEntity>,
) {
    val reclaimableBytes: Long get() = groups.sumOf { it.reclaimableBytes }
    val loserCount: Int get() = members.count { !it.isWinner }
}

/** Union-find over asset ids; the components are the duplicate groups. */
private class UnionFind {

    private val parent = HashMap<Long, Long>()

    fun union(left: Long, right: Long) {
        val a = find(left)
        val b = find(right)
        if (a != b) parent[a] = b
    }

    fun components(): Collection<List<Long>> = parent.keys.groupBy { find(it) }.values

    private fun find(id: Long): Long {
        var root = parent.getOrPut(id) { id }
        while (true) {
            val next = parent[root] ?: root
            if (next == root) return root
            root = next
        }
    }
}
