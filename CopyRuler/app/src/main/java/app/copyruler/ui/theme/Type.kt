package app.copyruler.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.copyruler.R

/** IBM Plex Sans Thai, bundled in res/font so the app works fully offline. */
val PlexSansThai = FontFamily(
    Font(R.font.ibm_plex_sans_thai_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_thai_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_thai_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_thai_bold, FontWeight.Bold),
)

private val Default = Typography()

/** Thai vowels and tone marks stack above and below letters, so body text gets extra leading. */
private fun TextStyle.plex(lineHeight: TextUnit = this.lineHeight) =
    copy(fontFamily = PlexSansThai, lineHeight = lineHeight)

val AppTypography = Typography(
    displayLarge = Default.displayLarge.plex(),
    displayMedium = Default.displayMedium.plex(),
    displaySmall = Default.displaySmall.plex(),
    headlineLarge = Default.headlineLarge.plex(),
    headlineMedium = Default.headlineMedium.plex(),
    headlineSmall = Default.headlineSmall.plex(),
    titleLarge = Default.titleLarge.plex().copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Default.titleMedium.plex(lineHeight = 26.sp).copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Default.titleSmall.plex(lineHeight = 22.sp),
    bodyLarge = Default.bodyLarge.plex(lineHeight = 26.sp),
    bodyMedium = Default.bodyMedium.plex(lineHeight = 22.sp),
    bodySmall = Default.bodySmall.plex(lineHeight = 18.sp),
    labelLarge = Default.labelLarge.plex(lineHeight = 22.sp).copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Default.labelMedium.plex(lineHeight = 18.sp),
    labelSmall = Default.labelSmall.plex(lineHeight = 16.sp),
)
