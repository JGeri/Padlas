package hu.padlas.feature.triage

import hu.padlas.core.database.entity.ShotKind

/**
 * Pure and deterministic: no clock, no Android, no ML Kit. The precedence is the product
 * rule of spec M7 - SECRET first, OTHER as the default - and it is asymmetric on purpose.
 * Protecting a meme costs a few megabytes; deleting a recovery code costs an account,
 * so every ambiguous signal is resolved upwards.
 */
object ShotClassifier {

    fun classify(input: ShotInput, now: Long): ShotVerdict {
        val raw = input.text
        val flat = deaccent(raw.lowercase())
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }

        secret(raw, flat)?.let { return it }
        ticket(input, raw, now)?.let { return it }
        receipt(raw, flat)?.let { return it }
        place(raw, flat)?.let { return it }
        chat(lines)?.let { return it }
        return other(input, raw, lines)
    }

    // ---- SECRET (INV-1c) ----------------------------------------------------

    private fun secret(raw: String, flat: String): ShotVerdict? {
        val phrase = SECRET_PHRASES.any { flat.contains(it) }
        val block = hasKeyBlock(raw)
        val seed = hasSeedPhrase(flat)
        return when {
            phrase -> ShotVerdict(ShotKind.SECRET, if (block || seed) 0.99f else 0.95f, TriageSignal.SECRET_PHRASE)
            seed -> ShotVerdict(ShotKind.SECRET, 0.90f, TriageSignal.SEED_PHRASE)
            block -> ShotVerdict(ShotKind.SECRET, 0.80f, TriageSignal.KEY_BLOCK)
            else -> null
        }
    }

    /**
     * Four to six groups of four or five alphanumerics. At least two groups have to mix
     * letters and digits, otherwise every IBAN and every card number on a receipt would
     * end up locked away as a secret.
     */
    private fun hasKeyBlock(raw: String): Boolean = KEY_BLOCK.findAll(raw).any { match ->
        match.value.split(' ', '-').count { group ->
            group.any(Char::isDigit) && group.any(Char::isLetter)
        } >= 2
    }

    /**
     * A BIP39 list is 12/15/18/21/24 plain lowercase words and almost nothing else on the
     * screen; the ratio guard is what keeps an ordinary chat out of the secret bucket.
     */
    private fun hasSeedPhrase(flat: String): Boolean {
        val words = flat.split(SPLITTER).filter { word -> word.isNotEmpty() }
        if (words.size < 12) return false
        var longest = 0
        var run = 0
        for (word in words) {
            if (word.length in 3..8) {
                run++
                if (run > longest) longest = run
            } else {
                run = 0
            }
        }
        if (longest !in SEED_LENGTHS) return false
        return longest.toFloat() / words.size >= 0.5f
    }

    // ---- TICKET -------------------------------------------------------------

    private fun ticket(input: ShotInput, raw: String, now: Long): ShotVerdict? {
        val scannable = input.barcodes.any {
            it.format == BarcodeFormat.QR || it.format == BarcodeFormat.AZTEC
        }
        if (!scannable) return null
        val date = TextDates.latest(raw) ?: return null
        val expired = date.endMillis <= now
        return ShotVerdict(
            kind = ShotKind.TICKET,
            confidence = if (expired) 0.85f else 0.92f,
            signal = TriageSignal.BARCODE_WITH_DATE,
            expired = expired,
            dateMillis = date.startMillis,
        )
    }

    // ---- RECEIPT ------------------------------------------------------------

    private fun receipt(raw: String, flat: String): ShotVerdict? {
        val iban = IBAN_HU.containsMatchIn(raw) || IBAN_ANY.containsMatchIn(raw) ||
            HU_ACCOUNT.containsMatchIn(raw)
        val word = RECEIPT_WORDS.containsMatchIn(flat)
        val amount = AMOUNT.containsMatchIn(raw)
        val currency = CURRENCY.containsMatchIn(raw)
        val bonus = 0.02f * (listOf(iban, word, amount, currency).count { it } - 1).coerceAtLeast(0)
        return when {
            iban -> ShotVerdict(ShotKind.RECEIPT, 0.90f + bonus, TriageSignal.IBAN)
            word -> ShotVerdict(ShotKind.RECEIPT, 0.85f + bonus, TriageSignal.RECEIPT_WORD)
            amount && TextDates.latest(raw) != null ->
                ShotVerdict(ShotKind.RECEIPT, 0.70f + bonus, TriageSignal.AMOUNT_WITH_DATE)

            currency -> ShotVerdict(ShotKind.RECEIPT, 0.60f + bonus, TriageSignal.CURRENCY_AMOUNT)
            else -> null
        }
    }

    // ---- PLACE --------------------------------------------------------------

    private fun place(raw: String, flat: String): ShotVerdict? {
        val street = STREET.containsMatchIn(flat)
        val postcode = POSTCODE.containsMatchIn(raw)
        val route = ROUTE_WORDS.containsMatchIn(flat) || DISTANCE.containsMatchIn(flat)
        val bonus = 0.03f * (listOf(street, postcode, route).count { it } - 1).coerceAtLeast(0)
        return when {
            street -> ShotVerdict(ShotKind.PLACE, 0.85f + bonus, TriageSignal.STREET_SUFFIX)
            postcode -> ShotVerdict(ShotKind.PLACE, 0.75f + bonus, TriageSignal.POSTCODE)
            route -> ShotVerdict(ShotKind.PLACE, 0.65f + bonus, TriageSignal.ROUTE_WORD)
            else -> null
        }
    }

    // ---- CHAT ---------------------------------------------------------------

    private fun chat(lines: List<String>): ShotVerdict? {
        if (lines.size < 6) return null
        val stamps = lines.count { TIME.containsMatchIn(it) }
        val short = lines.count { it.length <= SHORT_LINE }
        val alternations = alternations(lines)
        return when {
            stamps >= 3 && short * 2 >= lines.size ->
                ShotVerdict(ShotKind.CHAT, 0.80f, TriageSignal.CHAT_TIMESTAMP)

            lines.size >= 8 && short * 10 >= lines.size * 7 && alternations >= 3 ->
                ShotVerdict(ShotKind.CHAT, 0.60f, TriageSignal.SHORT_LINE_RUNS)

            else -> null
        }
    }

    /** Bubbles alternate sides, and OCR sees that as runs of very short and medium lines. */
    private fun alternations(lines: List<String>): Int {
        var count = 0
        var previous: Boolean? = null
        for (line in lines) {
            val tiny = line.length <= TINY_LINE
            if (previous != null && previous != tiny) count++
            previous = tiny
        }
        return count
    }

    // ---- OTHER --------------------------------------------------------------

    private fun other(input: ShotInput, raw: String, lines: List<String>): ShotVerdict {
        val density = raw.trim().length
        val tall = input.width > 0 && input.height.toFloat() / input.width >= 1.9f
        return when {
            density < 40 -> ShotVerdict(ShotKind.OTHER, if (tall) 0.75f else 0.60f, TriageSignal.SPARSE_TEXT)
            tall && lines.size <= 6 -> ShotVerdict(ShotKind.OTHER, 0.50f, TriageSignal.TALL_ASPECT)
            else -> ShotVerdict(ShotKind.OTHER, 0.30f, TriageSignal.NONE)
        }
    }

    // ---- signals ------------------------------------------------------------

    private const val SHORT_LINE = 42
    private const val TINY_LINE = 20

    private val SEED_LENGTHS = setOf(12, 15, 18, 21, 24)

    /** Digits are separators, not words: numbered seed lists must survive the split. */
    private val SPLITTER = Regex("[^a-z]+")

    /** De-accented, lowercase. "Helyreállítási kód" arrives here as "helyreallitasi kod". */
    private val SECRET_PHRASES = listOf(
        "recovery code", "recovery key", "recovery phrase", "backup code", "backup key",
        "seed phrase", "mnemonic", "private key", "secret key",
        "one-time password", "one time password", "otp code", "two-factor", "2fa",
        "mentokulcs", "helyreallitasi kod", "helyreallitasi kulcs", "biztonsagi kod",
        "egyszeri jelszo", "privat kulcs", "titkos kulcs", "jelszo", "jelszav",
    )

    private val KEY_BLOCK =
        Regex("(?<![A-Za-z0-9])(?:[A-Za-z0-9]{4,5}[ -]){3,5}[A-Za-z0-9]{4,5}(?![A-Za-z0-9])")

    private val IBAN_HU = Regex("\\bHU\\d{2}[0-9 ]{16,30}")
    private val IBAN_ANY = Regex("\\b[A-Z]{2}\\d{2}[A-Z0-9]{11,30}\\b")
    private val HU_ACCOUNT = Regex("(?<!\\d)\\d{8}-\\d{8}(-\\d{8})?(?!\\d)")

    private val RECEIPT_WORDS = Regex(
        "\\b(szamlaszam|szamla|nyugta|bizonylat|osszesen|vegosszeg|fizetendo|afa|blokk|" +
            "receipt|invoice|subtotal|total)\\b",
    )

    /** Grouped thousands only: a bare "12,05" is indistinguishable from a date fragment. */
    private val AMOUNT = Regex("(?<!\\d)\\d{1,3}(?:[   .]\\d{3})+(?:[,.]\\d{2})?(?!\\d)")

    private val CURRENCY = Regex(
        "\\d[\\d   .,]{0,12}\\s?(?:ft|huf|eur|usd|€|\\$)(?![a-z])",
        RegexOption.IGNORE_CASE,
    )

    private val STREET =
        Regex("\\b[a-z]{3,}\\s(utca|ut|ter|korut|koz|rakpart|setany|sugarut|krt)\\b")

    private val POSTCODE =
        Regex("(?<!\\d)[1-9]\\d{3}\\s+[A-ZÁÉÍÓÖŐÚÜŰ]\\p{L}{2,}")

    private val ROUTE_WORDS = Regex("\\b(utvonal|terkep|navigacio|iranyitoszam|route|directions)\\b")

    private val DISTANCE = Regex("(?<!\\d)\\d+([,.]\\d+)?\\s?km\\b")

    private val TIME = Regex("(?<!\\d)([01]?\\d|2[0-3]):[0-5]\\d(?!\\d)")
}
