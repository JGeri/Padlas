package hu.padlas.feature.sort.ui

import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.unit.dp
import hu.padlas.feature.sort.SwipeDirection
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * The physics of the deck, kept out of the composables so the numbers can be read and
 * argued about without a frame in front of you.
 */
internal object Deck {

    /** Past 40% of the width the decision is made; the spec's number, not a guess. */
    const val ThresholdFraction = 0.40f

    const val MaxTiltDegrees = 8f

    /**
     * Rotation pivot as a fraction of the card height: the bottom edge is 1.0, so 3.0
     * pins the card two heights below itself. The card hangs off that pin, and at the
     * commit threshold the swing alone carries it almost exactly as far as the finger
     * travelled - which is why the drag only adds the remainder.
     */
    const val PivotDepth = 3f

    const val BehindScale = 0.94f

    /** The lid tilts down first; the card only starts falling after this beat. */
    const val AnticipationMs = 60L

    const val DegreesToRadians = (Math.PI / 180.0).toFloat()

    val BinFall = 180.dp

    fun tiltDegrees(dragX: Float, thresholdPx: Float): Float =
        if (thresholdPx <= 0f) 0f else (dragX / thresholdPx).coerceIn(-1f, 1f) * MaxTiltDegrees

    /** 0 at rest, 1 at the commit threshold. Drives the tilt and the card behind. */
    fun dragProgress(dragX: Float, dragY: Float, thresholdPx: Float): Float =
        if (thresholdPx <= 0f) 0f else (max(abs(dragX), abs(dragY)) / thresholdPx).coerceIn(0f, 1f)

    /** Null while the drag is still undecided. The dominant axis wins. */
    fun committedDirection(dragX: Float, dragY: Float, thresholdPx: Float): SwipeDirection? {
        if (thresholdPx <= 0f) return null
        return if (abs(dragX) >= abs(dragY)) {
            when {
                dragX <= -thresholdPx -> SwipeDirection.LEFT
                dragX >= thresholdPx -> SwipeDirection.RIGHT
                else -> null
            }
        } else {
            when {
                dragY <= -thresholdPx -> SwipeDirection.UP
                dragY >= thresholdPx -> SwipeDirection.DOWN
                else -> null
            }
        }
    }

    /** Colour drains only on the way to the attic; the bin keeps the picture intact. */
    fun colourAlpha(direction: SwipeDirection, progress: Float): Float =
        if (direction == SwipeDirection.UP) 1f - progress.coerceIn(0f, 1f) else 1f

    fun outlineAlpha(direction: SwipeDirection, progress: Float): Float =
        if (direction == SwipeDirection.UP) (progress * 2.2f).coerceIn(0f, 1f) else 0f

    fun fadeTail(progress: Float, start: Float): Float =
        ((progress - start) / (1f - start)).coerceIn(0f, 1f)
}

/**
 * The hanging card. Rotating around a pin this deep already swings the card sideways, so
 * the drag only has to supply what the swing did not.
 */
internal fun GraphicsLayerScope.applyDrag(dragX: Float, dragY: Float, thresholdPx: Float) {
    val tilt = Deck.tiltDegrees(dragX, thresholdPx)
    val arm = (Deck.PivotDepth - 0.5f) * size.height
    rotationZ = tilt
    translationX = dragX - arm * sin(tilt * Deck.DegreesToRadians)
    translationY = dragY
}

/**
 * One function for both directions of travel: a commit runs it 0 -> 1, an undo runs it
 * 1 -> 0 on a snapback spring, which is why the card climbs back out of the tray instead
 * of reappearing.
 */
internal fun GraphicsLayerScope.applyFlight(
    direction: SwipeDirection,
    progress: Float,
    releaseX: Float,
    releaseY: Float,
    deckWidth: Float,
    deckHeight: Float,
) {
    val p = progress.coerceIn(0f, 1f)
    when (direction) {
        SwipeDirection.LEFT -> {
            // It falls into the tray: shrinks and drops. It never flies off screen.
            val scale = 1f - 0.65f * p
            scaleX = scale
            scaleY = scale
            translationX = releaseX * (1f - p)
            translationY = releaseY + Deck.BinFall.toPx() * p
            rotationZ = -6f * p
        }

        SwipeDirection.UP -> {
            val scale = 1f - 0.15f * p
            scaleX = scale
            scaleY = scale
            translationX = releaseX * (1f - p)
            translationY = releaseY - (deckHeight * 0.6f + size.height) * p
        }

        SwipeDirection.RIGHT -> {
            translationX = releaseX + deckWidth * 1.2f * p
            translationY = releaseY
            rotationZ = Deck.MaxTiltDegrees * (1f - p)
            alpha = 1f - Deck.fadeTail(p, 0.6f)
        }

        SwipeDirection.DOWN -> {
            val scale = 1f - 0.12f * p
            scaleX = scale
            scaleY = scale
            translationX = releaseX * (1f - p)
            translationY = releaseY + deckHeight * 0.45f * p
            alpha = 1f - Deck.fadeTail(p, 0.5f)
        }
    }
}
