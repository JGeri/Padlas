package hu.padlas.core.common.format

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.After
import org.junit.Before
import org.junit.Test

class FormatTest {

    private val original = Locale.getDefault()

    @Before
    fun hungarian() = Locale.setDefault(Locale.forLanguageTag("hu-HU"))

    @After
    fun restore() = Locale.setDefault(original)

    private val nbsp = ' '

    @Test
    fun `bytes uses binary units and a comma decimal`() {
        assertThat(Format.bytes(0)).isEqualTo("0 B")
        assertThat(Format.bytes(1023)).isEqualTo("1${nbsp}023 B")
        assertThat(Format.bytes(1024)).isEqualTo("1,0 KB")
        assertThat(Format.bytes(13_314_398_617)).startsWith("12,4")
        assertThat(Format.bytes(13_314_398_617)).endsWith("GB")
    }

    @Test
    fun `bytes drops the decimal above one hundred units`() {
        assertThat(Format.bytes(128L * 1024 * 1024 * 1024)).isEqualTo("128 GB")
    }

    @Test
    fun `negative sizes keep their sign`() {
        assertThat(Format.bytes(-2048)).isEqualTo("-2,0 KB")
    }

    @Test
    fun `value and unit split cleanly for the rolling hero number`() {
        val bytes = 88_446_744_073L
        assertThat(Format.bytesValue(bytes) + " " + Format.bytesUnit(bytes))
            .isEqualTo(Format.bytes(bytes))
    }

    @Test
    fun `counts group with a narrow no-break space`() {
        assertThat(Format.count(2341)).isEqualTo("2${nbsp}341")
        assertThat(Format.count(999)).isEqualTo("999")
        assertThat(Format.count(25_431)).isEqualTo("25${nbsp}431")
    }

    @Test
    fun `percent rounds to whole numbers`() {
        assertThat(Format.percent(0.344f)).isEqualTo("34%")
        assertThat(Format.percent(1f)).isEqualTo("100%")
    }

    @Test
    fun `percent of nothing is zero, not a crash`() {
        assertThat(Format.percent(0f / 0f)).isEqualTo("0%")
    }

    @Test
    fun `duration switches to hours when needed`() {
        assertThat(Format.duration(65_000)).isEqualTo("1:05")
        assertThat(Format.duration(3_725_000)).isEqualTo("1:02:05")
    }

    @Test
    fun `english uses a dot decimal and comma grouping`() {
        Locale.setDefault(Locale.US)
        assertThat(Format.bytes(1024)).isEqualTo("1.0 KB")
        assertThat(Format.count(25_431)).isEqualTo("25,431")
    }
}
