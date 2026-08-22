package hu.padlas.core.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.motion.staggerDelayMs
import kotlinx.coroutines.delay

private val EntranceOffset = 12.dp

/**
 * The difference between a list that appeared and a list that sat down. Siblings arrive one
 * after the other, top to bottom, each on its own spring.
 */
@Composable
fun StaggeredColumnItem(index: Int, content: @Composable () -> Unit) {
    val entrance = remember { Animatable(0f) }
    val spec = motionSpec(Motion.settle<Float>())
    val delayMs = staggerDelayMs(index)

    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        entrance.animateTo(1f, spec)
    }

    Box(
        modifier = Modifier.graphicsLayer {
            alpha = entrance.value
            translationY = (1f - entrance.value) * EntranceOffset.toPx()
        },
    ) {
        content()
    }
}
