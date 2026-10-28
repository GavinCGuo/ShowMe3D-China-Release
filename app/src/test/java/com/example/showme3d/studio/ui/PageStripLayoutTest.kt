package com.example.showme3d.studio.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageStripLayoutTest {
    @Test
    fun singlePageUsesOneCompactTabWithoutScrollButtons() {
        val layout = pageStripLayout(1)

        assertEquals(1, layout.visiblePageCount)
        assertEquals(66, layout.viewportWidthDp)
        assertFalse(layout.showScrollButtons)
    }

    @Test
    fun fivePagesFitWithoutScrollButtons() {
        val layout = pageStripLayout(5)

        assertEquals(5, layout.visiblePageCount)
        assertEquals(366, layout.viewportWidthDp)
        assertFalse(layout.showScrollButtons)
    }

    @Test
    fun sixthPageEnablesScrollButtons() {
        val layout = pageStripLayout(6)

        assertEquals(5, layout.visiblePageCount)
        assertEquals(366, layout.viewportWidthDp)
        assertTrue(layout.showScrollButtons)
    }
}
