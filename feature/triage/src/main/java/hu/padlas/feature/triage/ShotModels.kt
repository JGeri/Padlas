package hu.padlas.feature.triage

import hu.padlas.core.database.entity.ShotKind

/**
 * Only the symbologies the triage reasons about. ML Kit formats are mapped onto this at
 * the edge so the classifier stays free of Google Play types and stays unit testable.
 */
enum class BarcodeFormat { QR, AZTEC, OTHER }

data class BarcodeHit(
    val format: BarcodeFormat,
    val rawValue: String? = null,
)

/** Everything the classifier is allowed to look at. No clock, no file, no context. */
data class ShotInput(
    val text: String,
    val barcodes: List<BarcodeHit> = emptyList(),
    val width: Int = 0,
    val height: Int = 0,
)

/** Which evidence decided the kind. The Kidobó shows this, so the UI can explain itself. */
enum class TriageSignal {
    SECRET_PHRASE,
    KEY_BLOCK,
    SEED_PHRASE,
    BARCODE_WITH_DATE,
    IBAN,
    RECEIPT_WORD,
    AMOUNT_WITH_DATE,
    CURRENCY_AMOUNT,
    STREET_SUFFIX,
    POSTCODE,
    ROUTE_WORD,
    CHAT_TIMESTAMP,
    SHORT_LINE_RUNS,
    SPARSE_TEXT,
    TALL_ASPECT,
    NONE,
}

data class ShotVerdict(
    val kind: ShotKind,
    val confidence: Float,
    val signal: TriageSignal,
    /** Meaningful for [ShotKind.TICKET] only: the date in the text is already behind us. */
    val expired: Boolean = false,
    /** Start of the day of the date the classifier found, UTC. */
    val dateMillis: Long? = null,
)

/** A calendar day found in recognised text, as a half-open UTC interval. */
data class ShotDate(val startMillis: Long, val endMillis: Long)
