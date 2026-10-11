package hu.padlas.core.design.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect

/**
 * The navigation host provides both scopes; any module can then morph one of its elements
 * into an element of another screen just by agreeing on a key. Outside a transition, or in
 * previews, the modifiers below are no-ops.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** A soft, slightly underdamped spring: the morph settles instead of stopping dead. */
@OptIn(ExperimentalSharedTransitionApi::class)
val MorphBounds = BoundsTransform { _: Rect, _: Rect ->
    spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
}

/** Container transform: the element grows into (or shrinks back from) its counterpart. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.morphBounds(key: Any): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(shared) {
        this@morphBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)),
            boundsTransform = MorphBounds,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}

/** The same picture in two places: it flies from one to the other. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.morphElement(key: Any): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(shared) {
        this@morphElement.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            boundsTransform = MorphBounds,
        )
    }
}
