package hu.padlas.core.design.component

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import hu.padlas.core.design.motion.Motion

/**
 * The public [hu.padlas.core.design.motion.motionSpec] widens the type to AnimationSpec and
 * can only be read from composition. Enter/exit transitions need the finite type, and the
 * animation drivers in this package run inside coroutines, so they take the flag by value.
 */
internal fun <T> finiteSpec(reduced: Boolean, spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    if (reduced) snap() else spec

/** The same ladder as [hu.padlas.core.design.motion.staggerDelayMs], callable off composition. */
internal fun staggerDelayFor(index: Int, reduced: Boolean): Int = when {
    reduced -> 0
    index >= Motion.StaggerCap -> Motion.StaggerCap * Motion.StaggerMs
    else -> index * Motion.StaggerMs
}

/** One physical pixel, like every other rule in the app; 1.dp would blur it to three. */
internal fun Modifier.hairlineOutline(color: Color, shape: Shape): Modifier = drawBehind {
    drawOutline(shape.createOutline(size, layoutDirection, this), color = color, style = Stroke(1f))
}
