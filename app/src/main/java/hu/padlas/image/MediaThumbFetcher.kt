package hu.padlas.image

import android.content.Context
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import hu.padlas.core.media.MediaThumb

/**
 * Grids of thousands of photos and videos load through the platform thumbnail cache rather
 * than decoding every original: it is one call for both media types and it is already
 * warm for anything the system gallery has shown.
 */
internal class MediaThumbFetcher(
    private val data: MediaThumb,
    private val context: Context,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val bitmap = context.contentResolver.loadThumbnail(data.uri, Size(data.size, data.size), null)
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<MediaThumb> {
        override fun create(data: MediaThumb, options: Options, imageLoader: ImageLoader): Fetcher =
            MediaThumbFetcher(data, context)
    }
}

internal class MediaThumbKeyer : Keyer<MediaThumb> {
    override fun key(data: MediaThumb, options: Options): String = "thumb:${data.uri}#${data.size}"
}
