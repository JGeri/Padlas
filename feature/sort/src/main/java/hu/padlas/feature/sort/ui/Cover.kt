package hu.padlas.feature.sort.ui

import android.net.Uri

/**
 * A month's cover is its newest picture, which is also the first card of a newest-first
 * deck. Tile and card share a memory-cache entry, so the card shows the tile's thumbnail
 * at once while the full image loads, and a morph key, so the picture flies from the tile
 * into the card.
 */
internal object Cover {
    fun cacheKey(uri: Uri): String = "swipe-cover:$uri"

    fun morphKey(uri: Uri): String = "swipe-cover-$uri"
}
