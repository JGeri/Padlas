package hu.padlas.feature.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.haptics
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface MapMotion {
    data object Enter : MapMotion
    data object Morph : MapMotion
    data object Fade : MapMotion

    /** [focus] is in the previous level's coordinates: the patch that becomes the screen. */
    data class ZoomIn(val focus: MapRect) : MapMotion

    /** [focus] is in the new level's coordinates: the patch the old level shrinks back into. */
    data class ZoomOut(val focus: MapRect) : MapMotion
}

private class TileLabel(val name: TextLayoutResult?, val size: TextLayoutResult?, val framed: Boolean)

private class LabelStyles(val name: TextStyle, val size: TextStyle, val header: TextStyle, val headerSize: TextStyle)

/**
 * The squarified map of one folder. Levels never cut to each other: drilling in zooms the
 * camera onto the tapped folder until its own children, already sketched inside it, fill
 * the card; going up zooms back out. That continuity is what makes the map feel like one
 * surface rather than a stack of screens.
 */
@Composable
internal fun Treemap(
    directory: DirectoryUi,
    selectedKey: String?,
    onTap: (MapTile) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val density = LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val metrics = remember(density) { treemapMetrics(density) }

        val target by produceState<TreemapLayout?>(null, directory, width, height, metrics) {
            if (width <= 0f || height <= 0f) return@produceState
            value = withContext(Dispatchers.Default) {
                layoutTreemap(directory, directory.plan, width, height, metrics)
            }
        }

        var shown by remember { mutableStateOf<TreemapLayout?>(null) }
        var previous by remember { mutableStateOf<TreemapLayout?>(null) }
        var motion by remember { mutableStateOf<MapMotion>(MapMotion.Enter) }
        val progress = remember { Animatable(1f) }
        val reduced = LocalReducedMotion.current

        LaunchedEffect(target) {
            val next = target ?: return@LaunchedEffect
            val old = shown
            if (old === next) return@LaunchedEffect
            motion = motionBetween(old, next)
            previous = old
            shown = next
            if (reduced) {
                progress.snapTo(1f)
            } else {
                progress.snapTo(0f)
                // Drilling is a camera move with mass: it runs a touch past the folder and
                // settles back, which is what tells the eye the map is one surface.
                progress.animateTo(1f, if (motion == MapMotion.Morph) Motion.settle() else Motion.weight())
            }
            previous = null
        }

        val measurer = rememberTextMeasurer(cacheSize = 0)
        val styles = labelStyles()
        val currentLabels = remember(shown, styles) { shown?.let { measureLabels(measurer, it, styles, metrics, density) }.orEmpty() }
        val previousLabels = remember(previous, styles) { previous?.let { measureLabels(measurer, it, styles, metrics, density) }.orEmpty() }

        var outlined by remember { mutableStateOf<String?>(null) }
        SideEffect { if (selectedKey != null) outlined = selectedKey }
        val selection by animateFloatAsState(
            targetValue = if (selectedKey != null) 1f else 0f,
            animationSpec = motionSpec(Motion.settle()),
            label = "mapSelection",
        )

        val brushes = remember { CushionBrushes() }
        val tap by rememberUpdatedState(onTap)
        val haptic = haptics()
        val radius = with(density) { 5.dp.toPx() }
        val nestedRadius = with(density) { 2.5.dp.toPx() }
        val outlineWidth = with(density) { 2.dp.toPx() }
        val chrome = TileChrome(radius, nestedRadius, metrics)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Tárhelytérkép: ${directory.label}" }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val hit = shown?.hit(offset.x, offset.y) ?: return@detectTapGestures
                        haptic?.tick(0.25f)
                        tap(hit)
                    }
                },
        ) {
            val current = shown ?: return@Canvas
            val p = progress.value
            val full = MapRect.ofSize(size.width, size.height)
            val identity: (MapRect) -> MapRect = { it }

            when (val m = motion) {
                MapMotion.Enter -> current.tiles.forEachIndexed { index, tile ->
                    val t = staggered(p, index)
                    if (t > 0f) {
                        val grown = tile.rect.scaledAroundCenter(0.82f + 0.18f * t)
                        val transform: (MapRect) -> MapRect = { it.mapBetween(tile.rect, grown) }
                        drawTile(tile, transform, t, currentLabels[tile.key], t, brushes, chrome)
                    }
                }
                MapMotion.Morph -> {
                    val old = previous
                    old?.tiles?.forEach { gone ->
                        if (current.tile(gone.key) == null) {
                            val shrunk = gone.rect.scaledAroundCenter(1f - 0.4f * p.coerceIn(0f, 1f))
                            drawTile(gone, { it.mapBetween(gone.rect, shrunk) }, 1f - p.coerceIn(0f, 1f), null, 0f, brushes, chrome)
                        }
                    }
                    current.tiles.forEach { tile ->
                        val from = old?.tile(tile.key)?.rect
                        val rect = (from ?: tile.rect.scaledAroundCenter(0.6f)).lerpTo(tile.rect, p)
                        val alpha = if (from == null) p.coerceIn(0f, 1f) else 1f
                        drawTile(tile, { it.mapBetween(tile.rect, rect) }, alpha, currentLabels[tile.key], fadeIn(p), brushes, chrome)
                    }
                }
                MapMotion.Fade -> {
                    previous?.let { drawLevel(it, identity, 1f - p.coerceIn(0f, 1f), previousLabels, 0f, full, brushes, chrome) }
                    drawLevel(current, identity, p.coerceIn(0f, 1f), currentLabels, fadeIn(p), full, brushes, chrome)
                }
                is MapMotion.ZoomIn -> {
                    val camera = full.lerpTo(m.focus, p)
                    previous?.let { old ->
                        drawLevel(old, { it.mapBetween(camera, full) }, 1f, previousLabels, fadeOut(p), full, brushes, chrome)
                    }
                    drawLevel(
                        layout = current,
                        transform = { it.mapBetween(full, m.focus).mapBetween(camera, full) },
                        alpha = (p * 1.8f).coerceIn(0f, 1f),
                        labels = currentLabels,
                        labelAlpha = fadeIn(p),
                        full = full,
                        brushes = brushes,
                        chrome = chrome,
                    )
                }
                is MapMotion.ZoomOut -> {
                    val camera = m.focus.lerpTo(full, p)
                    drawLevel(current, { it.mapBetween(camera, full) }, 1f, currentLabels, fadeIn(p), full, brushes, chrome)
                    previous?.let { old ->
                        drawLevel(
                            layout = old,
                            transform = { it.mapBetween(full, m.focus).mapBetween(camera, full) },
                            alpha = (1f - p * 1.8f).coerceIn(0f, 1f),
                            labels = previousLabels,
                            labelAlpha = fadeOut(p),
                            full = full,
                            brushes = brushes,
                            chrome = chrome,
                        )
                    }
                }
            }

            val key = outlined
            val picked = key?.let(current::tile)
            if (selection > 0.01f && picked != null && p >= SettledProgress) {
                drawRect(Ink.Background.copy(alpha = 0.52f * selection))
                drawTile(picked, identity, 1f, currentLabels[picked.key], 1f, brushes, chrome)
                val r = picked.rect
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.16f * selection),
                    topLeft = Offset(r.left - outlineWidth, r.top - outlineWidth),
                    size = Size(r.width + outlineWidth * 2f, r.height + outlineWidth * 2f),
                    cornerRadius = CornerRadius(radius + outlineWidth),
                    style = Stroke(outlineWidth * 2f),
                )
                drawRoundRect(
                    color = Ink.Text.copy(alpha = selection),
                    topLeft = Offset(r.left, r.top),
                    size = Size(r.width, r.height),
                    cornerRadius = CornerRadius(radius),
                    style = Stroke(outlineWidth),
                )
            }
        }
    }
}

private fun motionBetween(old: TreemapLayout?, next: TreemapLayout): MapMotion {
    if (old == null) return MapMotion.Enter
    if (old.path == next.path) return MapMotion.Morph
    if (next.path.startsWith(old.path + "/")) {
        return old.tileLeadingTo(next.path)?.let { MapMotion.ZoomIn(it.focus) } ?: MapMotion.Fade
    }
    if (old.path.startsWith(next.path + "/")) {
        return next.tileLeadingTo(old.path)?.let { MapMotion.ZoomOut(it.focus) } ?: MapMotion.Fade
    }
    return MapMotion.Fade
}

private fun treemapMetrics(density: Density): TreemapMetrics = with(density) {
    TreemapMetrics(
        gap = 2.dp.toPx(),
        header = 22.dp.toPx(),
        padding = 3.dp.toPx(),
        nestedGap = 1.5.dp.toPx(),
        nestMinWidth = 84.dp.toPx(),
        nestMinHeight = 72.dp.toPx(),
    )
}

@Composable
private fun labelStyles(): LabelStyles {
    val type = PadlasTheme.type
    return remember(type) {
        LabelStyles(
            name = type.caption.copy(fontWeight = FontWeight.SemiBold, color = TileInk),
            size = type.micro.copy(color = TileInk.copy(alpha = 0.72f), fontFeatureSettings = "tnum"),
            header = type.caption.copy(fontWeight = FontWeight.SemiBold, color = Ink.Text),
            headerSize = type.micro.copy(color = Ink.TextSecondary, fontFeatureSettings = "tnum"),
        )
    }
}

/** Measured once per level against the final rectangles, never per animation frame. */
private fun measureLabels(
    measurer: TextMeasurer,
    layout: TreemapLayout,
    styles: LabelStyles,
    metrics: TreemapMetrics,
    density: Density,
): Map<String, TileLabel> {
    val pad = with(density) { 6.dp.toPx() }
    val minWidth = with(density) { 44.dp.toPx() }
    val minHeight = with(density) { 30.dp.toPx() }
    val sizeFloor = with(density) { 46.dp.toPx() }
    val out = HashMap<String, TileLabel>()
    for (tile in layout.tiles) {
        val rect = tile.rect
        val name = tile.item?.name ?: "${Format.count(tile.restCount)} kisebb elem"
        val bytes = Format.bytes(tile.bytes)
        if (tile.inner != null) {
            val available = (rect.width - pad * 2f).toInt()
            if (available <= 0) continue
            val size = measurer.measure(bytes, styles.headerSize, maxLines = 1, constraints = Constraints(maxWidth = available))
            val nameWidth = available - size.size.width - pad.toInt()
            val title = if (nameWidth > minWidth / 2f) {
                measurer.measure(
                    name,
                    styles.header,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    softWrap = false,
                    constraints = Constraints(maxWidth = nameWidth),
                )
            } else {
                null
            }
            out[tile.key] = TileLabel(title, size, framed = true)
        } else if (rect.width >= minWidth && rect.height >= minHeight) {
            val available = (rect.width - pad * 2f).toInt()
            val nameStyle = if (tile.isRest) styles.header.copy(color = Ink.TextSecondary) else styles.name
            val sizeStyle = if (tile.isRest) styles.headerSize else styles.size
            val title = measurer.measure(
                name,
                nameStyle,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                softWrap = false,
                constraints = Constraints(maxWidth = available),
            )
            val size = if (rect.height >= sizeFloor) {
                measurer.measure(bytes, sizeStyle, maxLines = 1, constraints = Constraints(maxWidth = available))
            } else {
                null
            }
            out[tile.key] = TileLabel(title, size, framed = false)
        }
    }
    return out
}

private class TileChrome(val radius: Float, val nestedRadius: Float, val metrics: TreemapMetrics)

private fun DrawScope.drawLevel(
    layout: TreemapLayout,
    transform: (MapRect) -> MapRect,
    alpha: Float,
    labels: Map<String, TileLabel>,
    labelAlpha: Float,
    full: MapRect,
    brushes: CushionBrushes,
    chrome: TileChrome,
) {
    if (alpha <= 0.01f) return
    for (tile in layout.tiles) {
        val rect = transform(tile.rect)
        if (rect.right < full.left || rect.left > full.right || rect.bottom < full.top || rect.top > full.bottom) continue
        drawTile(tile, transform, alpha, labels[tile.key], labelAlpha, brushes, chrome)
    }
}

private fun DrawScope.drawTile(
    tile: MapTile,
    transform: (MapRect) -> MapRect,
    alpha: Float,
    label: TileLabel?,
    labelAlpha: Float,
    brushes: CushionBrushes,
    chrome: TileChrome,
) {
    val rect = transform(tile.rect)
    if (rect.width < 0.5f || rect.height < 0.5f) return
    val color = tile.category.color
    when {
        tile.isRest -> {
            drawRoundRect(
                color = Ink.SurfaceHighest,
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                cornerRadius = CornerRadius(min(chrome.radius, min(rect.width, rect.height) / 2f)),
                alpha = alpha,
            )
            cushion(rect, Color.Transparent, chrome.radius, alpha * 0.6f, brushes)
        }
        tile.inner != null -> {
            drawRoundRect(
                color = lerp(Ink.SurfaceHigh, color, 0.18f),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                cornerRadius = CornerRadius(min(chrome.radius, min(rect.width, rect.height) / 2f)),
                alpha = alpha,
            )
            drawRoundRect(
                color = color.copy(alpha = 0.22f),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                cornerRadius = CornerRadius(min(chrome.radius, min(rect.width, rect.height) / 2f)),
                alpha = alpha,
                style = Stroke(1f),
            )
            for (child in tile.nested) {
                val inner = transform(child.rect)
                if (child.isRest) {
                    drawRect(
                        color = Ink.SurfaceHighest,
                        topLeft = Offset(inner.left, inner.top),
                        size = Size(max(inner.width, 0f), max(inner.height, 0f)),
                        alpha = alpha,
                    )
                } else {
                    cushion(inner, child.category.color, chrome.nestedRadius, alpha, brushes)
                }
            }
        }
        else -> cushion(rect, color, chrome.radius, alpha, brushes)
    }
    if (label != null && labelAlpha > 0.01f) drawLabel(rect, label, labelAlpha * alpha, chrome)
}

private fun DrawScope.drawLabel(rect: MapRect, label: TileLabel, alpha: Float, chrome: TileChrome) {
    val pad = 6.dp.toPx()
    clipRect(rect.left, rect.top, rect.right, rect.bottom) {
        if (label.framed) {
            val header = chrome.metrics.header
            label.name?.let { name ->
                drawText(name, topLeft = Offset(rect.left + pad, rect.top + (header - name.size.height) / 2f), alpha = alpha)
            }
            label.size?.let { size ->
                drawText(
                    size,
                    topLeft = Offset(rect.right - pad - size.size.width, rect.top + (header - size.size.height) / 2f),
                    alpha = alpha,
                )
            }
        } else {
            val top = rect.top + pad * 0.8f
            label.name?.let { drawText(it, topLeft = Offset(rect.left + pad, top), alpha = alpha) }
            val nameHeight = label.name?.size?.height ?: 0
            label.size?.let { drawText(it, topLeft = Offset(rect.left + pad, top + nameHeight), alpha = alpha) }
        }
    }
}

/**
 * Two shared gradients drawn into a fixed unit square that is scaled onto each tile: the
 * shader is built once for the whole map instead of once per tile per frame.
 */
private class CushionBrushes {
    val shade: Brush = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.20f),
        0.45f to Color.Transparent,
        1f to Color.Black.copy(alpha = 0.32f),
        start = Offset.Zero,
        end = Offset(UNIT, UNIT),
    )
    val gloss: Brush = Brush.radialGradient(
        0f to Color.White.copy(alpha = 0.16f),
        1f to Color.Transparent,
        center = Offset(UNIT * 0.3f, UNIT * 0.22f),
        radius = UNIT * 0.75f,
    )
}

/** WizTree's cushion: a solid tile, lit from the top left, falling off to the bottom right. */
private fun DrawScope.cushion(rect: MapRect, color: Color, radius: Float, alpha: Float, brushes: CushionBrushes) {
    if (rect.width < 0.5f || rect.height < 0.5f || alpha <= 0.01f) return
    val r = min(radius, min(rect.width, rect.height) / 2f)
    if (color.alpha > 0f) {
        drawRoundRect(
            color = color,
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
            cornerRadius = CornerRadius(r),
            alpha = alpha,
        )
    }
    if (rect.width < 3f || rect.height < 3f) return
    val sx = rect.width / UNIT
    val sy = rect.height / UNIT
    // The corner radius is given in unit space so that it lands on [r] after the scale.
    val corner = CornerRadius(r / sx, r / sy)
    withTransform({
        translate(rect.left, rect.top)
        scale(sx, sy, pivot = Offset.Zero)
    }) {
        drawRoundRect(brushes.shade, size = UnitSize, cornerRadius = corner, alpha = alpha)
        drawRoundRect(brushes.gloss, size = UnitSize, cornerRadius = corner, alpha = alpha)
    }
    if (rect.width < SheenMin || rect.height < SheenMin) return
    // A lit top edge and a faint rim, like the glass cards around the map: each tile reads
    // as a pane catching the light rather than a flat patch of colour.
    drawLine(
        color = Color.White.copy(alpha = 0.34f * alpha),
        start = Offset(rect.left + r, rect.top + 0.75f),
        end = Offset(rect.right - r, rect.top + 0.75f),
        strokeWidth = 1.5f,
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.10f * alpha),
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
        cornerRadius = CornerRadius(r),
        style = Stroke(1f),
    )
}

private fun MapRect.scaledAroundCenter(factor: Float): MapRect {
    val w = width * factor / 2f
    val h = height * factor / 2f
    return MapRect(centerX - w, centerY - h, centerX + w, centerY + h)
}

/** Siblings arrive one after the other on entrance; past the stagger cap the rest come together. */
private fun staggered(progress: Float, index: Int): Float {
    val delay = (minOf(index, Motion.StaggerCap) * Motion.StaggerMs).toFloat() / STAGGER_WINDOW_MS
    if (delay >= 1f) return progress.coerceIn(0f, 1f)
    return ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)
}

private fun fadeIn(p: Float): Float = ((p - 0.55f) / 0.45f).coerceIn(0f, 1f)

private fun fadeOut(p: Float): Float = (1f - p * 2.5f).coerceIn(0f, 1f)

private const val UNIT = 64f
private const val SheenMin = 10f

/** The overshooting drill spring dips just under 1 on its way back; the outline waits it out. */
private const val SettledProgress = 0.97f
private val UnitSize = Size(UNIT, UNIT)
private const val STAGGER_WINDOW_MS = 700f

/** Text on a coloured tile: near-black reads on every category colour of the spectrum. */
private val TileInk = Color(0xFF0B0B10)
