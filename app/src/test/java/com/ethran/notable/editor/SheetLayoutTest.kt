package com.ethran.notable.editor

import androidx.compose.ui.geometry.Offset
import com.ethran.notable.data.model.SheetSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetLayoutTest {

    private val viewWidth = 1404

    @Test
    fun aNarrowSheet_getsStripsCoveringExactlyTheViewOutsideIt() {
        for (sheetWidth in listOf(600, 601, 1000, 1403)) {
            for (zoom in listOf(0.5f, 0.75f, 1f)) {
                val drawn = sheetWidth * zoom
                if (drawn > viewWidth - 0.5f) continue
                val bar = stripWidthFor(SheetSize(sheetWidth, 2000), viewWidth, zoom)
                val message = "sheet $sheetWidth at $zoom"
                assertTrue("$message: strips cover the slack", 2 * bar >= viewWidth - drawn)
                assertTrue("$message: strips stay off the sheet", 2 * bar <= viewWidth - drawn + 2)
            }
        }
    }

    @Test
    fun aSheetFillingOrWiderThanTheView_orNoSheet_getsNoStrips() {
        assertEquals(0, stripWidthFor(SheetSize(1404, 2000), viewWidth, 1f))
        assertEquals(0, stripWidthFor(SheetSize(2480, 2000), viewWidth, 1f))
        // Fitted to the width: the sheet spans the view, up to float rounding.
        assertEquals(0, stripWidthFor(SheetSize(1860, 2632), viewWidth, viewWidth / 1860f))
        // One whose product rounds to 1403.9999, under the view width.
        assertEquals(0, stripWidthFor(SheetSize(2101, 3000), viewWidth, viewWidth / 2101f))
        assertEquals(0, stripWidthFor(null, viewWidth, 0.5f))
    }

    @Test
    fun aNarrowSheet_isCentred_whateverTheScroll() {
        val sheet = SheetSize(1000, 2000)
        for (scrollX in listOf(-500f, 0f, 300f)) {
            assertEquals(-202f, fitScrollX(sheet, viewWidth, scrollX, 1f), 0.001f)
        }
        // At zoom 0.5 the view spans 2808 page pixels.
        assertEquals(-904f, fitScrollX(sheet, viewWidth, 0f, 0.5f), 0.001f)
    }

    @Test
    fun aWideSheet_staysBetweenItsEdges() {
        val sheet = SheetSize(2480, 2000)
        assertEquals(0f, fitScrollX(sheet, viewWidth, -40f, 1f), 0f)
        assertEquals(500f, fitScrollX(sheet, viewWidth, 500f, 1f), 0f)
        assertEquals(1076f, fitScrollX(sheet, viewWidth, 5000f, 1f), 0f)
    }

    @Test
    fun aPageWithoutASheet_onlyStaysRightOfAndBelowItsOrigin() {
        val viewport = PageViewport(Offset(-10f, -20f), 2f)
        assertEquals(PageViewport(Offset.Zero, 2f), fittedToSheet(viewport, null, viewWidth))
        val moved = PageViewport(Offset(5000f, 300f), 2f)
        assertEquals(moved, fittedToSheet(moved, null, viewWidth))
    }

    private val narrow = SheetSize(1000, 2000)
    private val wide = SheetSize(2480, 2000)

    @Test
    fun aPageOpensAtItsSavedScroll_exceptWhereItsSheetIsCentred() {
        assertEquals(
            PageViewport(Offset(500f, 300f)),
            openingViewport(null, Offset(500f, 300f), 1f, wide, viewWidth, 1872),
        )
        assertEquals(
            "a saved x has no say on a centred sheet",
            PageViewport(Offset(-202f, 300f)),
            openingViewport(null, Offset(5000f, 300f), 1f, narrow, viewWidth, 1872),
        )
    }

    @Test
    fun aPageComesBackAsItWasLeft_atTheSameViewSize() {
        val left = LeftViewport(PageViewport(Offset(100f, 300f), 2f), viewWidth, 1872)
        assertEquals(left.viewport, openingViewport(left, Offset.Zero, 1f, wide, viewWidth, 1872))
    }

    @Test
    fun aPageLeftAtAnotherViewSize_comesBackAtTheZoomGiven_refitted() {
        val left = LeftViewport(PageViewport(Offset(-300f, 300f), 0.5f), 2000, 1500)
        assertEquals(
            "its zoom was chosen for the old size; its scroll is kept, refitted",
            PageViewport(Offset(-202f, 300f), 1f),
            openingViewport(left, Offset.Zero, 1f, narrow, viewWidth, 1872),
        )
    }
}
