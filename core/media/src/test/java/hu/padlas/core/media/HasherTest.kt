package hu.padlas.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HasherTest {

    @Test
    fun `identical hashes have no distance`() {
        assertThat(Hasher.hammingDistance(0L, 0L)).isEqualTo(0)
        assertThat(Hasher.hammingDistance(-1L, -1L)).isEqualTo(0)
        assertThat(Hasher.hammingDistance(0x0F1E2D3C4B5A6978L, 0x0F1E2D3C4B5A6978L)).isEqualTo(0)
    }

    @Test
    fun `a single flipped bit is distance one`() {
        assertThat(Hasher.hammingDistance(0L, 1L)).isEqualTo(1)
        assertThat(Hasher.hammingDistance(1L shl 63, 0L)).isEqualTo(1)
    }

    @Test
    fun `fully inverted hashes are 64 apart`() {
        assertThat(Hasher.hammingDistance(0L, -1L)).isEqualTo(64)
        assertThat(Hasher.hammingDistance(0x5555555555555555L, 0x5555555555555555L.inv())).isEqualTo(64)
    }

    @Test
    fun `distance counts every differing bit`() {
        assertThat(Hasher.hammingDistance(0b1011L, 0b0001L)).isEqualTo(2)
        assertThat(Hasher.hammingDistance(0x00000000000000FFL, 0L)).isEqualTo(8)
    }

    @Test
    fun `distance is symmetric`() {
        val left = 0x123456789ABCDEF0L
        val right = 0x0FEDCBA987654321L

        assertThat(Hasher.hammingDistance(left, right))
            .isEqualTo(Hasher.hammingDistance(right, left))
    }

    @Test
    fun `burst threshold separates near duplicates from unrelated frames`() {
        val base = 0x00000000000000FFL
        val nearDuplicate = 0x00000000000000F0L

        assertThat(Hasher.hammingDistance(base, nearDuplicate)).isAtMost(6)
        assertThat(Hasher.hammingDistance(base, base.inv())).isGreaterThan(12)
    }
}
