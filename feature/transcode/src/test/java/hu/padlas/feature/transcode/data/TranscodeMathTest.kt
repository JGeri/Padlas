package hu.padlas.feature.transcode.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TranscodeMathTest {

    @Test
    fun `target bitrate is half the source bitrate`() {
        // 60 MB over 10 s is 48 Mbit/s; half of that is 24 Mbit/s.
        val bitrate = TranscodeMath.targetBitrateBps(sourceBytes = 60_000_000, durationMs = 10_000)
        assertThat(bitrate).isEqualTo(24_000_000)
    }

    @Test
    fun `target bitrate is clamped for absurd inputs`() {
        assertThat(TranscodeMath.targetBitrateBps(4_000_000_000L, 1_000)).isEqualTo(40_000_000)
        assertThat(TranscodeMath.targetBitrateBps(1_000, 600_000)).isEqualTo(2_000_000)
        assertThat(TranscodeMath.targetBitrateBps(0, 0)).isEqualTo(2_000_000)
    }

    @Test
    fun `saved bytes never go negative`() {
        assertThat(TranscodeMath.savedBytes(100, 40)).isEqualTo(60)
        assertThat(TranscodeMath.savedBytes(40, 100)).isEqualTo(0)
    }

    @Test
    fun `shrink ratio drives the card height`() {
        assertThat(TranscodeMath.shrinkRatio(1_000, 400)).isWithin(TOLERANCE).of(0.4f)
        assertThat(TranscodeMath.shrinkRatio(1_000, 0)).isWithin(TOLERANCE).of(1f)
        assertThat(TranscodeMath.shrinkRatio(0, 400)).isWithin(TOLERANCE).of(1f)
    }

    @Test
    fun `height fraction has a floor so small files stay readable`() {
        assertThat(TranscodeMath.heightFraction(1_000, 1_000)).isWithin(TOLERANCE).of(1f)
        assertThat(TranscodeMath.heightFraction(500, 1_000)).isWithin(TOLERANCE).of(0.5f)
        assertThat(TranscodeMath.heightFraction(1, 1_000)).isWithin(TOLERANCE).of(0.34f)
        assertThat(TranscodeMath.heightFraction(500, 0)).isWithin(TOLERANCE).of(1f)
    }

    @Test
    fun `trial decode rejects a truncated output`() {
        assertThat(TranscodeMath.durationWithinTolerance(60_000, 59_000)).isTrue()
        assertThat(TranscodeMath.durationWithinTolerance(60_000, 30_000)).isFalse()
        assertThat(TranscodeMath.durationWithinTolerance(60_000, 0)).isFalse()
        // 400 ms of drift on a 2 s clip is inside the 500 ms floor.
        assertThat(TranscodeMath.durationWithinTolerance(2_000, 2_400)).isTrue()
    }

    @Test
    fun `an unknown source duration only demands a playable output`() {
        assertThat(TranscodeMath.durationWithinTolerance(0, 1_000)).isTrue()
        assertThat(TranscodeMath.durationWithinTolerance(0, 0)).isFalse()
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
