package hu.padlas.feature.sort

import com.google.common.truth.Truth.assertThat
import hu.padlas.core.database.entity.ValueClass
import java.time.ZoneId
import org.junit.Test

class EventGrouperTest {

    private val zone: ZoneId = ZoneId.of("Europe/Budapest")
    private val base = 1_690_000_000_000L // 2023-07-22
    private val hour = 60L * 60L * 1000L

    private fun card(
        id: Long,
        atMillis: Long,
        latitude: Double? = null,
        longitude: Double? = null,
    ) = SortCard(
        id = id,
        uri = "content://media/external/images/media/$id",
        displayName = "IMG_$id.jpg",
        bucketName = "Camera",
        sizeBytes = 3_000_000,
        dateTaken = atMillis,
        isVideo = false,
        durationMs = null,
        latitude = latitude,
        longitude = longitude,
        valueClass = ValueClass.IRREPLACEABLE,
        triagedAt = 0,
    )

    @Test
    fun `a gap under four hours keeps one event`() {
        val sessions = EventGrouper.group(
            listOf(
                card(1, base),
                card(2, base + 2 * hour),
                card(3, base + 3 * hour),
            ),
            zone,
        )

        assertThat(sessions).hasSize(1)
        assertThat(sessions.first().cards).hasSize(3)
    }

    @Test
    fun `a gap over four hours starts a new event`() {
        val sessions = EventGrouper.group(
            listOf(
                card(1, base),
                card(2, base + hour),
                card(3, base + 6 * hour),
            ),
            zone,
        )

        // Newest event first: the deck opens on what you shot most recently.
        assertThat(sessions).hasSize(2)
        assertThat(sessions.map { it.cards.size }).containsExactly(1, 2).inOrder()
    }

    @Test
    fun `cards arrive newest first whatever the input order was`() {
        val sessions = EventGrouper.group(
            listOf(
                card(3, base + 2 * hour),
                card(1, base),
                card(2, base + hour),
            ),
            zone,
        )

        assertThat(sessions.first().cards.map { it.id }).containsExactly(3L, 2L, 1L).inOrder()
    }

    @Test
    fun `nearby coordinates hold an event together across a long night`() {
        val sessions = EventGrouper.group(
            listOf(
                card(1, base, latitude = 46.9100, longitude = 17.8900),
                card(2, base + 14 * hour, latitude = 46.9105, longitude = 17.8908),
            ),
            zone,
        )

        assertThat(sessions).hasSize(1)
    }

    @Test
    fun `an empty deck produces no sessions`() {
        assertThat(EventGrouper.group(emptyList(), zone)).isEmpty()
    }

    @Test
    fun `the header counts the cards and names the period`() {
        val session = EventGrouper.group(listOf(card(1, base), card(2, base + hour)), zone).first()

        assertThat(session.header).contains("2 kép")
        assertThat(session.bytes).isEqualTo(6_000_000)
    }
}
