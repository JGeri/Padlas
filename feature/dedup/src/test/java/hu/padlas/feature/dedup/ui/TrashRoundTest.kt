package hu.padlas.feature.dedup.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TrashRoundTest {

    private fun group(id: String, losers: Int) = id to List(losers) { "$id-$it" }

    @Test
    fun `groups that fit go in one round and are resolved`() {
        val round = planTrashRound(listOf(group("a", 3), group("b", 2)), max = 10)

        assertEquals(listOf("a", "b"), round.resolved)
        assertEquals(5, round.losers.size)
        assertEquals(emptyList<String>(), round.rest)
    }

    @Test
    fun `a group that would overflow the dialog waits for the next round`() {
        val round = planTrashRound(listOf(group("a", 6), group("b", 5), group("c", 1)), max = 10)

        assertEquals(listOf("a"), round.resolved)
        assertEquals(6, round.losers.size)
        assertEquals(listOf("b", "c"), round.rest)
    }

    @Test
    fun `a group bigger than one dialog is cut and stays open`() {
        val round = planTrashRound(listOf(group("a", 25), group("b", 1)), max = 10)

        assertEquals(emptyList<String>(), round.resolved)
        assertEquals((0 until 10).map { "a-$it" }, round.losers)
        assertEquals(listOf("a", "b"), round.rest)
    }

    @Test
    fun `a group exactly the size of a dialog is resolved`() {
        val round = planTrashRound(listOf(group("a", 10), group("b", 1)), max = 10)

        assertEquals(listOf("a"), round.resolved)
        assertEquals(10, round.losers.size)
        assertEquals(listOf("b"), round.rest)
    }

    @Test
    fun `rounds always shrink what is left`() {
        var open = listOf(group("a", 25), group("b", 7), group("c", 4))
        val binned = HashSet<String>()
        var rounds = 0
        while (open.isNotEmpty()) {
            val round = planTrashRound(open, max = 10)
            check(round.losers.isNotEmpty())
            binned += round.losers
            open = round.rest.mapNotNull { id ->
                val left = open.first { it.first == id }.second.filter { it !in binned }
                if (left.isEmpty()) null else id to left
            }
            rounds++
        }

        assertEquals(36, binned.size)
        assertEquals(5, rounds)
    }
}
