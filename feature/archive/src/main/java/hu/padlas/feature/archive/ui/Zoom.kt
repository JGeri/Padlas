package hu.padlas.feature.archive.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade
import hu.padlas.core.design.component.PadlasIconButton
import hu.padlas.core.design.component.glass
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Radius
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** What the viewer shows: the tile's own picture, a name and one line about it. */
@Immutable
internal data class ZoomTarget(
    val key: Any,
    val model: Any?,
    val title: String? = null,
    val caption: String? = null,
    val tag: Any? = null,
)

private data class ZoomKey(val key: Any)

/** Learnt from the tile's own load, so the viewer can open at the right shape at once. */
internal class ZoomMemo(val ratio: Float, val cacheKey: MemoryCache.Key?)

@Stable
internal class ZoomState {
    var target: ZoomTarget? by mutableStateOf(null)
        private set

    private val memos = HashMap<Any, ZoomMemo>()

    fun open(target: ZoomTarget) {
        this.target = target
    }

    fun close() {
        target = null
    }

    fun isOpen(key: Any): Boolean = target?.key == key

    internal fun note(key: Any, ratio: Float, cacheKey: MemoryCache.Key?) {
        memos[key] = ZoomMemo(ratio, cacheKey)
    }

    internal fun memo(key: Any): ZoomMemo? = memos[key]
}

@Composable
internal fun rememberZoomState(): ZoomState = remember { ZoomState() }

@OptIn(ExperimentalSharedTransitionApi::class)
private val LocalZoomScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

private val LocalZoom = staticCompositionLocalOf<ZoomState?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
private val ZoomBounds = BoundsTransform { _, _ -> spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow) }

@OptIn(ExperimentalSharedTransitionApi::class)
private val InstantBounds = BoundsTransform { _, _ -> snap() }

/**
 * Lets any tile below it grow into a full-screen picture and shrink back into its slot.
 * The decision stays in the viewer: [actions] puts the tile's own choice under the picture.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ZoomHost(
    state: ZoomState,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.(ZoomTarget) -> Unit = {},
    content: @Composable () -> Unit,
) {
    SharedTransitionLayout(modifier = modifier) {
        CompositionLocalProvider(LocalZoomScope provides this, LocalZoom provides state) {
            Box(Modifier.fillMaxSize()) {
                content()
                ZoomOverlay(state = state, shared = this@SharedTransitionLayout, actions = actions)
            }
        }
    }
}

/**
 * A grid picture that knows how to fly. While its viewer is open the slot stays empty, so
 * there is only ever one copy of the picture on screen.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ZoomableImage(
    key: Any,
    model: Any?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val shared = LocalZoomScope.current
    val zoom = LocalZoom.current
    if (shared == null || zoom == null) {
        AsyncImage(model = model, contentDescription = contentDescription, contentScale = ContentScale.Crop, modifier = modifier)
        return
    }
    val reduced = LocalReducedMotion.current
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = !zoom.isOpen(key),
            enter = fadeIn(if (reduced) snap() else spring(stiffness = Spring.StiffnessMedium)),
            exit = fadeOut(if (reduced) snap() else spring(stiffness = Spring.StiffnessMedium)),
            modifier = Modifier.matchParentSize(),
        ) {
            with(shared) {
                AsyncImage(
                    model = model,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    onSuccess = { success ->
                        val size = success.painter.intrinsicSize
                        if (size.width > 0f && size.height > 0f) {
                            zoom.note(key, size.width / size.height, success.result.memoryCacheKey)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .sharedElement(
                            sharedContentState = rememberSharedContentState(ZoomKey(key)),
                            animatedVisibilityScope = this@AnimatedVisibility,
                            boundsTransform = if (reduced) InstantBounds else ZoomBounds,
                        )
                        .graphicsLayer {
                            shape = Shapes.small
                            clip = true
                        },
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ZoomOverlay(
    state: ZoomState,
    shared: SharedTransitionScope,
    actions: @Composable ColumnScope.(ZoomTarget) -> Unit,
) {
    val reduced = LocalReducedMotion.current
    BackHandler(enabled = state.target != null) { state.close() }
    AnimatedContent(
        targetState = state.target,
        contentKey = { it?.key },
        transitionSpec = {
            val spec: FiniteAnimationSpec<Float> = if (reduced) snap() else spring(stiffness = Spring.StiffnessMediumLow)
            (fadeIn(spec) togetherWith fadeOut(spec)).using(null)
        },
        label = "zoom",
    ) { target ->
        if (target != null) {
            ZoomViewer(
                target = target,
                state = state,
                shared = shared,
                visibility = this,
                reduced = reduced,
                actions = actions,
            )
        }
    }
}

private class ZoomGeometry {
    var box: IntSize = IntSize.Zero
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ZoomViewer(
    target: ZoomTarget,
    state: ZoomState,
    shared: SharedTransitionScope,
    visibility: AnimatedVisibilityScope,
    reduced: Boolean,
    actions: @Composable ColumnScope.(ZoomTarget) -> Unit,
) {
    val context = LocalContext.current
    val memo = remember(target.key) { state.memo(target.key) }
    val ratio = memo?.ratio ?: 1f
    val request = remember(target.model, memo) {
        ImageRequest.Builder(context)
            .data(target.model)
            .placeholderMemoryCacheKey(memo?.cacheKey)
            .crossfade(true)
            .build()
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var drag by remember { mutableFloatStateOf(0f) }
    val geometry = remember { ZoomGeometry() }
    val scope = rememberCoroutineScope()
    var settle by remember { mutableStateOf<Job?>(null) }
    val dismissAt = with(LocalDensity.current) { DismissDistance.toPx() }
    val corner by visibility.transition.animateDp(label = "zoomCorner") { phase ->
        if (phase == EnterExitState.Visible) 0.dp else Radius.small
    }

    fun clampPan(value: Offset, s: Float, area: IntSize): Offset {
        val maxX = ((geometry.box.width * s - area.width) / 2f).coerceAtLeast(0f)
        val maxY = ((geometry.box.height * s - area.height) / 2f).coerceAtLeast(0f)
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    fun animateTo(toScale: Float, toPan: Offset) {
        settle?.cancel()
        val fromScale = scale
        val fromPan = pan
        settle = scope.launch {
            animate(0f, 1f, animationSpec = if (reduced) snap() else Motion.settle()) { t, _ ->
                scale = fromScale + (toScale - fromScale) * t
                pan = fromPan + (toPan - fromPan) * t
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = 1f - (abs(drag) / (dismissAt * 4f)).coerceIn(0f, 0.55f) }
                .background(Ink.Background.copy(alpha = 0.97f)),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(target.key) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            if (scale > 1.05f) {
                                animateTo(1f, Offset.Zero)
                            } else {
                                val center = Offset(size.width / 2f, size.height / 2f)
                                animateTo(DoubleTapScale, clampPan((center - tap) * (DoubleTapScale - 1f), DoubleTapScale, size))
                            }
                        },
                        onTap = { state.close() },
                    )
                }
                .pointerInput(target.key) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        settle?.cancel()
                        var moved = Offset.Zero
                        var engaged = false
                        var dismissing = false
                        do {
                            val event = awaitPointerEvent()
                            val fingers = event.changes.count { it.pressed }
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (!engaged) {
                                moved += panChange
                                if (fingers > 1 || moved.getDistance() > viewConfiguration.touchSlop) {
                                    engaged = true
                                    dismissing = fingers == 1 && scale <= 1.01f && abs(moved.y) > abs(moved.x)
                                }
                            }
                            if (engaged) {
                                if (dismissing) {
                                    drag += panChange.y
                                } else {
                                    scale = (scale * zoomChange).coerceIn(1f, MaxScale)
                                    pan = clampPan(pan + panChange, scale, size)
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        when {
                            dismissing && abs(drag) > dismissAt -> state.close()
                            dismissing -> {
                                val from = drag
                                settle = scope.launch {
                                    animate(from, 0f, animationSpec = if (reduced) snap() else Motion.snapback()) { v, _ ->
                                        drag = v
                                    }
                                }
                            }
                            scale <= 1.01f && pan != Offset.Zero -> animateTo(1f, Offset.Zero)
                        }
                    }
                }
                .systemBarsPadding()
                .padding(horizontal = Space.s, vertical = ViewerVerticalInset),
            contentAlignment = Alignment.Center,
        ) {
            with(shared) {
                Box(
                    modifier = Modifier
                        .aspectRatio(ratio)
                        .onSizeChanged { geometry.box = it }
                        .graphicsLayer {
                            translationY = drag
                            val shrink = 1f - (abs(drag) / (dismissAt * 6f)).coerceIn(0f, 0.18f)
                            scaleX = shrink
                            scaleY = shrink
                        }
                        .sharedElement(
                            sharedContentState = rememberSharedContentState(ZoomKey(target.key)),
                            animatedVisibilityScope = visibility,
                            boundsTransform = if (reduced) InstantBounds else ZoomBounds,
                        )
                        .graphicsLayer {
                            val radius = corner.toPx()
                            clip = radius > 0f
                            shape = RoundedCornerShape(radius)
                        },
                ) {
                    AsyncImage(
                        model = request,
                        contentDescription = target.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = pan.x
                                translationY = pan.y
                            },
                    )
                }
            }
        }
        PadlasIconButton(
            icon = Icons.Rounded.Close,
            contentDescription = "Bezárás",
            onClick = { state.close() },
            background = Ink.SurfaceSolid.copy(alpha = 0.72f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(Space.m),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = Space.page, vertical = Space.l)
                .widthIn(max = 520.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            if (target.title != null || target.caption != null) {
                Column(
                    modifier = Modifier
                        .glass(Shapes.pill, Ink.SurfaceSolid.copy(alpha = 0.78f), Ink.HairlineStrong)
                        .padding(horizontal = Space.l, vertical = Space.s),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    target.title?.let {
                        Text(
                            text = it,
                            style = PadlasTheme.type.label,
                            color = Ink.Text,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                    target.caption?.let {
                        Text(
                            text = it,
                            style = PadlasTheme.type.caption,
                            color = Ink.TextSecondary,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            actions(target)
        }
    }
}

private const val DoubleTapScale = 2.5f
private const val MaxScale = 5f
private val DismissDistance = 140.dp
private val ViewerVerticalInset = 72.dp
