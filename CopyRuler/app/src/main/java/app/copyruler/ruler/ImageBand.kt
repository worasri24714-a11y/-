package app.copyruler.ruler

import kotlin.math.abs

/**
 * Math for the highlight band laid over a photo. Positions are fractions of the image
 * height (0 = top edge, 1 = bottom edge) so they survive zooming and screen rotation.
 */
object ImageBand {
    const val DEFAULT_HEIGHT = 0.04f
    const val MIN_HEIGHT = 0.01f
    const val MAX_HEIGHT = 0.20f

    private const val EPSILON = 0.0001f

    fun clampHeight(height: Float): Float = height.coerceIn(MIN_HEIGHT, MAX_HEIGHT)

    fun clampTop(top: Float, height: Float): Float =
        top.coerceIn(0f, (1f - height).coerceAtLeast(0f))

    /** Moves down by one band height, stopping at the bottom edge. */
    fun next(top: Float, height: Float): Float = clampTop(top + height, height)

    /** Moves up by one band height, stopping at the top edge. */
    fun previous(top: Float, height: Float): Float = clampTop(top - height, height)

    /** Band top that centres the band on [fraction], e.g. where the user tapped. */
    fun centeredAt(fraction: Float, height: Float): Float = clampTop(fraction - height / 2f, height)

    /** How far down the image the band has reached, 0..1. */
    fun progress(top: Float, height: Float): Float = (top + height).coerceIn(0f, 1f)

    fun sameTop(a: Float, b: Float): Boolean = abs(a - b) < EPSILON
}
