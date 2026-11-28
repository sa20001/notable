package com.ethran.notable.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageFormatTest {

    // A Note Air 4C screen, held either way: the sheet does not depend on how it is held.
    private val screens = listOf(1860 to 2480, 2480 to 1860)

    @Test
    fun `a portrait sheet is as wide as the screen's short side`() {
        for ((width, height) in screens) {
            assertEquals(SheetSize(1860, 2632), PageFormat.A4.sheetSize(SheetOrientation.PORTRAIT, width, height))
        }
    }

    @Test
    fun `a landscape sheet is as wide as the screen's long side`() {
        for ((width, height) in screens) {
            assertEquals(SheetSize(2480, 1752), PageFormat.A4.sheetSize(SheetOrientation.LANDSCAPE, width, height))
        }
    }

    @Test
    fun `an infinite page has no sheet`() {
        assertNull(PageFormat.INFINITE.sheetSize(SheetOrientation.PORTRAIT, 1860, 2480))
    }

    @Test
    fun `every preset is recognised from the sheet it makes, in both orientations`() {
        for (format in PageFormat.entries.filter { it != PageFormat.INFINITE }) {
            for (orientation in SheetOrientation.entries) {
                val sheet = format.sheetSize(orientation, 1404, 1872)!!
                assertEquals("$format $orientation", format, PageFormat.matching(sheet))
            }
        }
    }

    @Test
    fun `a sheet of no preset's proportions matches none`() {
        assertNull(PageFormat.matching(SheetSize(1000, 1000)))
    }
}
