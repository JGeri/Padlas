package hu.padlas.feature.sort.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.haptics
import hu.padlas.feature.sort.SwipeAction
import hu.padlas.feature.sort.SwipeCard
import hu.padlas.feature.sort.session.UndoEvent
import kotlin.math.abs
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** How far each fate is from the resting card, 0 at rest and 1 at the commit line. */
@Immutable
internal data class SwipeOverlay(val delete: Float = 0f, val keep: Float = 0f, val favorite: Float = 0f) {
    companion object {
        val None = SwipeOverlay()

        fun full(action: SwipeAction) = when (action) {
            SwipeAction.DELETE -> SwipeOverlay(delete = 1f)
            SwipeAction.KEEP -> SwipeOverlay(keep = 1f)
            SwipeAction.FAVORITE -> SwipeOverlay(favorite = 1f)
        }
    }
}

/** Lets the buttons under the deck throw the top card exactly as a finger would. */
@Stable
internal class SwipeDeckState {
    internal val commands = Channel<SwipeAction>(Channel.CONFLATED)

    fun swipe(action: SwipeAction) {
        commands.trySend(action)
    }
}

@Composable
internal fun rememberSwipeDeckState(): SwipeDeckState = remember { SwipeDeckState() }

private data class Flight(val key: Long, val card: SwipeCard, val action: SwipeAction, val start: Offset, val velocity: Velocity)

private const val HorizontalCommit = 0.26f
private const val VerticalCommit = 0.16f
private const val FlingVelocity = 1_600f
private const val MaxTilt = 14f

internal fun decide(offset: Offset, velocity: Velocity, width: Float, height: Float): SwipeAction? {
    if (width <= 0f || height <= 0f) return null
    val upward = offset.y < 0f && -offset.y > abs(offset.x)
    if (upward && (-offset.y > height * VerticalCommit || velocity.y < -FlingVelocity)) return SwipeAction.FAVORITE
    if (offset.x > width * HorizontalCommit || (velocity.x > FlingVelocity && offset.x > 0f)) return SwipeAction.KEEP
    if (offset.x < -width * HorizontalCommit || (velocity.x < -FlingVelocity && offset.x < 0f)) return SwipeAction.DELETE
    return null
}

/** Drops whatever part of the offset has crossed the centre since [from]. */
private fun Offset.towards(from: Offset?): Offset = if (from == null) {
    this
} else {
    Offset(if (x * from.x < 0f) 0f else x, if (y * from.y < 0f) 0f else y)
}

internal fun overlayFor(offset: Offset, width: Float, height: Float): SwipeOverlay {
    if (width <= 0f || height <= 0f) return SwipeOverlay.None
    val upward = offset.y < 0f && -offset.y > abs(offset.x)
    return if (upward) {
        SwipeOverlay(favorite = (-offset.y / (height * VerticalCommit)).coerceIn(0f, 1f))
    } else {
        val fraction = (offset.x / (width * HorizontalCommit)).coerceIn(-1f, 1f)
        SwipeOverlay(delete = (-fraction).coerceAtLeast(0f), keep = fraction.coerceAtLeast(0f))
    }
}

/**
 * The card under the finger, the two behind it, and any card still flying out. A decided
 * card leaves as its own "flight", so the next one is usable immediately and fast swipes
 * never wait for an animation to finish.
 */
@Composable
internal fun SwipeDeck(
    cards: List<SwipeCard>,
    state: SwipeDeckState,
    undo: UndoEvent?,
    onSwiped: (SwipeCard, SwipeAction) -> Unit,
    onTap: (SwipeCard) -> Unit,
    modifier: Modifier = Modifier,
    dealIn: Boolean = true,
    cardContent: @Composable (card: SwipeCard, isTop: Boolean, overlay: () -> SwipeOverlay, modifier: Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val depthStep = with(density) { 14.dp.toPx() }
        val haptics = haptics()
        val reduced = LocalReducedMotion.current
        val scope = rememberCoroutineScope()
        val flights = remember { mutableStateListOf<Flight>() }
        var flightSeq by remember { mutableLongStateOf(0L) }
        var handledUndo by remember { mutableLongStateOf(0L) }
        val swiped = rememberUpdatedState(onSwiped)

        val top = cards.firstOrNull()

        // The first cards are dealt onto the table one after another, from below.
        val deal = remember { Animatable(if (dealIn && !reduced) 0f else 1f) }
        LaunchedEffect(Unit) {
            if (deal.value < 1f) deal.animateTo(1f, spring(0.8f, Spring.StiffnessVeryLow * 2.5f))
        }
        fun dealt(depth: Int): Float = ((deal.value - depth * 0.14f) / 0.72f).coerceIn(0f, 1f)

        fun offscreen(action: SwipeAction): Offset = when (action) {
            SwipeAction.DELETE -> Offset(-width * 1.4f, height * 0.05f)
            SwipeAction.KEEP -> Offset(width * 1.4f, height * 0.05f)
            SwipeAction.FAVORITE -> Offset(0f, -height * 1.2f)
        }

        fun commit(card: SwipeCard, action: SwipeAction, start: Offset, velocity: Velocity) {
            flights += Flight(++flightSeq, card, action, start, velocity)
            when (action) {
                SwipeAction.DELETE -> haptics?.binImpact()
                SwipeAction.KEEP -> haptics?.keep()
                SwipeAction.FAVORITE -> haptics?.favorite()
            }
            swiped.value(card, action)
        }

        val restoring = undo != null && undo.ticket != handledUndo && undo.cardId == top?.id
        val offset = remember(top?.id) {
            Animatable(if (restoring && undo != null) offscreen(undo.action) else Offset.Zero, Offset.VectorConverter)
        }

        // A card springing back to the centre overshoots it a little; the overshoot must not
        // light up the opposite fate, so while it settles only the side it came from counts.
        var settlingFrom by remember(top?.id) { mutableStateOf<Offset?>(null) }
        suspend fun settle(spec: AnimationSpec<Offset>) {
            settlingFrom = offset.value
            try {
                offset.animateTo(Offset.Zero, spec)
            } finally {
                settlingFrom = null
            }
        }

        LaunchedEffect(undo?.ticket, top?.id) {
            val event = undo ?: return@LaunchedEffect
            if (event.ticket == handledUndo || event.cardId != top?.id) return@LaunchedEffect
            handledUndo = event.ticket
            haptics?.undo()
            if (reduced) offset.snapTo(Offset.Zero) else settle(spring(0.75f, Spring.StiffnessMediumLow))
        }

        // One button press throws exactly one card: the effect restarts for the next top.
        LaunchedEffect(top?.id) {
            val card = top ?: return@LaunchedEffect
            val action = state.commands.receive()
            commit(card, action, offset.value, Velocity.Zero)
        }

        for (depth in cards.indices.take(3).reversed()) {
            val card = cards[depth]
            key(card.id) {
                val baseScale by animateFloatAsState(
                    targetValue = 1f - 0.06f * depth,
                    animationSpec = spring(0.8f, Spring.StiffnessMediumLow),
                    label = "deckScale",
                )
                val baseLift by animateFloatAsState(
                    targetValue = depth.toFloat(),
                    animationSpec = spring(0.8f, Spring.StiffnessMediumLow),
                    label = "deckLift",
                )
                if (depth == 0) {
                    val gesture = Modifier
                        .pointerInput(card.id) {
                            detectTapGestures(onTap = { onTap(card) })
                        }
                        .pointerInput(card.id, width, height) {
                            val tracker = VelocityTracker()
                            var drag = Offset.Zero
                            var crossed: SwipeAction? = null
                            detectDragGestures(
                                onDragStart = {
                                    tracker.resetTracking()
                                    drag = offset.value
                                    scope.launch { offset.stop() }
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    drag += amount
                                    tracker.addPosition(change.uptimeMillis, drag)
                                    scope.launch { offset.snapTo(drag) }
                                    val now = decide(drag, Velocity.Zero, width, height)
                                    if (now != crossed) {
                                        crossed = now
                                        if (now != null) haptics?.threshold()
                                    }
                                },
                                onDragEnd = {
                                    val velocity = tracker.calculateVelocity()
                                    val action = decide(drag, velocity, width, height)
                                    crossed = null
                                    if (action != null) {
                                        commit(card, action, drag, velocity)
                                    } else {
                                        scope.launch { settle(spring(0.6f, Spring.StiffnessMedium)) }
                                    }
                                },
                                onDragCancel = {
                                    crossed = null
                                    scope.launch { settle(spring(0.6f, Spring.StiffnessMedium)) }
                                },
                            )
                        }
                    cardContent(
                        card,
                        true,
                        { overlayFor(offset.value.towards(settlingFrom), width, height) },
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val position = offset.value
                                val arrive = dealt(0)
                                translationX = position.x
                                translationY = position.y + (1f - arrive) * height * 0.7f
                                rotationZ = (position.x / width * MaxTilt).coerceIn(-MaxTilt * 1.5f, MaxTilt * 1.5f) + (1f - arrive) * 8f
                                alpha = arrive
                                transformOrigin = TransformOrigin(0.5f, 0.9f)
                            }
                            .then(gesture),
                    )
                } else {
                    cardContent(
                        card,
                        false,
                        { SwipeOverlay.None },
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val arrive = dealt(depth)
                                scaleX = baseScale
                                scaleY = baseScale
                                translationY = baseLift * depthStep + (1f - arrive) * height * 0.7f
                                rotationZ = (1f - arrive) * (if (depth == 1) -6f else 5f)
                                alpha = (if (depth >= 2) 0.55f else 1f) * arrive
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            },
                    )
                }
            }
        }

        flights.forEach { flight ->
            key("flight-${flight.key}") {
                val travel = remember { Animatable(flight.start, Offset.VectorConverter) }
                LaunchedEffect(Unit) {
                    val target = offscreen(flight.action).let {
                        if (flight.action == SwipeAction.FAVORITE) it.copy(x = flight.start.x) else it.copy(y = flight.start.y + flight.velocity.y * 0.12f)
                    }
                    if (reduced) {
                        travel.snapTo(target)
                    } else {
                        travel.animateTo(
                            targetValue = target,
                            animationSpec = spring(1f, Spring.StiffnessLow, visibilityThreshold = Offset(2f, 2f)),
                            initialVelocity = Offset(flight.velocity.x, flight.velocity.y),
                        )
                    }
                    flights.remove(flight)
                }
                cardContent(
                    flight.card,
                    false,
                    { SwipeOverlay.full(flight.action) },
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val position = travel.value
                            translationX = position.x
                            translationY = position.y
                            rotationZ = (position.x / width * MaxTilt).coerceIn(-MaxTilt * 1.5f, MaxTilt * 1.5f)
                            transformOrigin = TransformOrigin(0.5f, 0.9f)
                        },
                )
            }
        }

        if (cards.isEmpty() && flights.isEmpty()) Box(Modifier.fillMaxSize())
    }
}
