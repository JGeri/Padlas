package hu.padlas.core.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.Space

/**
 * Background work is the only thing allowed to move while you are not touching the screen,
 * and it moves as thinly as it can: a hairline track with a brass fill, no percentage that
 * can get stuck at 99.
 */
@Composable
fun ProgressStrip(fraction: Float, label: String, modifier: Modifier = Modifier) {
    val filled = animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = motionSpec(Motion.settle()),
        label = "progressStrip",
    )
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(label)
        Spacer(Modifier.height(Space.s))
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .drawBehind {
                    drawRect(Ink.Hairline)
                    drawRect(Ink.Brass, size = Size(size.width * filled.value, size.height))
                },
        )
    }
}
