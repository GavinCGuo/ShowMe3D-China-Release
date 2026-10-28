package com.example.showme3d

import com.example.showme3d.studio.aigc.PixelBounds
import com.example.showme3d.studio.aigc.SubjectCutoutAlgorithm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectCutoutProcessorTest {
    @Test
    fun edgeFloodMaskSeparatesCenterSubjectFromFlatBackground() {
        val white = argb(255, 255, 255, 255)
        val black = argb(255, 0, 0, 0)
        val pixels = IntArray(49) { white }
        for (y in 2..4) {
            for (x in 2..4) {
                pixels[y * 7 + x] = black
            }
        }

        val mask = SubjectCutoutAlgorithm.buildEdgeBackgroundMask(
            pixels = pixels,
            width = 7,
            height = 7,
            tolerance = 10,
        )

        assertEquals(PixelBounds(left = 2, top = 2, right = 4, bottom = 4), mask.foregroundBounds)
        assertEquals(40f / 49f, mask.backgroundCoverage, 0.0001f)
        assertEquals(9f / 49f, mask.foregroundCoverage, 0.0001f)
        assertTrue(mask.background[0])
        assertFalse(mask.background[24])
    }

    @Test
    fun alphaForegroundBoundsTrimTransparentMargin() {
        val transparent = argb(0, 255, 255, 255)
        val red = argb(255, 220, 20, 20)
        val pixels = IntArray(16) { transparent }
        pixels[1 * 4 + 1] = red
        pixels[2 * 4 + 2] = red

        assertTrue(SubjectCutoutAlgorithm.hasMeaningfulTransparency(pixels))
        assertEquals(
            PixelBounds(left = 1, top = 1, right = 2, bottom = 2),
            SubjectCutoutAlgorithm.alphaForegroundBounds(pixels, width = 4, height = 4),
        )
    }

    @Test
    fun allBackgroundImageProducesNoForegroundBounds() {
        val white = argb(255, 255, 255, 255)

        val mask = SubjectCutoutAlgorithm.buildEdgeBackgroundMask(
            pixels = IntArray(16) { white },
            width = 4,
            height = 4,
            tolerance = 10,
        )

        assertEquals(null, mask.foregroundBounds)
        assertEquals(1f, mask.backgroundCoverage, 0.0001f)
        assertEquals(0f, mask.foregroundCoverage, 0.0001f)
    }

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha shl 24) or (red shl 16) or (green shl 8) or blue
}
