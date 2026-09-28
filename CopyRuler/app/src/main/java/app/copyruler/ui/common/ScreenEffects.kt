package app.copyruler.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import app.copyruler.MainActivity
import app.copyruler.VolumeKey

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Keeps the display on for as long as the calling screen is shown. */
@Composable
fun KeepScreenOn() {
    val window = LocalContext.current.findActivity()?.window
    DisposableEffect(window) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

/** Routes the hardware volume keys to the ruler while the calling screen is shown. */
@Composable
fun VolumeKeysEffect(enabled: Boolean, onNext: () -> Unit, onPrevious: () -> Unit) {
    val activity = LocalContext.current.findActivity() as? MainActivity
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    DisposableEffect(activity, enabled) {
        if (activity != null && enabled) {
            activity.volumeKeyListener = { key ->
                when (key) {
                    VolumeKey.NEXT -> next()
                    VolumeKey.PREVIOUS -> previous()
                }
            }
        }
        onDispose { activity?.volumeKeyListener = null }
    }
}
