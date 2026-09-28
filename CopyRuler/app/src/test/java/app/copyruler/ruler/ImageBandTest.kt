package app.copyruler.ruler

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageBandTest {

    @Test
    fun next_movesDownByOneBandHeight() {
        assertEquals(0.05f, ImageBand.next(top = 0f, height = 0.05f), 1e-6f)
        assertEquals(0.30f, ImageBand.next(top = 0.25f, height = 0.05f), 1e-6f)
    }

    @Test
    fun next_stopsAtBottomEdge() {
        assertEquals(0.95f, ImageBand.next(top = 0.93f, height = 0.05f), 1e-6f)
        assertEquals(0.95f, ImageBand.next(top = 0.95f, height = 0.05f), 1e-6f)
    }

    @Test
    fun previous_stopsAtTopEdge() {
        assertEquals(0.10f, ImageBand.previous(top = 0.15f, height = 0.05f), 1e-6f)
        assertEquals(0f, ImageBand.previous(top = 0.02f, height = 0.05f), 1e-6f)
    }

    @Test
    fun centeredAt_centresBandOnTapAndClamps() {
        assertEquals(0.48f, ImageBand.centeredAt(fraction = 0.5f, height = 0.04f), 1e-6f)
        assertEquals(0f, ImageBand.centeredAt(fraction = 0.01f, height = 0.04f), 1e-6f)
        assertEquals(0.96f, ImageBand.centeredAt(fraction = 1f, height = 0.04f), 1e-6f)
    }

    @Test
    fun progress_isHowFarTheBandReaches() {
        assertEquals(0.04f, ImageBand.progress(top = 0f, height = 0.04f), 1e-6f)
        assertEquals(1f, ImageBand.progress(top = 0.96f, height = 0.04f), 1e-6f)
    }

    @Test
    fun clampHeight_staysWithinSliderRange() {
        assertEquals(ImageBand.MIN_HEIGHT, ImageBand.clampHeight(0f), 1e-6f)
        assertEquals(ImageBand.MAX_HEIGHT, ImageBand.clampHeight(0.9f), 1e-6f)
        assertEquals(0.05f, ImageBand.clampHeight(0.05f), 1e-6f)
    }
}
