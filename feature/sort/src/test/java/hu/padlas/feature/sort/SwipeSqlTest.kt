package hu.padlas.feature.sort

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import com.google.common.truth.Truth.assertThat
import hu.padlas.feature.sort.ui.decide
import hu.padlas.feature.sort.ui.overlayFor
import org.junit.Test

class SwipeSqlTest {

    @Test
    fun `an empty filter only keeps local photos and videos that were never reviewed`() {
        val query = SwipeSql.where(SwipeFilter())

        assertThat(query.sql).contains("a.state IN ('LOCAL', 'PROTECTED')")
        assertThat(query.sql).contains("a.mimeType LIKE 'image/%' OR a.mimeType LIKE 'video/%'")
        assertThat(query.sql).contains("d.assetId IS NULL")
        assertThat(query.args).isEmpty()
    }

    @Test
    fun `folders and months are bound as arguments in a stable order`() {
        val query = SwipeSql.where(
            SwipeFilter(buckets = setOf(30L, 10L), months = setOf("2023-02", "2022-12"), onlyUnreviewed = false),
        )

        assertThat(query.sql).contains("a.bucketId IN (?, ?)")
        assertThat(query.sql).contains("${SwipeSql.MONTH_EXPRESSION} IN (?, ?)")
        assertThat(query.sql).doesNotContain("d.assetId IS NULL")
        assertThat(query.args).containsExactly(10L, 30L, "2022-12", "2023-02").inOrder()
    }

    @Test
    fun `the order and the limit end the candidate query`() {
        val newest = SwipeSql.candidates(SwipeFilter(order = SwipeOrder.NEWEST), 100).sql
        val largest = SwipeSql.candidates(SwipeFilter(order = SwipeOrder.LARGEST, media = MediaFilter.VIDEOS), 5).sql

        assertThat(newest).endsWith("ORDER BY a.dateTaken DESC LIMIT 100")
        assertThat(largest).endsWith("ORDER BY a.sizeBytes DESC LIMIT 5")
        assertThat(largest).contains("a.mimeType LIKE 'video/%'")
    }

    @Test
    fun `a short drag springs back and a long one decides`() {
        assertThat(decide(Offset(40f, 0f), Velocity.Zero, 1000f, 2000f)).isNull()
        assertThat(decide(Offset(300f, 10f), Velocity.Zero, 1000f, 2000f)).isEqualTo(SwipeAction.KEEP)
        assertThat(decide(Offset(-300f, 10f), Velocity.Zero, 1000f, 2000f)).isEqualTo(SwipeAction.DELETE)
        assertThat(decide(Offset(20f, -400f), Velocity.Zero, 1000f, 2000f)).isEqualTo(SwipeAction.FAVORITE)
    }

    @Test
    fun `a fast flick decides even when the card barely moved`() {
        assertThat(decide(Offset(-60f, 0f), Velocity(-2_500f, 0f), 1000f, 2000f)).isEqualTo(SwipeAction.DELETE)
        assertThat(decide(Offset(0f, -50f), Velocity(0f, -3_000f), 1000f, 2000f)).isEqualTo(SwipeAction.FAVORITE)
    }

    @Test
    fun `the overlay grows towards the dominant direction only`() {
        val left = overlayFor(Offset(-130f, 20f), 1000f, 2000f)
        assertThat(left.delete).isWithin(0.01f).of(0.5f)
        assertThat(left.keep).isEqualTo(0f)
        assertThat(left.favorite).isEqualTo(0f)

        val up = overlayFor(Offset(10f, -640f), 1000f, 2000f)
        assertThat(up.favorite).isEqualTo(1f)
        assertThat(up.delete).isEqualTo(0f)
    }

    @Test
    fun `month keys become Hungarian labels`() {
        assertThat(HungarianDates.month("2023-02")).isEqualTo("2023. február")
        assertThat(HungarianDates.monthName("2023-09")).isEqualTo("Szeptember")
        assertThat(HungarianDates.year("2021-07")).isEqualTo(2021)
        assertThat(HungarianDates.month("nonsense")).isEqualTo("nonsense")
    }
}
