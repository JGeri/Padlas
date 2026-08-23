package hu.padlas.feature.browse

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BrowseSqlTest {

    private val now = 1_700_000_000_000L
    private val dayMs = 24L * 60 * 60 * 1000

    @Test
    fun `an empty filter still restricts to what is on the device`() {
        val query = BrowseSql.where(BrowseFilter(), now)

        assertThat(query.sql).isEqualTo(BrowseSql.BASE_CLAUSE)
        assertThat(query.args).isEmpty()
    }

    @Test
    fun `folders bind one placeholder each`() {
        val query = BrowseSql.where(BrowseFilter(buckets = setOf(7L, 3L)), now)

        assertThat(query.sql).contains("bucketId IN (?, ?)")
        assertThat(query.args).containsExactly(3L, 7L).inOrder()
    }

    @Test
    fun `months are matched with the same expression the aggregate uses`() {
        val query = BrowseSql.where(BrowseFilter(months = setOf("2024-07")), now)

        assertThat(query.sql).contains(BrowseSql.MONTH_EXPRESSION)
        assertThat(query.args).containsExactly("2024-07")
    }

    @Test
    fun `folder and month combine with and`() {
        val query = BrowseSql.where(
            BrowseFilter(buckets = setOf(1L), months = setOf("2023-01", "2023-02")),
            now,
        )

        assertThat(query.sql).contains(" AND ")
        assertThat(query.args).containsExactly(1L, "2023-01", "2023-02").inOrder()
    }

    @Test
    fun `the age cutoff is resolved against the supplied clock`() {
        val query = BrowseSql.where(BrowseFilter(olderThanDays = 365), now)

        assertThat(query.args).containsExactly(now - 365 * dayMs)
    }

    @Test
    fun `media type filters bind a like pattern`() {
        assertThat(BrowseSql.where(BrowseFilter(mediaType = BrowseMediaType.VIDEO), now).args)
            .containsExactly("video/%")
        assertThat(BrowseSql.where(BrowseFilter(mediaType = BrowseMediaType.PHOTO), now).args)
            .containsExactly("image/%")
        assertThat(BrowseSql.where(BrowseFilter(mediaType = BrowseMediaType.ALL), now).args).isEmpty()
    }

    @Test
    fun `the page query is ordered newest first`() {
        val query = BrowseSql.page(BrowseFilter(buckets = setOf(2L)), now)

        assertThat(query.sql).startsWith("SELECT * FROM asset WHERE ")
        assertThat(query.sql).endsWith("ORDER BY dateTaken DESC")
    }

    @Test
    fun `the totals query projects the columns SizeAndCount expects`() {
        val query = BrowseSql.totals(BrowseFilter(), now)

        assertThat(query.sql).contains("COUNT(*) AS itemCount")
        assertThat(query.sql).contains("COALESCE(SUM(sizeBytes), 0) AS totalBytes")
    }

    @Test
    fun `select all is capped`() {
        assertThat(BrowseSql.ids(BrowseFilter(), now, limit = 2_000).sql).endsWith("LIMIT 2000")
    }

    @Test
    fun `a filter with any axis set is not empty`() {
        assertThat(BrowseFilter().isEmpty).isTrue()
        assertThat(BrowseFilter(months = setOf("2024-01")).isEmpty).isFalse()
        assertThat(BrowseFilter(buckets = setOf(1L)).isEmpty).isFalse()
        assertThat(BrowseFilter(olderThanDays = 30).isEmpty).isFalse()
        assertThat(BrowseFilter(mediaType = BrowseMediaType.VIDEO).isEmpty).isFalse()
    }
}
