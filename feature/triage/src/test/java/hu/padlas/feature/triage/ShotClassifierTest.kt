package hu.padlas.feature.triage

import com.google.common.truth.Truth.assertThat
import hu.padlas.core.database.entity.ShotKind
import org.junit.Test

class ShotClassifierTest {

    private val now = 1_700_000_000_000L // 2023-11-14

    private fun classify(
        text: String,
        barcodes: List<BarcodeHit> = emptyList(),
        width: Int = 1080,
        height: Int = 2316,
    ) = ShotClassifier.classify(ShotInput(text, barcodes, width, height), now)

    @Test
    fun `recovery codes are secret`() {
        assertThat(classify("Recovery codes\nA1B2C 3D4E5 F6G7H").kind).isEqualTo(ShotKind.SECRET)
    }

    @Test
    fun `hungarian wording is recognised too`() {
        assertThat(classify("Helyreállítási kód: 8842-9911").kind).isEqualTo(ShotKind.SECRET)
        assertThat(classify("mentőkulcs elmentve").kind).isEqualTo(ShotKind.SECRET)
    }

    @Test
    fun `secret wins over every other signal`() {
        val verdict = classify(
            text = "Backup code 1234 5678\nSzámlaszám: HU42 1177 3016 1111 1018 0000 0000\n2024.01.02.",
            barcodes = listOf(BarcodeHit(BarcodeFormat.QR)),
        )

        assertThat(verdict.kind).isEqualTo(ShotKind.SECRET)
    }

    @Test
    fun `an iban makes it a receipt`() {
        val verdict = classify("Számlaszám: HU42 1177 3016 1111 1018 0000 0000\nÖsszesen: 12 400 Ft")

        assertThat(verdict.kind).isEqualTo(ShotKind.RECEIPT)
    }

    @Test
    fun `a barcode with a future date is a ticket worth keeping`() {
        val verdict = classify(
            text = "Beszállókártya\n2030.06.01. 18:40",
            barcodes = listOf(BarcodeHit(BarcodeFormat.AZTEC)),
        )

        assertThat(verdict.kind).isEqualTo(ShotKind.TICKET)
        assertThat(verdict.expired).isFalse()
    }

    @Test
    fun `a barcode with a past date is an expired ticket`() {
        val verdict = classify(
            text = "Jegy\n2019.03.11. 07:15",
            barcodes = listOf(BarcodeHit(BarcodeFormat.QR)),
        )

        assertThat(verdict.kind).isEqualTo(ShotKind.TICKET)
        assertThat(verdict.expired).isTrue()
    }

    @Test
    fun `an address is a place`() {
        val verdict = classify("Budapest, Váci utca 12.\n1052\nÚtvonal indítása")

        assertThat(verdict.kind).isEqualTo(ShotKind.PLACE)
    }

    @Test
    fun `many short lines with timestamps read as chat`() {
        val verdict = classify(
            """
            Szia 9:41
            ott vagy? 9:41
            mindjárt 9:42
            ok 9:42
            jövök 9:43
            5 perc 9:44
            """.trimIndent(),
        )

        assertThat(verdict.kind).isEqualTo(ShotKind.CHAT)
    }

    @Test
    fun `a nearly empty tall screenshot falls through to other`() {
        assertThat(classify("12:04", width = 1080, height = 2316).kind).isEqualTo(ShotKind.OTHER)
    }

    @Test
    fun `the verdict names the signal that decided it`() {
        assertThat(classify("Recovery code: AAAA-BBBB").signal).isEqualTo(TriageSignal.SECRET_PHRASE)
    }
}
