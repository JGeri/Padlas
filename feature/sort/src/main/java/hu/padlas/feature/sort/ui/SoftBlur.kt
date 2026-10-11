package hu.padlas.feature.sort.ui

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.roundToInt

/**
 * A heavy blur baked into a small bitmap once, when the thumbnail loads. A blur effect on
 * the layer would be recomputed on every frame the card or the background moves; this
 * shrinks the picture to a dozen pixels across and grows it back in steps, each one
 * smoothing the last, which at this strength looks the same.
 */
internal object SoftBlur : Transformation() {
    override val cacheKey: String = "soft-blur-12"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val source = if (input.config == Bitmap.Config.HARDWARE) input.copy(Bitmap.Config.ARGB_8888, false) else input
        val width = 12
        val height = (width * source.height / source.width.toFloat()).roundToInt().coerceIn(1, 48)
        var out = Bitmap.createScaledBitmap(source, width, height, true)
        repeat(3) { out = Bitmap.createScaledBitmap(out, out.width * 3, out.height * 3, true) }
        return out
    }
}
