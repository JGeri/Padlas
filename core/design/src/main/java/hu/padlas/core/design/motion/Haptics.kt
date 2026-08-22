package hu.padlas.core.design.motion

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Haptics are bound to the peak of a movement, not to the touch that started it. The
 * S22 Ultra has an LRA worth using; a single generic click would waste it.
 */
class PadlasHaptics(context: Context) {

    private val vibrator: Vibrator? = runCatching {
        val manager = context.getSystemService(VibratorManager::class.java)
        manager?.defaultVibrator
    }.getOrNull()

    private val supportsComposition: Boolean = vibrator?.areAllPrimitivesSupported(
        VibrationEffect.Composition.PRIMITIVE_TICK,
        VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
        VibrationEffect.Composition.PRIMITIVE_THUD,
    ) ?: false

    /** Drag crossed the commit threshold. */
    fun threshold() = compose { addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.3f) }

    /** The card lands in the bin. */
    fun binImpact() = compose {
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.4f)
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.7f, 120)
    }

    /** Lifted into the attic: quieter, it left rather than landed. */
    fun lift() = compose {
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.5f)
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.2f, 90)
    }

    fun undo() = compose { addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.4f) }

    /** A duplicate group collapsing: three fading ticks. */
    fun collapse() = compose {
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f)
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.35f, 40)
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.2f, 40)
    }

    /** The batch is committed. This is the heaviest thing the app does. */
    fun commit() = compose {
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.3f)
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 80)
    }

    /** Ticks during the hold-to-delete arc; scale rises as the arc closes. */
    fun holdTick(progress: Float) = compose {
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.2f + 0.5f * progress)
    }

    fun tick(scale: Float = 0.3f) = compose {
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, scale)
    }

    private inline fun compose(block: VibrationEffect.Composition.() -> Unit) {
        val v = vibrator ?: return
        if (!supportsComposition) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            return
        }
        runCatching { v.vibrate(VibrationEffect.startComposition().apply(block).compose()) }
    }
}

val LocalHaptics = compositionLocalOf<PadlasHaptics?> { null }

@Composable
fun rememberPadlasHaptics(): PadlasHaptics {
    val context = LocalContext.current
    return remember(context) { PadlasHaptics(context.applicationContext) }
}

@Composable
@ReadOnlyComposable
fun haptics(): PadlasHaptics? = LocalHaptics.current
