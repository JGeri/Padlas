package hu.padlas.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Size
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.padlas.core.common.di.HashDispatcher
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

data class PerceptualResult(val dHash: Long, val blurScore: Float)

/**
 * Spec 2.4. Two phase content hashing plus the perceptual pair. Everything runs on the
 * dedicated hash dispatcher so a 25 000 file pass never competes with recomposition.
 */
@Singleton
class Hasher @Inject constructor(
    @ApplicationContext private val context: Context,
    @HashDispatcher private val dispatcher: CoroutineDispatcher,
) {

    /** size + first 64 KB + last 64 KB. The disk never reads the middle of the file. */
    suspend fun quickHash(uri: Uri, sizeBytes: Long): String? = withContext(dispatcher) {
        runCatching { computeQuickHash(uri, sizeBytes) }.getOrNull()
    }

    suspend fun sha256(uri: Uri): String? = withContext(dispatcher) {
        runCatching { digestAll(uri, SHA_256) }.getOrNull()
    }

    /** Drive returns MD5 for uploaded files; INV-1 compares it against this one. */
    suspend fun md5(uri: Uri): String? = withContext(dispatcher) {
        runCatching { digestAll(uri, MD5) }.getOrNull()
    }

    /** One pixel decode feeds both the 9x8 dHash and the Laplace blur score. */
    suspend fun perceptual(uri: Uri): PerceptualResult? = withContext(dispatcher) {
        runCatching { computePerceptual(uri) }.getOrNull()
    }

    private fun computeQuickHash(uri: Uri, sizeBytes: Long): String? {
        val digest = MessageDigest.getInstance(SHA_256)
        digest.update(sizeBytes.toString().toByteArray(Charsets.US_ASCII))
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        descriptor.use { parcel ->
            FileInputStream(parcel.fileDescriptor).use { stream ->
                val buffer = ByteArray(CHUNK_BYTES)
                consume(stream, digest, buffer, CHUNK_BYTES.toLong())
                if (sizeBytes > CHUNK_BYTES * 2L) {
                    stream.channel.position(sizeBytes - CHUNK_BYTES)
                    consume(stream, digest, buffer, CHUNK_BYTES.toLong())
                } else {
                    // Under 128 KB the head and the tail overlap, so hash the whole file.
                    consume(stream, digest, buffer, Long.MAX_VALUE)
                }
            }
        }
        return digest.digest().toHex()
    }

    private fun digestAll(uri: Uri, algorithm: String): String? {
        val digest = MessageDigest.getInstance(algorithm)
        val stream = context.contentResolver.openInputStream(uri) ?: return null
        stream.use { consume(it, digest, ByteArray(CHUNK_BYTES), Long.MAX_VALUE) }
        return digest.digest().toHex()
    }

    private fun consume(
        stream: InputStream,
        digest: MessageDigest,
        buffer: ByteArray,
        limit: Long,
    ) {
        var remaining = limit
        while (remaining > 0L) {
            val wanted = minOf(buffer.size.toLong(), remaining).toInt()
            val read = stream.read(buffer, 0, wanted)
            if (read <= 0) return
            digest.update(buffer, 0, read)
            remaining -= read
        }
    }

    private fun computePerceptual(uri: Uri): PerceptualResult? {
        val bitmap = decodeSmall(uri) ?: return null
        return try {
            val gray = grayscale(bitmap)
            PerceptualResult(dHashOf(gray), laplaceVariance(gray))
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun decodeSmall(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val shortSide = minOf(bounds.outWidth, bounds.outHeight)
        if (shortSide <= 0) {
            // Video and anything BitmapFactory cannot open still has a system thumbnail.
            return runCatching {
                context.contentResolver.loadThumbnail(uri, Size(GRID, GRID), null)
            }.getOrNull()
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(shortSide)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun sampleSizeFor(shortSide: Int): Int {
        var sample = 1
        while (shortSide / (sample * 2) >= GRID) sample *= 2
        return sample
    }

    private fun grayscale(bitmap: Bitmap): FloatArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, GRID, GRID, true)
        val pixels = IntArray(GRID * GRID)
        scaled.getPixels(pixels, 0, GRID, 0, 0, GRID, GRID)
        if (scaled !== bitmap) scaled.recycle()
        val gray = FloatArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            gray[i] = LUMA_RED * ((pixel shr 16) and 0xFF) +
                LUMA_GREEN * ((pixel shr 8) and 0xFF) +
                LUMA_BLUE * (pixel and 0xFF)
        }
        return gray
    }

    /** Box average the 256x256 plane down to 9x8, then compare horizontal neighbours. */
    private fun dHashOf(gray: FloatArray): Long {
        val cells = FloatArray(DHASH_COLUMNS * DHASH_ROWS)
        val cellWidth = GRID.toFloat() / DHASH_COLUMNS
        val cellHeight = GRID.toFloat() / DHASH_ROWS
        for (row in 0 until DHASH_ROWS) {
            val top = (row * cellHeight).toInt()
            val bottom = minOf(GRID, maxOf(top + 1, ((row + 1) * cellHeight).toInt()))
            for (column in 0 until DHASH_COLUMNS) {
                val left = (column * cellWidth).toInt()
                val right = minOf(GRID, maxOf(left + 1, ((column + 1) * cellWidth).toInt()))
                var sum = 0f
                var count = 0
                for (y in top until bottom) {
                    val offset = y * GRID
                    for (x in left until right) {
                        sum += gray[offset + x]
                        count++
                    }
                }
                cells[row * DHASH_COLUMNS + column] = if (count == 0) 0f else sum / count
            }
        }
        var hash = 0L
        for (row in 0 until DHASH_ROWS) {
            for (column in 0 until DHASH_COLUMNS - 1) {
                val left = cells[row * DHASH_COLUMNS + column]
                val right = cells[row * DHASH_COLUMNS + column + 1]
                val bit = if (left > right) 1L else 0L
                hash = (hash shl 1) or bit
            }
        }
        return hash
    }

    /** Variance of the 4-neighbour Laplace response. Only comparable inside a group. */
    private fun laplaceVariance(gray: FloatArray): Float {
        var sum = 0.0
        var sumSquares = 0.0
        var count = 0
        for (y in 1 until GRID - 1) {
            val row = y * GRID
            for (x in 1 until GRID - 1) {
                val index = row + x
                val response = gray[index - GRID] + gray[index + GRID] +
                    gray[index - 1] + gray[index + 1] - 4f * gray[index]
                sum += response
                sumSquares += response.toDouble() * response
                count++
            }
        }
        if (count == 0) return 0f
        val mean = sum / count
        return ((sumSquares / count) - mean * mean).toFloat()
    }

    companion object {
        fun hammingDistance(a: Long, b: Long): Int = (a xor b).countOneBits()

        private const val SHA_256 = "SHA-256"
        private const val MD5 = "MD5"
        private const val CHUNK_BYTES = 64 * 1024
        private const val GRID = 256
        private const val DHASH_COLUMNS = 9
        private const val DHASH_ROWS = 8
        private const val LUMA_RED = 0.299f
        private const val LUMA_GREEN = 0.587f
        private const val LUMA_BLUE = 0.114f
    }
}

private val HEX_DIGITS = "0123456789abcdef".toCharArray()

private fun ByteArray.toHex(): String {
    val out = CharArray(size * 2)
    for (i in indices) {
        val value = this[i].toInt() and 0xFF
        out[i * 2] = HEX_DIGITS[value ushr 4]
        out[i * 2 + 1] = HEX_DIGITS[value and 0x0F]
    }
    return String(out)
}
