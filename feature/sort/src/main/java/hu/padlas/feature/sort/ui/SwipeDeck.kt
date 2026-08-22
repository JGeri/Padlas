package hu.padlas.feature.sort.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.haptics
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Space
import hu.padlas.feature.sort.SortCard
import hu.padlas.feature.sort.SwipeDirection
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CardWidthFraction = 0.86f
private const val CardAspect = 0.72f
private val DepthStep = 12.dp

/** The card that has already been decided and is on its way out of the deck. */
private data class Flight(
    val card: SortCard,
    val direction: SwipeDirection,
    val releaseX: Float,
    val releaseY: Float,
)

/**
 * At most three cards exist at any moment: the one under the finger, the one growing
 * behind it, and a hint of thickness under that. Everything that moves per frame is read
 * inside a graphicsLayer block, so a 200 card run costs no recomposition at all.
 */
@Composable
internal fun SwipeDeck(
    cards: List<SortCard>,
    undoTicket: Long,
    undoneDirection: SwipeDirection?,
    onSwipe: (SortCard, SwipeDirection) -> Unit,
    onProtect: (SortCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = haptics()
    val scope = rememberCoroutineScope()
    val snapSpec = motionSpec(Motion.snapSpring<Float>())
    val weightSpec = motionSpec(Motion.weight<Float>())
    val snapbackSpec = motionSpec(Motion.snapback<Float>())

    val dragX = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    val lid = remember { Animatable(0f) }
    val restore = remember { Animatable(0f) }
    val flightProgress = remember { Animatable(0f) }
    var flight by remember { mutableStateOf<Flight?>(null) }
    var crossed by remember { mutableStateOf<SwipeDirection?>(null) }
    var deckSize by remember { mutableStateOf(IntSize.Zero) }

    val thresholdPx = deckSize.width * Deck.ThresholdFraction
    val deckWidth = deckSize.width.toFloat()
    val deckHeight = deckSize.height.toFloat()
    val stack = cards.take(3)
    val top = stack.firstOrNull()

    // Undo replays the commit backwards: the card climbs out of the tray on a snapback.
    LaunchedEffect(undoTicket) {
        if (undoTicket == 0L || undoneDirection == null) return@LaunchedEffect
        restore.snapTo(1f)
        haptics?.undo()
        if (undoneDirection == SwipeDirection.LEFT) {
            launch {
                lid.animateTo(1f, snapSpec)
                lid.animateTo(0f, weightSpec)
            }
        }
        restore.animateTo(0f, snapbackSpec)
    }

    suspend fun settleBack() {
        crossed = null
        coroutineScope {
            launch { dragX.animateTo(0f, snapbackSpec) }
            launch { dragY.animateTo(0f, snapbackSpec) }
        }
    }

    suspend fun commit(card: SortCard, direction: SwipeDirection) {
        flightProgress.snapTo(0f)
        flight = Flight(card, direction, dragX.value, dragY.value)
        dragX.snapTo(0f)
        dragY.snapTo(0f)
        crossed = null
        // The deck advances underneath immediately; only the decided card is still flying.
        onSwipe(card, direction)
        when (direction) {
            SwipeDirection.LEFT -> {
                scope.launch { lid.animateTo(1f, snapSpec) }
                delay(Deck.AnticipationMs)
                flightProgress.animateTo(1f, weightSpec)
                haptics?.binImpact()
                lid.animateTo(0f, weightSpec)
            }

            SwipeDirection.UP -> {
                haptics?.lift()
                flightProgress.animateTo(1f, weightSpec)
            }

            else -> {
                haptics?.tick(0.25f)
                flightProgress.animateTo(1f, weightSpec)
            }
        }
        flight = null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { deckSize = it },
    ) {
        AtticHatch(
            active = crossed == SwipeDirection.UP,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        KeepGlyph(
            active = crossed == SwipeDirection.RIGHT,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = Space.xs),
        )
        LaterGlyph(
            active = crossed == SwipeDirection.DOWN,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Space.xs, bottom = Space.xl),
        )
        BinTray(
            active = crossed == SwipeDirection.LEFT,
            lid = { lid.value },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        for (depth in stack.indices.reversed()) {
            val card = stack[depth]
            key(card.id) {
                if (depth == 0) {
                    TopCard(
                        card = card,
                        gesture = Modifier
                            .pointerInput(card.id) {
                                detectTapGestures(onLongPress = { onProtect(card) })
                            }
                            .pointerInput(card.id, thresholdPx) {
                                var accumX = 0f
                                var accumY = 0f
                                detectDragGestures(
                                    onDragStart = {
                                        accumX = 0f
                                        accumY = 0f
                                    },
                                    onDragEnd = {
                                        val direction = Deck.committedDirection(accumX, accumY, thresholdPx)
                                        scope.launch {
                                            if (direction == null) settleBack() else commit(card, direction)
                                        }
                                    },
                                    onDragCancel = { scope.launch { settleBack() } },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        accumX += delta.x
                                        accumY += delta.y
                                        scope.launch {
                                            dragX.snapTo(accumX)
                                            dragY.snapTo(accumY)
                                        }
                                        val direction = Deck.committedDirection(accumX, accumY, thresholdPx)
                                        if (direction != crossed) {
                                            crossed = direction
                                            if (direction != null) haptics?.threshold()
                                        }
                                    },
                                )
                            },
                        transform = {
                            val climbing = restore.value
                            if (climbing > 0f && undoneDirection != null) {
                                transformOrigin = TransformOrigin.Center
                                applyFlight(undoneDirection, climbing, 0f, 0f, deckWidth, deckHeight)
                            } else {
                                transformOrigin = TransformOrigin(0.5f, Deck.PivotDepth)
                                applyDrag(dragX.value, dragY.value, thresholdPx)
                            }
                        },
                    )
                } else {
                    BehindCard(
                        card = card,
                        depth = depth,
                        progress = { Deck.dragProgress(dragX.value, dragY.value, thresholdPx) },
                    )
                }
            }
        }

        flight?.let { inFlight ->
            SortCardFace(
                card = inFlight.card,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(CardWidthFraction)
                    .aspectRatio(CardAspect)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin.Center
                        applyFlight(
                            direction = inFlight.direction,
                            progress = flightProgress.value,
                            releaseX = inFlight.releaseX,
                            releaseY = inFlight.releaseY,
                            deckWidth = deckWidth,
                            deckHeight = deckHeight,
                        )
                    },
                wireframe = inFlight.direction == SwipeDirection.UP,
                colourAlpha = { Deck.colourAlpha(inFlight.direction, flightProgress.value) },
                outlineAlpha = { Deck.outlineAlpha(inFlight.direction, flightProgress.value) },
            )
        }
    }
}

@Composable
private fun BoxScope.TopCard(
    card: SortCard,
    gesture: Modifier,
    transform: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit,
) {
    SortCardFace(
        card = card,
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth(CardWidthFraction)
            .aspectRatio(CardAspect)
            .graphicsLayer(transform)
            .then(gesture),
    )
}

@Composable
private fun BoxScope.BehindCard(card: SortCard, depth: Int, progress: () -> Float) {
    val offset by animateFloatAsState(
        targetValue = depth.toFloat(),
        animationSpec = motionSpec(Motion.settle<Float>()),
        label = "deckDepth",
    )
    SortCardFace(
        card = card,
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth(CardWidthFraction)
            .aspectRatio(CardAspect)
            .graphicsLayer {
                // Only the card directly behind grows with the drag; the third is thickness.
                val grown = if (depth == 1) {
                    Deck.BehindScale + (1f - Deck.BehindScale) * progress()
                } else {
                    Deck.BehindScale
                }
                scaleX = grown
                scaleY = grown
                translationY = offset * DepthStep.toPx()
            },
    )
}
