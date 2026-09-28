package app.copyruler.ui.common

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Short vibrations for moving the ruler. Uses the vibrator directly (rather than view
 * haptics) so the feedback is felt even when system touch vibration is turned off.
 */
class Haptics(private val vibrator: Vibrator?, private val enabled: Boolean) {

    /** A light tick each time the ruler moves. */
    fun tick() {
        vibrate(
            predefined = VibrationEffect.EFFECT_CLICK,
            fallback = { VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE) },
        )
    }

    /** A double buzz when the ruler is already at the first or last position. */
    fun edge() {
        vibrate(
            predefined = VibrationEffect.EFFECT_DOUBLE_CLICK,
            fallback = { VibrationEffect.createWaveform(longArrayOf(0, 18, 70, 18), -1) },
        )
    }

    private fun vibrate(predefined: Int, fallback: () -> VibrationEffect) {
        val vibrator = vibrator ?: return
        if (!enabled || !vibrator.hasVibrator()) return
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(predefined)
        } else {
            fallback()
        }
        vibrator.vibrate(effect)
    }
}

@Composable
fun rememberHaptics(enabled: Boolean): Haptics {
    val context = LocalContext.current
    return remember(context, enabled) { Haptics(context.vibrator(), enabled) }
}

private fun Context.vibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        getSystemService(Vibrator::class.java)
    }
