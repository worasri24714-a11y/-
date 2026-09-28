package app.copyruler.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colours outside the Material scheme that the ruler screens need. */
@Immutable
data class RulerColors(
    /** Highlighter yellow behind the current segment and on the photo band. */
    val highlight: Color,
    /** Ink on top of [highlight]; always dark so it reads on yellow in both themes. */
    val onHighlight: Color,
    /** Document cards, like sheets of paper on the desk. */
    val card: Color,
    /** Darkens the parts of a photo above and below the band. */
    val photoDim: Color,
    /** Backdrop around a photo that does not fill the screen. */
    val photoBackdrop: Color,
)

private val LightRulerColors = RulerColors(
    highlight = HighlighterLight,
    onHighlight = GraphiteLight,
    card = Color.White,
    photoDim = Color.Black.copy(alpha = 0.55f),
    photoBackdrop = Color(0xFF2A2F33),
)

private val DarkRulerColors = RulerColors(
    highlight = HighlighterDark,
    onHighlight = PaperDark,
    card = Color(0xFF1F2327),
    photoDim = Color.Black.copy(alpha = 0.55f),
    photoBackdrop = Color(0xFF0E1012),
)

private val LocalRulerColors = staticCompositionLocalOf { LightRulerColors }

object RulerTheme {
    val colors: RulerColors
        @Composable
        @ReadOnlyComposable
        get() = LocalRulerColors.current
}

@Composable
fun CopyRulerTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalRulerColors provides if (darkTheme) DarkRulerColors else LightRulerColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
