package hu.padlas.feature.sort

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure. Turns a flat, newest-first deck into events, because deciding about "47 pictures
 * from Balaton" is one mental act and deciding about 47 unrelated pictures is 47.
 */
internal object EventGrouper {

    /** More than four hours of silence means you put the phone away: a new event. */
    const val EventGapMs: Long = 4L * 60L * 60L * 1000L

    /** Two shots this close together are the same place even across a night's sleep. */
    const val ClusterRadiusMeters: Double = 3_000.0

    private const val EarthRadiusMeters = 6_371_000.0

    private val periodFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy MMMM", Locale.forLanguageTag("hu-HU"))

    fun group(cards: List<SortCard>, zone: ZoneId = ZoneId.systemDefault()): List<SortSession> {
        if (cards.isEmpty()) return emptyList()

        val chronological = cards.sortedBy { it.dateTaken }
        val events = ArrayList<MutableList<SortCard>>()
        var current = mutableListOf(chronological.first())

        for (index in 1 until chronological.size) {
            val previous = chronological[index - 1]
            val next = chronological[index]
            if (startsNewEvent(previous, next)) {
                events += current
                current = mutableListOf(next)
            } else {
                current += next
            }
        }
        events += current

        // Newest event first, newest card first inside it: the deck always opens on the
        // material the user still remembers taking.
        return events.asReversed().map { event ->
            SortSession(
                id = "evt-${event.first().id}",
                cards = event.asReversed().toList(),
                period = periodOf(event, zone),
            )
        }
    }

    /**
     * The four-hour rule, with GPS as the tie-breaker in both directions: the same place
     * holds an event together across a night, a different place splits it immediately.
     */
    fun startsNewEvent(previous: SortCard, next: SortCard): Boolean {
        val distance = distanceMeters(previous, next)
        if (distance != null && distance > ClusterRadiusMeters) return true
        if (next.dateTaken - previous.dateTaken <= EventGapMs) return false
        // A long gap only splits when GPS cannot vouch that it is still the same place.
        return distance == null
    }

    /** Null when either side has no EXIF GPS; only then does time decide alone. */
    fun distanceMeters(a: SortCard, b: SortCard): Double? {
        val aLat = a.latitude ?: return null
        val aLon = a.longitude ?: return null
        val bLat = b.latitude ?: return null
        val bLon = b.longitude ?: return null
        return haversineMeters(aLat, aLon, bLat, bLon)
    }

    fun haversineMeters(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val dLat = Math.toRadians(bLat - aLat)
        val dLon = Math.toRadians(bLon - aLon)
        val latA = Math.toRadians(aLat)
        val latB = Math.toRadians(bLat)
        val sinLat = sin(dLat / 2)
        val sinLon = sin(dLon / 2)
        val h = sinLat * sinLat + cos(latA) * cos(latB) * sinLon * sinLon
        return 2 * EarthRadiusMeters * asin(min(1.0, sqrt(h)))
    }

    private fun periodOf(event: List<SortCard>, zone: ZoneId): String {
        val median = event[event.size / 2].dateTaken
        return Instant.ofEpochMilli(median).atZone(zone).format(periodFormat)
    }
}
