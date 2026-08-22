package hu.padlas.feature.archive.drive

/**
 * The whole arithmetic of a resumable upload, kept free of OkHttp so the boundaries can
 * be proven in a unit test. Getting an off-by-one wrong here does not fail loudly: it
 * uploads a corrupt file that still passes as "done", which is exactly what the MD5
 * verification exists to catch and what this object exists to avoid.
 */
internal object ResumableRange {

    /** Google requires every non-final chunk to be a multiple of 256 KB; 8 MB is one. */
    const val CHUNK_BYTES = 8 * 1024 * 1024

    fun chunkLength(offset: Long, totalBytes: Long, chunkBytes: Int = CHUNK_BYTES): Int {
        if (totalBytes <= 0L || offset >= totalBytes || offset < 0L) return 0
        val remaining = totalBytes - offset
        return if (remaining < chunkBytes) remaining.toInt() else chunkBytes
    }

    fun chunkCount(totalBytes: Long, chunkBytes: Int = CHUNK_BYTES): Int {
        if (totalBytes <= 0L) return 0
        return ((totalBytes + chunkBytes - 1) / chunkBytes).toInt()
    }

    /** "bytes 0-8388607/12000000". An empty payload degrades to the probe form. */
    fun contentRange(offset: Long, length: Int, totalBytes: Long): String {
        if (length <= 0) return probeRange(totalBytes)
        return "bytes $offset-${offset + length - 1}/$totalBytes"
    }

    /** Sent with an empty body to ask the server how much of the file it already holds. */
    fun probeRange(totalBytes: Long): String = "bytes */$totalBytes"

    /**
     * A 308 carries `Range: bytes=0-8388607`, meaning bytes 0..8388607 inclusive are
     * stored, so the next byte to send is 8388608. A missing header means nothing landed.
     */
    fun parseResumeOffset(rangeHeader: String?): Long {
        val header = rangeHeader?.trim().orEmpty()
        if (header.isEmpty()) return 0L
        val span = header.substringAfter('=', header)
        val last = span.substringAfterLast('-', "")
        val end = last.trim().toLongOrNull() ?: return 0L
        if (end < 0L) return 0L
        return end + 1L
    }

    fun isFinal(offset: Long, length: Int, totalBytes: Long): Boolean =
        totalBytes > 0L && offset + length >= totalBytes

    fun progress(bytesSent: Long, totalBytes: Long): Float {
        if (totalBytes <= 0L) return 0f
        return (bytesSent.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    }
}
