package hu.padlas.feature.dedup.group

import com.google.common.truth.Truth.assertThat
import hu.padlas.core.database.entity.DupKind
import hu.padlas.core.database.entity.ShotKind
import org.junit.Test

class DupGrouperTest {

    private val grouper = DupGrouper()

    // ---- exact --------------------------------------------------------------

    @Test
    fun `equal sha256 forms an exact group`() {
        val result = grouper.group(
            listOf(
                candidate(1, sha = "aa", size = 300),
                candidate(2, sha = "aa", size = 300),
                candidate(3, sha = "bb", size = 300),
            ),
            NOW,
        )

        assertThat(result.groups).hasSize(1)
        assertThat(result.groups.single().kind).isEqualTo(DupKind.EXACT)
        assertThat(memberIds(result, result.groups.single().id)).containsExactly(1L, 2L)
    }

    @Test
    fun `a lone sha256 is not a group`() {
        val result = grouper.group(listOf(candidate(1, sha = "aa")), NOW)

        assertThat(result.groups).isEmpty()
    }

    @Test
    fun `quickHash alone never groups files of different size`() {
        val result = grouper.group(
            listOf(
                candidate(1, quick = "qq", size = 1_000),
                candidate(2, quick = "qq", size = 2_000),
            ),
            NOW,
        )

        assertThat(result.groups).isEmpty()
    }

    @Test
    fun `quickHash groups only while the full hash is missing for the whole set`() {
        val sameSize = grouper.group(
            listOf(candidate(1, quick = "qq", size = 1_000), candidate(2, quick = "qq", size = 1_000)),
            NOW,
        )
        assertThat(sameSize.groups).hasSize(1)
        assertThat(sameSize.groups.single().kind).isEqualTo(DupKind.EXACT)

        val partiallyHashed = grouper.group(
            listOf(
                candidate(1, quick = "qq", sha = "aa", size = 1_000),
                candidate(2, quick = "qq", size = 1_000),
            ),
            NOW,
        )
        assertThat(partiallyHashed.groups).isEmpty()
    }

    // ---- burst and similar --------------------------------------------------

    @Test
    fun `near hashes shot within ten seconds are a burst`() {
        val result = grouper.group(
            listOf(
                candidate(1, dHash = BASE, blur = 10f, dateTaken = NOW),
                candidate(2, dHash = BASE.flip(0, 1, 2, 3, 4, 5), blur = 20f, dateTaken = NOW + 9_000),
            ),
            NOW,
        )

        assertThat(result.groups.single().kind).isEqualTo(DupKind.BURST)
    }

    @Test
    fun `seven differing bits is a similar group, not a burst`() {
        val result = grouper.group(
            listOf(
                candidate(1, dHash = BASE, blur = 10f, dateTaken = NOW),
                candidate(2, dHash = BASE.flip(0, 1, 2, 3, 4, 5, 6), blur = 20f, dateTaken = NOW),
            ),
            NOW,
        )

        assertThat(result.groups.single().kind).isEqualTo(DupKind.SIMILAR)
    }

    @Test
    fun `the same scene an hour apart is not a burst`() {
        val result = grouper.group(
            listOf(
                candidate(1, dHash = BASE, blur = 10f, dateTaken = NOW),
                candidate(2, dHash = BASE.flip(0, 1), blur = 20f, dateTaken = NOW + 3_600_000),
            ),
            NOW,
        )

        assertThat(result.groups).isEmpty()
    }

    @Test
    fun `thirteen differing bits is a different photo`() {
        val result = grouper.group(
            listOf(
                candidate(1, dHash = BASE, blur = 10f),
                candidate(2, dHash = BASE.flip(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12), blur = 20f),
            ),
            NOW,
        )

        assertThat(result.groups).isEmpty()
    }

    @Test
    fun `a pair split across two prefix buckets is still found`() {
        // The differing bit sits in the top 16 bits, so the two hashes land in different
        // buckets; the one bit neighbour probe is what keeps them together.
        val result = grouper.group(
            listOf(
                candidate(1, dHash = BASE, blur = 10f),
                candidate(2, dHash = BASE.flip(62), blur = 20f),
            ),
            NOW,
        )

        assertThat(result.groups).hasSize(1)
        assertThat(result.groups.single().kind).isEqualTo(DupKind.BURST)
    }

    @Test
    fun `an exact duplicate is not offered again as a lookalike`() {
        val result = grouper.group(
            listOf(
                candidate(1, sha = "aa", dHash = BASE, blur = 10f),
                candidate(2, sha = "aa", dHash = BASE, blur = 20f),
            ),
            NOW,
        )

        assertThat(result.groups).hasSize(1)
        assertThat(result.groups.single().kind).isEqualTo(DupKind.EXACT)
    }

    // ---- winner selection ---------------------------------------------------

    @Test
    fun `the sharpest member wins`() {
        val result = grouper.group(
            listOf(
                candidate(1, dHash = BASE, blur = 12f),
                candidate(2, dHash = BASE.flip(0), blur = 48f),
                candidate(3, dHash = BASE.flip(1), blur = 30f),
            ),
            NOW,
        )

        assertThat(result.groups.single().representativeAssetId).isEqualTo(2L)
        assertThat(winnerIds(result)).containsExactly(2L)
    }

    @Test
    fun `equal sharpness is broken by size and then by date`() {
        val bySize = grouper.group(
            listOf(
                candidate(1, dHash = BASE, blur = 20f, size = 1_000),
                candidate(2, dHash = BASE.flip(0), blur = 20f, size = 9_000),
            ),
            NOW,
        )
        assertThat(bySize.groups.single().representativeAssetId).isEqualTo(2L)

        val byDate = grouper.group(
            listOf(
                candidate(1, dHash = BASE, blur = 20f, size = 1_000, dateTaken = NOW - 5_000),
                candidate(2, dHash = BASE.flip(0), blur = 20f, size = 1_000, dateTaken = NOW),
            ),
            NOW,
        )
        assertThat(byDate.groups.single().representativeAssetId).isEqualTo(2L)
    }

    @Test
    fun `sharpness is relative, a group of blurry frames still has a winner`() {
        val result = grouper.group(
            listOf(
                candidate(1, dHash = BASE, blur = 0.4f),
                candidate(2, dHash = BASE.flip(0), blur = 0.9f),
            ),
            NOW,
        )

        assertThat(result.groups.single().representativeAssetId).isEqualTo(2L)
    }

    @Test
    fun `reclaimable bytes is the sum of the losers`() {
        val result = grouper.group(
            listOf(
                candidate(1, sha = "aa", size = 500, blur = 90f),
                candidate(2, sha = "aa", size = 500, blur = 10f),
                candidate(3, sha = "aa", size = 500, blur = 20f),
            ),
            NOW,
        )

        assertThat(result.groups.single().reclaimableBytes).isEqualTo(1_000)
        assertThat(result.groups.single().memberCount).isEqualTo(3)
        assertThat(result.loserCount).isEqualTo(2)
    }

    // ---- protected set (INV-4 / INV-1c) -------------------------------------

    @Test
    fun `a favourite is a member but never a loser`() {
        val result = grouper.group(
            listOf(
                candidate(1, sha = "aa", blur = 5f, favorite = true),
                candidate(2, sha = "aa", blur = 90f),
            ),
            NOW,
        )

        assertThat(winnerIds(result)).containsExactly(1L)
        assertThat(loserIds(result)).containsExactly(2L)
    }

    @Test
    fun `a manually protected asset wins over the sharper frame`() {
        val result = grouper.group(
            listOf(
                candidate(1, dHash = BASE, blur = 5f, protectedManually = true),
                candidate(2, dHash = BASE.flip(0), blur = 90f),
            ),
            NOW,
        )

        assertThat(winnerIds(result)).containsExactly(1L)
    }

    @Test
    fun `a secret screenshot is never a loser`() {
        val result = grouper.group(
            listOf(
                candidate(1, sha = "aa", blur = 5f, shotKind = ShotKind.SECRET),
                candidate(2, sha = "aa", blur = 90f),
            ),
            NOW,
        )

        assertThat(loserIds(result)).containsExactly(2L)
    }

    @Test
    fun `a fresh camera shot is protected, an old one is not`() {
        val fresh = grouper.group(
            listOf(
                candidate(1, sha = "aa", blur = 5f, path = "DCIM/Camera/", dateTaken = NOW - 3 * DAY),
                candidate(2, sha = "aa", blur = 90f, path = "Pictures/"),
            ),
            NOW,
        )
        assertThat(winnerIds(fresh)).containsExactly(1L)

        val old = grouper.group(
            listOf(
                candidate(1, sha = "aa", blur = 5f, path = "DCIM/Camera/", dateTaken = NOW - 90 * DAY),
                candidate(2, sha = "aa", blur = 90f, path = "Pictures/"),
            ),
            NOW,
        )
        assertThat(winnerIds(old)).containsExactly(2L)
        assertThat(loserIds(old)).containsExactly(1L)
    }

    @Test
    fun `two protected members drop the whole group`() {
        val result = grouper.group(
            listOf(
                candidate(1, sha = "aa", blur = 5f, favorite = true),
                candidate(2, sha = "aa", blur = 90f, protectedManually = true),
            ),
            NOW,
        )

        assertThat(result.groups).isEmpty()
        assertThat(result.members).isEmpty()
    }

    // ---- entities -----------------------------------------------------------

    @Test
    fun `member rows carry the distance from the winner`() {
        val result = grouper.group(
            listOf(
                candidate(1, dHash = BASE, blur = 90f),
                candidate(2, dHash = BASE.flip(0, 1, 2), blur = 10f),
            ),
            NOW,
        )

        val loser = result.members.single { !it.isWinner }
        assertThat(loser.distance).isEqualTo(3)
        assertThat(result.members.single { it.isWinner }.distance).isEqualTo(0)
    }

    @Test
    fun `the same members always produce the same group id`() {
        val input = listOf(candidate(1, sha = "aa"), candidate(2, sha = "aa"))
        val first = grouper.group(input, NOW)
        val second = grouper.group(input.reversed(), NOW + 10_000)

        assertThat(second.groups.single().id).isEqualTo(first.groups.single().id)
    }

    // ---- helpers ------------------------------------------------------------

    private fun memberIds(grouping: DupGrouping, groupId: String): List<Long> =
        grouping.members.filter { it.groupId == groupId }.map { it.assetId }

    private fun winnerIds(grouping: DupGrouping): List<Long> =
        grouping.members.filter { it.isWinner }.map { it.assetId }

    private fun loserIds(grouping: DupGrouping): List<Long> =
        grouping.members.filter { !it.isWinner }.map { it.assetId }

    private fun candidate(
        id: Long,
        sha: String? = null,
        quick: String? = null,
        dHash: Long? = null,
        blur: Float? = null,
        size: Long = 1_000_000L,
        dateTaken: Long = NOW,
        path: String = "Pictures/",
        favorite: Boolean = false,
        protectedManually: Boolean = false,
        shotKind: ShotKind? = null,
    ) = DupCandidate(
        assetId = id,
        sizeBytes = size,
        dateTaken = dateTaken,
        quickHash = quick,
        sha256 = sha,
        dHash = dHash,
        blurScore = blur,
        relativePath = path,
        isFavorite = favorite,
        protectedManually = protectedManually,
        shotKind = shotKind,
    )

    private fun Long.flip(vararg positions: Int): Long =
        positions.fold(this) { acc, position -> acc xor (1L shl position) }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val DAY = 24L * 60 * 60 * 1000

        /** A hash with bits set high and low, so flipping either end is meaningful. */
        const val BASE = 0x5A3C_7E10_2B49_0000L
    }
}
