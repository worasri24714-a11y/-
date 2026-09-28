package app.copyruler.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// "Paper and highlighter" palette.
val PaperLight = Color(0xFFF3F5F4)
val GraphiteLight = Color(0xFF1F2428)
val HighlighterLight = Color(0xFFFFE45C)

val PaperDark = Color(0xFF15181B)
val GraphiteDark = Color(0xFFE7EAEC)
val HighlighterDark = Color(0xFFE8C93A)

internal val LightColors = lightColorScheme(
    primary = GraphiteLight,
    onPrimary = PaperLight,
    primaryContainer = HighlighterLight,
    onPrimaryContainer = GraphiteLight,
    inversePrimary = HighlighterLight,
    secondary = Color(0xFF4F5D58),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE5E1),
    onSecondaryContainer = GraphiteLight,
    tertiary = Color(0xFF6F5B00),
    onTertiary = Color.White,
    tertiaryContainer = HighlighterLight,
    onTertiaryContainer = GraphiteLight,
    background = PaperLight,
    onBackground = GraphiteLight,
    surface = PaperLight,
    onSurface = GraphiteLight,
    surfaceVariant = Color(0xFFE0E5E2),
    onSurfaceVariant = Color(0xFF58616A),
    surfaceTint = GraphiteLight,
    inverseSurface = GraphiteLight,
    inverseOnSurface = PaperLight,
    outline = Color(0xFF858E8A),
    outlineVariant = Color(0xFFC8CFCB),
    surfaceBright = Color(0xFFF8FAF9),
    surfaceDim = Color(0xFFD9DEDB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F9F8),
    surfaceContainer = Color(0xFFEBEFED),
    surfaceContainerHigh = Color(0xFFE5E9E7),
    surfaceContainerHighest = Color(0xFFDFE4E1),
)

internal val DarkColors = darkColorScheme(
    primary = GraphiteDark,
    onPrimary = PaperDark,
    primaryContainer = HighlighterDark,
    onPrimaryContainer = PaperDark,
    inversePrimary = GraphiteLight,
    secondary = Color(0xFFB6C4BE),
    onSecondary = Color(0xFF20302A),
    secondaryContainer = Color(0xFF2E3935),
    onSecondaryContainer = GraphiteDark,
    tertiary = HighlighterDark,
    onTertiary = PaperDark,
    tertiaryContainer = Color(0xFF4A3F0A),
    onTertiaryContainer = GraphiteDark,
    background = PaperDark,
    onBackground = GraphiteDark,
    surface = PaperDark,
    onSurface = GraphiteDark,
    surfaceVariant = Color(0xFF2A3035),
    onSurfaceVariant = Color(0xFFA8B0B6),
    surfaceTint = GraphiteDark,
    inverseSurface = GraphiteDark,
    inverseOnSurface = PaperDark,
    outline = Color(0xFF6C757C),
    outlineVariant = Color(0xFF394046),
    surfaceBright = Color(0xFF30363B),
    surfaceDim = PaperDark,
    surfaceContainerLowest = Color(0xFF101315),
    surfaceContainerLow = Color(0xFF1A1E21),
    surfaceContainer = Color(0xFF1E2226),
    surfaceContainerHigh = Color(0xFF262B2F),
    surfaceContainerHighest = Color(0xFF2E3439),
)
