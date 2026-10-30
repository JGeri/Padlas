package hu.padlas.feature.triage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.dao.AuditDao
import hu.padlas.core.database.dao.SearchDao
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.AssetSearchEntity
import hu.padlas.core.database.entity.AuditAction
import hu.padlas.core.database.entity.AuditEntry
import hu.padlas.core.database.entity.ShotKind
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device OCR + barcode pass over the ephemeral set (spec M7).
 *
 * The whole cost of the feature is in the decode, not in the models: a 12 MP frame takes
 * roughly ten times as long to recognise as a downscaled one and buys nothing, because
 * screenshot type is already legible at 2000px. Full resolution turns a twenty minute
 * pass into a four hour one.
 */
@Singleton
class ScreenshotTriage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assetDao: AssetDao,
    private val searchDao: SearchDao,
    private val auditDao: AuditDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private val scanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_AZTEC)
                .build(),
        )
    }

    /**
     * Drains the triage queue in batches. Returns how many assets it got through, so the
     * worker can decide whether another run is worth scheduling.
     */
    suspend fun runPass(
        budget: Int = DEFAULT_BUDGET,
        onProgress: suspend (done: Int, budget: Int) -> Unit = { _, _ -> },
    ): Int = withContext(io) {
        var done = 0
        var bytes = 0L
        try {
            while (done < budget && currentCoroutineContext().isActive) {
                val batch = assetDao.needingTriage(minOf(BATCH, budget - done))
                if (batch.isEmpty()) break
                for (asset in batch) {
                    if (!currentCoroutineContext().isActive) break
                    triage(asset)
                    bytes += asset.sizeBytes
                    done++
                }
                onProgress(done, budget)
            }
        } finally {
            // One audit row per pass, not per asset or batch: reading a file is not a state
            // change, and a row per sixty shots drowned the log the user actually reads. A
            // cancelled pass still reports what it got through.
            if (done > 0) {
                withContext(NonCancellable) {
                    auditDao.insert(
                        AuditEntry(
                            timestamp = System.currentTimeMillis(),
                            action = AuditAction.OCR,
                            assetId = null,
                            sizeBytes = bytes,
                            reversible = false,
                            note = "${Format.count(done)} képernyőkép átnézve",
                        ),
                    )
                }
            }
        }
        done
    }

    /**
     * Recognises one asset and persists the verdict. Always marks the asset triaged, even
     * when the decode fails - an asset that stays at `triagedAt = 0` comes back in the
     * next batch forever.
     */
    suspend fun triage(asset: Asset): ShotVerdict = withContext(io) {
        val now = System.currentTimeMillis()
        val uri = Uri.parse(asset.uri)
        val bitmap = runCatching { decodeDownscaled(uri) }.getOrNull()
        if (bitmap == null) {
            assetDao.markTriaged(asset.mediaStoreId, now)
            return@withContext ShotVerdict(ShotKind.OTHER, 0f, TriageSignal.NONE)
        }

        val read = runCatching { read(bitmap, asset) }
        // A cancelled await returns while ML Kit is still reading the pixels on its own
        // thread; recycling under it pulls the bitmap away mid-recognition. Only a
        // finished task has let go of it.
        read.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        bitmap.recycle()

        val input = read.getOrNull()
        if (input == null) {
            assetDao.markTriaged(asset.mediaStoreId, now)
            return@withContext ShotVerdict(ShotKind.OTHER, 0f, TriageSignal.NONE)
        }

        val verdict = ShotClassifier.classify(input, now)
        val text = input.text.trim().takeIf { it.isNotEmpty() }
        assetDao.setTriage(asset.mediaStoreId, text, verdict.kind, now)
        if (text != null) {
            searchDao.insert(AssetSearchEntity(assetId = asset.mediaStoreId.toString(), text = text))
        }
        verdict
    }

    fun close() {
        runCatching { recognizer.close() }
        runCatching { scanner.close() }
    }

    private suspend fun read(bitmap: Bitmap, asset: Asset): ShotInput {
        val image = InputImage.fromBitmap(bitmap, 0)
        val text = recognizer.process(image).await().text
        val barcodes = scanner.process(image).await().map { it.toHit() }
        return ShotInput(
            text = text,
            barcodes = barcodes,
            width = if (asset.width > 0) asset.width else bitmap.width,
            height = if (asset.height > 0) asset.height else bitmap.height,
        )
    }

    /**
     * Power of two sampling straight out of the decoder: the full frame never reaches the
     * heap, so peak memory stays flat regardless of what the camera produced.
     */
    private fun decodeDownscaled(uri: Uri, maxEdge: Int = MAX_EDGE): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null

        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / (sample * 2) >= maxEdge) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun Barcode.toHit(): BarcodeHit = BarcodeHit(
        format = when (format) {
            Barcode.FORMAT_QR_CODE -> BarcodeFormat.QR
            Barcode.FORMAT_AZTEC -> BarcodeFormat.AZTEC
            else -> BarcodeFormat.OTHER
        },
        rawValue = rawValue,
    )

    private companion object {
        const val BATCH = 60
        const val DEFAULT_BUDGET = 600

        /** Screenshots stay untouched, 12 MP camera frames come down to about 3 MP. */
        const val MAX_EDGE = 1920
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
