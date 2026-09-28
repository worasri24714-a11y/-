package app.copyruler.ruler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tan

class LineDetectorTest {

    private val width = 400
    private val height = 600

    /** Line (top, bottom) in pixels; words are dark dashes, with optional Thai marks above. */
    private fun page(
        lines: List<Pair<Int, Int>>,
        thaiMarks: Boolean = false,
        skewDegrees: Double = 0.0,
    ): IntArray {
        val slope = tan(Math.toRadians(skewDegrees))
        // Uneven lighting: brighter on the left, darker towards the bottom right.
        val lum = IntArray(width * height) { i -> 235 - (i % width) / 10 - (i / width) / 20 }
        fun ink(x: Int, y: Int) {
            val yy = (y + x * slope).roundToInt()
            if (x in 0 until width && yy in 0 until height) lum[yy * width + x] = 40
        }
        for ((top, bottom) in lines) {
            var x = 30
            while (x < width - 40) {
                val word = 20 + (x * 7 % 23)
                for (dx in 0 until word step 3) for (y in top until bottom) ink(x + dx, y)
                if (thaiMarks) for (dx in 0 until word step 9) for (y in top - 5 until top - 2) ink(x + dx, y)
                x += word + 10
            }
        }
        return lum
    }

    private fun detect(lum: IntArray) = LineDetector.detect(lum, width, height)

    private fun assertCovers(expected: List<Pair<Int, Int>>, found: List<LineSpan>) {
        assertEquals("line count", expected.size, found.size)
        expected.zip(found).forEach { (e, f) ->
            val center = (e.first + e.second) / 2f / height
            assertTrue("line $e not covered by ${f.top * height}..${f.bottom * height}", center in f.top..f.bottom)
            assertTrue("band too tall for $e", f.height * height < (e.second - e.first) * 2.6f)
        }
    }

    @Test
    fun findsLinesWithUnevenSpacingAndParagraphGaps() {
        val lines = listOf(40 to 54, 72 to 86, 104 to 118, 190 to 204, 222 to 236, 254 to 268, 340 to 354, 371 to 385)
        assertCovers(lines, detect(page(lines)))
    }

    @Test
    fun thaiMarksAboveTheLineStayWithIt() {
        val lines = listOf(50 to 64, 90 to 104, 130 to 144, 170 to 184, 250 to 264)
        val found = detect(page(lines, thaiMarks = true))
        assertCovers(lines, found)
        // The band reaches up to include the marks drawn 2-5 px above each line.
        found.zip(lines).forEach { (f, l) -> assertTrue(f.top * height <= l.first - 4) }
    }

    @Test
    fun blankPageHasNoLines() {
        assertEquals(emptyList<LineSpan>(), detect(IntArray(width * height) { 230 }))
    }

    @Test
    fun measuresSkewSoThePhotoCanBeStraightened() {
        val lines = (0 until 12).map { 60 + it * 34 to 74 + it * 34 }
        for (angle in listOf(-3.0, 2.0, 4.0)) {
            val lum = page(lines, skewDegrees = angle)
            val ink = LineDetector.inkMap(lum, width, height)
            val found = LineDetector.skewDegrees(ink, width, height)
            assertTrue("expected ~$angle°, got $found°", abs(found - angle) <= 0.5)
        }
        val level = LineDetector.inkMap(page(lines), width, height)
        assertEquals(0f, LineDetector.skewDegrees(level, width, height), 0.26f)
    }

    @Test
    fun nearestPicksTheLineUnderOrClosestToAPoint() {
        val lines = listOf(LineSpan(0.1f, 0.15f), LineSpan(0.2f, 0.25f), LineSpan(0.4f, 0.45f))
        assertEquals(1, LineDetector.nearest(lines, 0.22f))
        assertEquals(1, LineDetector.nearest(lines, 0.3f))
        assertEquals(2, LineDetector.nearest(lines, 0.36f))
        assertEquals(-1, LineDetector.nearest(emptyList(), 0.5f))
    }
}
