package hu.padlas.feature.map

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Spectrum

/**
 * The rectangles are not cross-faded into the next level: the tapped one becomes the whole
 * card and its children are divided out of its area. Keeping the origin rectangle around is
 * what makes the drill read as one continuous surface rather than two screens.
 */
@Composable
internal fun Treemap(
    node: MapNode,
    onDrill: (MapNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val gapPx = with(density) { 2.dp.toPx() }
        val labelFloorPx = with(density) { 64.dp.toPx() }

        val measurer = rememberTextMeasurer(cacheSize = 64)
        val labelStyle = PadlasTheme.type.label
        val numberStyle = PadlasTheme.type.numeric
        val reduced = LocalReducedMotion.current
        val settleSpec = motionSpec(Motion.settle<Float>())

        var origin by remember { mutableStateOf<MapRect?>(null) }
        val entrance = remember { Animatable(0f) }

        val full = remember(widthPx, heightPx) { MapRect.ofSize(widthPx, heightPx) }
        val children = node.children
        val target = remember(node.id, widthPx, heightPx, children.size) {
            squarify(children.map { SquarifyItem(it.id, it.sizeBytes) }, full)
        }
        val start = remember(node.id, origin, target) {
            val from = origin ?: full.centred(0.7f)
            squarify(children.map { SquarifyItem(it.id, it.sizeBytes) }, from)
        }

        LaunchedEffect(node.id, widthPx, heightPx) {
            entrance.snapTo(0f)
            entrance.animateTo(1f, settleSpec)
        }

        val progress = if (reduced) 1f else entrance.value
        val byId = remember(children) { children.associateBy { it.id } }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(node.id, target) {
                    detectTapGestures { offset ->
                        val hit = target.firstOrNull { it.rect.contains(offset.x, offset.y) } ?: return@detectTapGestures
                        origin = hit.rect
                        byId[hit.id]?.let(onDrill)
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                target.forEachIndexed { index, tile ->
                    val tileProgress = staggered(progress, index)
                    if (tileProgress <= 0f) return@forEachIndexed
                    val from = start.firstOrNull { it.id == tile.id }?.rect ?: tile.rect
                    val rect = from.lerpTo(tile.rect, tileProgress)
                    val child = byId[tile.id] ?: return@forEachIndexed
                    drawTile(
                        rect = rect,
                        gap = gapPx,
                        color = Spectrum[child.colorIndex % Spectrum.size],
                        alpha = tileProgress,
                    )
                    if (rect.width > labelFloorPx && rect.height > labelFloorPx) {
                        drawLabel(measurer, child, rect, gapPx, labelStyle, numberStyle, tileProgress)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawTile(rect: MapRect, gap: Float, color: Color, alpha: Float) {
    val left = rect.left + gap / 2f
    val top = rect.top + gap / 2f
    val width = (rect.width - gap).coerceAtLeast(0f)
    val height = (rect.height - gap).coerceAtLeast(0f)
    if (width <= 0f || height <= 0f) return
    drawRect(
        color = color.copy(alpha = 0.85f * alpha),
        topLeft = Offset(left, top),
        size = Size(width, height),
    )
    drawRect(
        color = Ink.Void.copy(alpha = 0.35f * alpha),
        topLeft = Offset(left, top),
        size = Size(width, height),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
    )
}

private fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    node: MapNode,
    rect: MapRect,
    gap: Float,
    labelStyle: TextStyle,
    numberStyle: TextStyle,
    alpha: Float,
) {
    val padding = gap * 3f
    val name = measurer.measure(
        text = node.label,
        style = labelStyle.copy(color = Ink.Void.copy(alpha = 0.9f * alpha)),
        maxLines = 1,
    )
    val size = measurer.measure(
        text = Format.bytes(node.sizeBytes),
        style = numberStyle.copy(color = Ink.Void.copy(alpha = 0.7f * alpha)),
        maxLines = 1,
    )
    if (name.size.width > rect.width - padding * 2) return
    drawText(name, topLeft = Offset(rect.left + padding, rect.top + padding))
    drawText(size, topLeft = Offset(rect.left + padding, rect.top + padding + name.size.height))
}

/** Twenty-eight milliseconds between siblings, and after twelve of them the rest arrives together. */
private fun staggered(progress: Float, index: Int): Float {
    val delay = (minOf(index, Motion.StaggerCap) * Motion.StaggerMs).toFloat() / STAGGER_WINDOW_MS
    if (delay >= 1f) return progress
    return ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)
}

private fun MapRect.centred(fraction: Float): MapRect {
    val w = width * fraction / 2f
    val h = height * fraction / 2f
    return MapRect(centerX - w, centerY - h, centerX + w, centerY + h)
}

private const val STAGGER_WINDOW_MS = 700f
