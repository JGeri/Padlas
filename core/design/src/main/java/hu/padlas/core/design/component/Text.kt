package hu.padlas.core.design.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val RollStaggerMs = 40L

private data class SlotMetrics(val width: Dp, val height: Dp)

/**
 * Each digit rolls in its own slot, fixed to the widest figure so the number never shivers
 * while it changes. Digits swap right to left, the direction a figure actually changes in.
 */
@Composable
fun RollingNumber(
    value: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Ink.Text,
) {
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val density = LocalDensity.current
    val digitPair = measurer.measure("00", style, maxLines = 1).size.width
    val slot = with(density) {
        var widest = 0
        var tallest = 0
        for (digit in '0'..'9') {
            val line = measurer.measure(digit.toString(), style, maxLines = 1)
            widest = maxOf(widest, line.size.width)
            tallest = maxOf(tallest, line.size.height)
        }
        SlotMetrics(widest.toDp(), tallest.toDp())
    }

    val displayed = remember { mutableStateListOf<Char>().apply { addAll(value.toList()) } }
    var previous by remember { mutableStateOf(value) }
    var rollDown by remember { mutableStateOf(true) }
    val reduced = LocalReducedMotion.current
    val slideSpec = finiteSpec(reduced, Motion.settle<IntOffset>())
    val fadeSpec = finiteSpec(reduced, Motion.settle<Float>())
    val down = rollDown

    LaunchedEffect(value) {
        val from = previous
        if (from == value) return@LaunchedEffect
        previous = value
        rollDown = rollsDown(from, value)
        val target = value.toList()
        if (displayed.size != target.size) {
            val old = displayed.toList()
            val shift = target.size - old.size
            displayed.clear()
            // Right aligned, so the columns the two figures share still roll instead of
            // being rebuilt from nothing.
            for (index in target.indices) {
                val source = index - shift
                displayed.add(if (source in old.indices) old[source] else target[index])
            }
        }
        val step = if (reduced) 0L else RollStaggerMs
        coroutineScope {
            for (index in target.indices) {
                val order = target.lastIndex - index
                launch {
                    delay(step * order)
                    if (displayed[index] != target[index]) displayed[index] = target[index]
                }
            }
        }
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        for (index in displayed.indices) {
            val char = displayed[index]
            when {
                char in '0'..'9' -> AnimatedContent(
                    targetState = char,
                    modifier = Modifier
                        .width(slot.width)
                        .height(slot.height)
                        .clipToBounds(),
                    transitionSpec = {
                        val enter = slideInVertically(slideSpec) { full -> if (down) -full else full } +
                            fadeIn(fadeSpec)
                        val exit = slideOutVertically(slideSpec) { full -> if (down) full else -full } +
                            fadeOut(fadeSpec)
                        enter togetherWith exit using SizeTransform(clip = false)
                    },
                    contentAlignment = Alignment.Center,
                    label = "digit",
                ) { digit ->
                    Text(
                        text = digit.toString(),
                        modifier = Modifier.fillMaxWidth(),
                        color = color,
                        style = style,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
                // A run holding nothing but a space measures zero, because trailing
                // whitespace is dropped from the line width; the gap has to be measured
                // between two digits instead.
                char.isWhitespace() -> {
                    val gap = measurer.measure("0" + char + "0", style, maxLines = 1).size.width - digitPair
                    Spacer(Modifier.width(with(density) { gap.coerceAtLeast(0).toDp() }))
                }
                else -> Text(text = char.toString(), color = color, style = style, maxLines = 1)
            }
        }
    }
}

/** More digits means a bigger figure, so length decides before lexical order does. */
private fun rollsDown(from: String, to: String): Boolean {
    val before = from.filter { it.isDigit() }
    val after = to.filter { it.isDigit() }
    return when {
        before.isEmpty() || after.isEmpty() -> true
        before.length != after.length -> after.length < before.length
        else -> after < before
    }
}

/** A quiet group heading. Old callers still pass all caps; it is set in sentence case. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.sentenceCase(),
        modifier = modifier,
        color = Ink.TextSecondary,
        style = PadlasTheme.type.label,
        maxLines = 1,
    )
}

/** Section title with an optional action on the right ("Mind", "Szerkesztés"). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Ink.Text,
            style = PadlasTheme.type.title,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            PadlasTextButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun NumericText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Ink.TextSecondary,
    large: Boolean = false,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = if (large) PadlasTheme.type.numericLarge else PadlasTheme.type.numeric,
        maxLines = 1,
    )
}
