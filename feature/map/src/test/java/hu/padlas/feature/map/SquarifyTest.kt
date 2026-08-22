package hu.padlas.feature.map

import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.junit.Test

class SquarifyTest {

    @Test
    fun `an empty input produces no tiles`() {
        assertThat(squarify(emptyList(), BOUNDS)).isEmpty()
    }

    @Test
    fun `a degenerate rectangle produces no tiles`() {
        assertThat(squarify(items(1, 2, 3), MapRect(0f, 0f, 0f, 400f))).isEmpty()
    }

    @Test
    fun `zero weight items are dropped`() {
        val tiles = squarify(listOf(SquarifyItem("a", 10), SquarifyItem("b", 0)), BOUNDS)

        assertThat(tiles.map { it.id }).containsExactly("a")
    }

    @Test
    fun `a single item fills the whole rectangle`() {
        val tiles = squarify(listOf(SquarifyItem("only", 42)), BOUNDS)

        assertThat(tiles).hasSize(1)
        assertThat(tiles.single().rect.area).isWithin(TOLERANCE * BOUNDS.area).of(BOUNDS.area)
    }

    @Test
    fun `tiles come back in descending weight order`() {
        val tiles = squarify(items(3, 91, 12, 7, 55, 1, 30), BOUNDS)

        val weights = tiles.map { it.weight }
        assertThat(weights).isEqualTo(weights.sortedDescending())
        assertThat(weights).hasSize(7)
    }

    @Test
    fun `the tiles cover exactly the area of the rectangle`() {
        CASES.forEach { case ->
            val tiles = squarify(case, BOUNDS)
            val covered = tiles.sumOf { it.rect.area.toDouble() }

            assertThat(covered).isWithin(TOLERANCE * BOUNDS.area.toDouble()).of(BOUNDS.area.toDouble())
        }
    }

    @Test
    fun `each tile gets the share of the area its weight asks for`() {
        CASES.forEach { case ->
            val total = case.sumOf { it.weight }.toDouble()
            val tiles = squarify(case, BOUNDS)

            tiles.forEach { tile ->
                val expected = BOUNDS.area.toDouble() * tile.weight / total
                assertThat(tile.rect.area.toDouble()).isWithin(PROPORTION_TOLERANCE * expected).of(expected)
            }
        }
    }

    @Test
    fun `no two tiles overlap`() {
        CASES.forEach { case ->
            val tiles = squarify(case, BOUNDS)

            for (i in tiles.indices) {
                for (j in i + 1 until tiles.size) {
                    val overlap = overlapArea(tiles[i].rect, tiles[j].rect)
                    assertThat(overlap).isLessThan(TOLERANCE * BOUNDS.area)
                }
            }
        }
    }

    @Test
    fun `every tile stays inside the rectangle`() {
        CASES.forEach { case ->
            squarify(case, MapRect(20f, 60f, 1020f, 1860f)).forEach { tile ->
                assertThat(tile.rect.left).isAtLeast(20f - EPSILON)
                assertThat(tile.rect.top).isAtLeast(60f - EPSILON)
                assertThat(tile.rect.right).isAtMost(1020f + EPSILON)
                assertThat(tile.rect.bottom).isAtMost(1860f + EPSILON)
            }
        }
    }

    @Test
    fun `a wildly skewed distribution still tiles the rectangle`() {
        val skewed = items(100_000_000, 5_000, 4_000, 3_000, 2, 1)
        val tiles = squarify(skewed, BOUNDS)

        assertThat(tiles).hasSize(skewed.size)
        assertThat(tiles.sumOf { it.rect.area.toDouble() })
            .isWithin(TOLERANCE * BOUNDS.area.toDouble())
            .of(BOUNDS.area.toDouble())
    }

    private fun overlapArea(a: MapRect, b: MapRect): Float {
        val w = max(0f, min(a.right, b.right) - max(a.left, b.left))
        val h = max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
        return abs(w * h)
    }

    private companion object {
        val BOUNDS = MapRect(0f, 0f, 1080f, 1920f)
        const val TOLERANCE = 0.005f
        const val PROPORTION_TOLERANCE = 0.02
        const val EPSILON = 0.5f

        fun items(vararg weights: Long): List<SquarifyItem> =
            weights.mapIndexed { i, w -> SquarifyItem("id-$i", w) }

        val CASES: List<List<SquarifyItem>> = listOf(
            items(6, 6, 4, 3, 2, 2, 1),
            items(500),
            items(50, 50),
            items(90, 5, 3, 1, 1),
            items(12_884_901_888, 4_294_967_296, 1_073_741_824, 536_870_912, 268_435_456),
            items(7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7),
        )
    }
}
