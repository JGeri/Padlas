package hu.padlas.feature.dedup.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal typealias FloatAnimatable = Animatable<Float, AnimationVector1D>

/** The entrance: the members fan out from the winner's position, 28 ms apart. */
@Composable
internal fun LaunchedFan(
    key: Any,
    spread: List<FloatAnimatable>,
    delays: List<Long>,
    spec: AnimationSpec<Float>,
) {
    LaunchedEffect(key) {
        spread.forEachIndexed { index, animatable ->
            launch {
                delay(delays.getOrElse(index) { 0L })
                animatable.animateTo(1f, spec)
            }
        }
    }
}

/**
 * The user cancelled the system dialog, so the collapse has to be taken back: the group is
 * still there and the card must look like it.
 */
@Composable
internal fun LaunchedReset(token: Int, active: Boolean, block: suspend CoroutineScope.() -> Unit) {
    LaunchedEffect(token) {
        if (active) block()
    }
}
