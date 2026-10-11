package hu.padlas.core.design.motion

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Hands out entrance slots in composition order. Only what is composed while the screen
 * opens sits down one after the other; rows scrolled in later are simply there, because a
 * row that waits its turn mid-scroll reads as lag.
 */
@Stable
class Entrance internal constructor(private val enabled: Boolean) {
    private val start = SystemClock.uptimeMillis()
    private var next = 0

    internal fun claim(): Int? {
        if (!enabled || SystemClock.uptimeMillis() - start > EntranceWindowMs) return null
        return next++
    }
}

@Composable
fun rememberEntrance(): Entrance {
    val reduced = LocalReducedMotion.current
    return remember { Entrance(enabled = !reduced) }
}

/** Rises a few dp and fades in on its own spring, delayed by its place in the queue. */
@Composable
fun Modifier.entrance(entrance: Entrance): Modifier {
    val slot = remember { entrance.claim() } ?: return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(minOf(slot, Motion.StaggerCap).toLong() * EntranceStepMs)
        progress.animateTo(1f, Motion.settle())
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * EntranceRise.toPx()
    }
}

/** Removals collapse and the rest closes up on a spring instead of jumping. */
@Composable
fun LazyItemScope.itemMotion(): Modifier =
    if (LocalReducedMotion.current) {
        Modifier
    } else {
        Modifier.animateItem(
            fadeInSpec = Motion.settle(),
            placementSpec = Motion.settle(),
            fadeOutSpec = Motion.snapSpring(),
        )
    }

/** The grid twin of [LazyItemScope.itemMotion]; the two scopes share no supertype. */
@Composable
fun LazyGridItemScope.itemMotion(): Modifier =
    if (LocalReducedMotion.current) {
        Modifier
    } else {
        Modifier.animateItem(
            fadeInSpec = Motion.settle(),
            placementSpec = Motion.settle(),
            fadeOutSpec = Motion.snapSpring(),
        )
    }

private const val EntranceWindowMs = 450L
private const val EntranceStepMs = 45L
private val EntranceRise = 18.dp
