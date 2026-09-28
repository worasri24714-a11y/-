package app.copyruler.ruler

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tan

/** A text line found in a photo, as fractions of the image height (0 = top, 1 = bottom). */
data class LineSpan(val top: Float, val bottom: Float) {
    val height: Float get() = bottom - top
    val center: Float get() = (top + bottom) / 2f
}

/**
 * Finds text lines in a photo of a page so the highlight band can jump from line to line,
 * even when the spacing is uneven (paragraph gaps, headings) and the photo is slightly tilted.
 *
 * Works on a small grayscale copy: ink pixels are those clearly darker than their surroundings,
 * each image row gets an ink count, and runs of inked rows become lines. Thai vowels and tone
 * marks form thin runs just above or below a line; those are merged into the line.
 */
object LineDetector {

    /** Marks pixels that are clearly darker than the local background (handles uneven lighting). */
    fun inkMap(luminance: IntArray, width: Int, height: Int, radius: Int = 12, contrast: Int = 22): BooleanArray {
        val background = boxBlur(luminance, width, height, radius)
        return BooleanArray(width * height) { luminance[it] < background[it] - contrast }
    }

    /**
     * Angle of the text lines in degrees; positive when lines run downhill to the right.
     * Rotating the image by the negative of this angle makes the lines level.
     */
    fun skewDegrees(ink: BooleanArray, width: Int, height: Int, maxDegrees: Float = 6f, step: Float = 0.25f): Float {
        var best = 0f
        var bestScore = -1.0
        var angle = -maxDegrees
        while (angle <= maxDegrees + 1e-3f) {
            val score = profileSharpness(ink, width, height, angle)
            if (score > bestScore + 1e-9 || (abs(score - bestScore) < 1e-9 && abs(angle) < abs(best))) {
                bestScore = score
                best = angle
            }
            angle += step
        }
        return best
    }

    /** Number of ink pixels in each row. */
    fun rowProfile(ink: BooleanArray, width: Int, height: Int): IntArray =
        IntArray(height) { y -> (0 until width).count { x -> ink[y * width + x] } }

    /** Text lines from a row profile, padded slightly so the band covers ascenders and descenders. */
    fun findLines(profile: IntArray): List<LineSpan> {
        val n = profile.size
        if (n < 8) return emptyList()
        val smooth = FloatArray(n) { i ->
            (profile[max(i - 1, 0)] + profile[i] + profile[min(i + 1, n - 1)]) / 3f
        }
        val inked = smooth.filter { it > 0f }.sorted()
        if (inked.size < 3) return emptyList()
        val p90 = inked[((inked.size - 1) * 0.9f).roundToInt()]
        val threshold = max(1f, p90 * 0.12f)

        var runs = mutableListOf<IntArray>()
        var start = -1
        for (i in 0..n) {
            val on = i < n && smooth[i] > threshold
            if (on && start < 0) start = i
            if (!on && start >= 0) {
                runs += intArrayOf(start, i)
                start = -1
            }
        }
        if (runs.isEmpty()) return emptyList()

        // Merge thin runs (Thai vowels/tone marks, i-dots) into the line next to them.
        val typical = median(runs.map { it[1] - it[0] }.filter { it >= 2 }) ?: return emptyList()
        runs = mergeClose(runs, maxGap = typical * 0.45f, maxHeight = typical * 1.9f)
        val lineHeight = median(runs.map { it[1] - it[0] }.filter { it >= typical * 0.5f }) ?: typical

        // Drop specks, split runs that are really two or three touching lines.
        val lines = mutableListOf<IntArray>()
        for (run in runs) {
            val h = run[1] - run[0]
            if (h < lineHeight * 0.35f) continue
            val parts = if (h > lineHeight * 1.7f) (h.toFloat() / lineHeight).roundToInt().coerceAtLeast(2) else 1
            for (p in 0 until parts) {
                lines += intArrayOf(run[0] + h * p / parts, run[0] + h * (p + 1) / parts)
            }
        }
        if (lines.isEmpty()) return emptyList()

        return lines.mapIndexed { i, line ->
            val gapAbove = if (i == 0) Int.MAX_VALUE else line[0] - lines[i - 1][1]
            val gapBelow = if (i == lines.lastIndex) Int.MAX_VALUE else lines[i + 1][0] - line[1]
            val pad = min(lineHeight * 0.3f, min(gapAbove, gapBelow) / 2f)
            LineSpan(
                top = ((line[0] - pad) / n).coerceIn(0f, 1f),
                bottom = ((line[1] + pad) / n).coerceIn(0f, 1f),
            )
        }
    }

    /** Convenience: luminance in, lines out, assuming the image is already level. */
    fun detect(luminance: IntArray, width: Int, height: Int): List<LineSpan> =
        findLines(rowProfile(inkMap(luminance, width, height), width, height))

    /** Index of the line nearest to [fraction], or -1 if there are none. */
    fun nearest(lines: List<LineSpan>, fraction: Float): Int =
        lines.indices.minByOrNull { i ->
            val l = lines[i]
            if (fraction in l.top..l.bottom) 0f else min(abs(fraction - l.top), abs(fraction - l.bottom))
        } ?: -1

    private fun mergeClose(runs: List<IntArray>, maxGap: Float, maxHeight: Float): MutableList<IntArray> {
        val out = mutableListOf<IntArray>()
        for (run in runs) {
            val last = out.lastOrNull()
            if (last != null && run[0] - last[1] <= maxGap && run[1] - last[0] <= maxHeight) {
                last[1] = run[1]
            } else {
                out += run.copyOf()
            }
        }
        return out
    }

    private fun median(values: List<Int>): Int? = values.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }

    /** Sum of squared row counts after shearing by [degrees]; highest when lines are level. */
    private fun profileSharpness(ink: BooleanArray, width: Int, height: Int, degrees: Float): Double {
        val slope = tan(Math.toRadians(degrees.toDouble()))
        val bins = IntArray(height)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (!ink[row + x]) continue
                val yy = (y - x * slope).roundToInt()
                if (yy in 0 until height) bins[yy]++
            }
        }
        var sum = 0.0
        for (b in bins) sum += b.toDouble() * b
        return sum
    }

    private fun boxBlur(values: IntArray, width: Int, height: Int, radius: Int): IntArray {
        // Summed-area table keeps this O(pixels) whatever the radius.
        val sat = LongArray((width + 1) * (height + 1))
        for (y in 0 until height) {
            var rowSum = 0L
            for (x in 0 until width) {
                rowSum += values[y * width + x]
                sat[(y + 1) * (width + 1) + x + 1] = sat[y * (width + 1) + x + 1] + rowSum
            }
        }
        return IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            val x0 = max(0, x - radius)
            val x1 = min(width, x + radius + 1)
            val y0 = max(0, y - radius)
            val y1 = min(height, y + radius + 1)
            val sum = sat[y1 * (width + 1) + x1] - sat[y0 * (width + 1) + x1] -
                sat[y1 * (width + 1) + x0] + sat[y0 * (width + 1) + x0]
            (sum / ((x1 - x0) * (y1 - y0))).toInt()
        }
    }
}
