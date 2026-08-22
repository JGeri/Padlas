package hu.padlas.core.media

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.padlas.core.common.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

data class ExifData(
    val dateTaken: Long?,
    val hasCameraTags: Boolean,
    val latitude: Double?,
    val longitude: Double?,
    val rotationDegrees: Int,
) {
    companion object {
        val EMPTY = ExifData(
            dateTaken = null,
            hasCameraTags = false,
            latitude = null,
            longitude = null,
            rotationDegrees = 0,
        )
    }
}

@Singleton
class ExifReader @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun read(uri: Uri): ExifData = withContext(io) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).toExifData()
            }
        }.getOrNull() ?: ExifData.EMPTY
    }

    private fun ExifInterface.toExifData(): ExifData {
        val location = latLong
        return ExifData(
            dateTaken = dateTimeOriginal ?: dateTime,
            hasCameraTags = hasCameraTags(),
            latitude = location?.getOrNull(0),
            longitude = location?.getOrNull(1),
            rotationDegrees = rotationDegrees,
        )
    }

    /**
     * Spec 1.5: a maker plus an exposure tag. Screenshots, downloads and app-generated PNGs
     * carry neither, which makes this the cheapest reliable "you took this" signal.
     */
    private fun ExifInterface.hasCameraTags(): Boolean {
        val make = getAttribute(ExifInterface.TAG_MAKE)
        if (make.isNullOrBlank()) return false
        val aperture = getAttribute(ExifInterface.TAG_F_NUMBER)
        val iso = getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
            ?: getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
        return !aperture.isNullOrBlank() || !iso.isNullOrBlank()
    }
}
