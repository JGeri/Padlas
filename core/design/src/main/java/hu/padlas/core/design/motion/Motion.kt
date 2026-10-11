package hu.padlas.core.design.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

/**
 * Springs, never tweens: a spring is what gives an element the sense of mass. The numbers
 * below are the motion vocabulary of the app.
 */
object Motion {

    /** Immediate feedback, no overshoot. */
    fun <T> snapSpring(): FiniteAnimationSpec<T> = spring(dampingRatio = 1.00f, stiffness = 1400f)

    /** Default UI transition: just barely perceptible overshoot. */
    fun <T> settle(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.85f, stiffness = 380f)

    /** Large elements: cards, sheets, pages. */
    fun <T> weight(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.72f, stiffness = 220f)

    /** Rejection and undo: it comes back, it does not reappear. */
    fun <T> snapback(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.55f, stiffness = 600f)

    /** Playful overshoot for things that pop in: badges, counters, stamps. */
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.5f, stiffness = 500f)

    /** Cards coming to rest after a drag: a little wobble, then still. */
    fun <T> rest(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.62f, stiffness = 420f)

    /** Slow and confident: the meter draining. */
    fun <T> drain(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.90f, stiffness = 90f)

    /** Milliseconds between sibling elements on entrance. */
    const val StaggerMs = 28

    /** After this many elements the rest arrives in one block. */
    const val StaggerCap = 12

    /** Nothing is allowed to block the user for longer than this. */
    const val MaxBlockingMs = 600
}

/**
 * True when the system animator scale is 0. The app stays fully usable, it just stops
 * moving; haptics deliberately stay on.
 */
val LocalReducedMotion = compositionLocalOf { false }

@Composable
@ReadOnlyComposable
fun <T> motionSpec(spec: FiniteAnimationSpec<T>): AnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spec

/**
 * [motionSpec] for enter/exit transitions and coroutine drivers: those need the finite
 * type, and they are often built where composition locals cannot be read.
 */
fun <T> finite(reduced: Boolean): FiniteAnimationSpec<T> = if (reduced) snap() else Motion.settle()

/** Badges and verdicts arrive with a little overshoot, so a change of state is noticed. */
fun popIn(reduced: Boolean): EnterTransition =
    scaleIn(if (reduced) snap() else Motion.bouncy(), initialScale = 0.6f) + fadeIn(finite(reduced))

@Composable
@ReadOnlyComposable
fun staggerDelayMs(index: Int): Int = when {
    LocalReducedMotion.current -> 0
    index >= Motion.StaggerCap -> Motion.StaggerCap * Motion.StaggerMs
    else -> index * Motion.StaggerMs
}
