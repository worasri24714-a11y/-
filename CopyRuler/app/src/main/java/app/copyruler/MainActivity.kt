package app.copyruler

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.copyruler.data.AppSettings
import app.copyruler.data.ThemeMode
import app.copyruler.ui.CopyRulerNavHost
import app.copyruler.ui.theme.CopyRulerTheme

class MainActivity : ComponentActivity() {

    /**
     * Set by the copy screen while it is on screen and the volume-key setting is on;
     * null everywhere else, so the volume keys keep their normal behaviour.
     */
    var volumeKeyListener: ((VolumeKey) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as CopyRulerApp).container

        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = null)
            val darkTheme = when (settings?.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM, null -> isSystemInDarkTheme()
            }

            // Keep status/navigation bar icons readable when the in-app theme differs from the system.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
                )
                onDispose {}
            }

            CopyRulerTheme(darkTheme = darkTheme) {
                CopyRulerNavHost(container = container, settings = settings ?: AppSettings())
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val listener = volumeKeyListener
        val key = VolumeKey.fromKeyCode(keyCode)
        if (listener != null && key != null) {
            // Holding the key down must not race through the document.
            if (event.repeatCount == 0) listener(key)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (volumeKeyListener != null && VolumeKey.fromKeyCode(keyCode) != null) return true
        return super.onKeyUp(keyCode, event)
    }

    private companion object {
        val LIGHT_SCRIM = Color.argb(0xE6, 0xF3, 0xF5, 0xF4)
        val DARK_SCRIM = Color.argb(0x80, 0x15, 0x18, 0x1B)
    }
}

enum class VolumeKey {
    NEXT,
    PREVIOUS;

    companion object {
        fun fromKeyCode(keyCode: Int): VolumeKey? = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> NEXT
            KeyEvent.KEYCODE_VOLUME_UP -> PREVIOUS
            else -> null
        }
    }
}
