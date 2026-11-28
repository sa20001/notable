package com.ethran.notable.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import com.ethran.notable.data.model.SheetSize
import org.junit.Assert.assertEquals
import org.junit.Test

class SheetLayoutTest {

    private val viewWidth = 1404
    private val viewHeight = 1872
    private val wholeView = IntRect(0, 0, viewWidth, viewHeight)

    private fun area(sheet: SheetSize?, scroll: Offset, zoom: Float = 1f) =
        sheetAreaFor(sheet, PageViewport(scroll, zoom), viewWidth, viewHeight)

    private fun fitted(sheet: SheetSize?, scroll: Offset, zoom: Float = 1f): Offset =
        fittedToSheet(PageViewport(scroll, zoom), sheet, viewWidth, viewHeight).scroll

    private fun framed(sheet: SheetSize?, scroll: Offset, zoom: Float = 1f): Offset =
        framedOnSheet(PageViewport(scroll, zoom), sheet, viewWidth, viewHeight).scroll

    @Test
    fun theSheetsArea_isWhereTheViewportPutsIt_cutToTheView() {
        // A sheet framed in the middle, then panned so that it starts half way across and down.
        assertEquals(IntRect(202, 436, 1202, 1436), area(SheetSize(1000, 1000), Offset(-202f, -436f)))
        assertEquals(IntRect(702, 936, viewWidth, viewHeight), area(SheetSize(2480, 3000), Offset(-702f, -936f)))
        // At zoom 0.5, a sheet scrolled to x = 100 shows from 50 screen pixels left of the view.
        assertEquals(IntRect(0, 0, 1190, 1000), area(SheetSize(2480, 2000), Offset(100f, 0f), 0.5f))
    }

    @Test
    fun aSheetCoveringTheView_orNoSheet_takesTheWholeView() {
        assertEquals(wholeView, area(SheetSize(1404, 2000), Offset.Zero))
        assertEquals(wholeView, area(SheetSize(2480, 2000), Offset(500f, 100f)))
        // Fitted to the width: the sheet spans the view, up to float rounding.
        assertEquals(wholeView, area(SheetSize(1860, 2632), Offset.Zero, viewWidth / 1860f))
        // One whose product rounds to 1403.9999, under the view width.
        assertEquals(wholeView, area(SheetSize(2101, 3000), Offset.Zero, viewWidth / 2101f))
        assertEquals(wholeView, area(null, Offset(300f, 300f), 0.5f))
    }

    @Test
    fun panning_goesPastEachEdge_byHalfTheViewAtMost() {
        val sheet = SheetSize(2480, 3000)
        assertEquals(Offset(-702f, -936f), fitted(sheet, Offset(-5000f, -5000f)))
        assertEquals(Offset(2480f - 702f, 3000f - 936f), fitted(sheet, Offset(5000f, 5000f)))
        assertEquals(Offset(500f, 100f), fitted(sheet, Offset(500f, 100f)))
        // A sheet smaller than the view moves within the same reach, and at zoom 0.5 the view is
        // twice as many page pixels.
        assertEquals(Offset(-1404f, -1872f), fitted(SheetSize(1000, 1000), Offset(-5000f, -5000f), 0.5f))
    }

    @Test
    fun framing_centresAShortAxis_andKeepsALongOneWithinTheSheet() {
        assertEquals(Offset(-202f, -436f), framed(SheetSize(1000, 1000), Offset(300f, -40f)))
        assertEquals(Offset(0f, 128f), framed(SheetSize(2480, 2000), Offset(-40f, 5000f)))
        assertEquals(Offset(1076f, 100f), framed(SheetSize(2480, 2000), Offset(5000f, 100f)))
        // At zoom 0.5 the view spans 2808 page pixels.
        assertEquals(-904f, framed(SheetSize(1000, 5000), Offset.Zero, 0.5f).x, 0.001f)
    }

    @Test
    fun aPageWithoutASheet_onlyStaysRightOfAndBelowItsOrigin() {
        for (place in listOf(::fitted, ::framed)) {
            assertEquals(Offset.Zero, place(null, Offset(-10f, -20f), 2f))
            assertEquals(Offset(5000f, 300f), place(null, Offset(5000f, 300f), 2f))
        }
    }

    private val narrow = SheetSize(1000, 5000)
    private val wide = SheetSize(2480, 5000)

    @Test
    fun aPageOpensAtItsSavedScroll_framed() {
        assertEquals(
            PageViewport(Offset(500f, 300f)),
            openingViewport(null, Offset(500f, 300f), 1f, wide, viewWidth, viewHeight),
        )
        assertEquals(
            "a saved x has no say on a centred sheet",
            PageViewport(Offset(-202f, 300f)),
            openingViewport(null, Offset(5000f, 300f), 1f, narrow, viewWidth, viewHeight),
        )
    }

    @Test
    fun aPageComesBackAsItWasLeft_atTheSameViewSize() {
        val left = LeftViewport(PageViewport(Offset(-500f, -300f), 2f), viewWidth, viewHeight)
        assertEquals(left.viewport, openingViewport(left, Offset.Zero, 1f, wide, viewWidth, viewHeight))
    }

    @Test
    fun aPageLeftAtAnotherViewSize_comesBackAtTheZoomGiven_framed() {
        val left = LeftViewport(PageViewport(Offset(-300f, 300f), 0.5f), 2000, 1500)
        assertEquals(
            "its zoom was chosen for the old size; its scroll is kept, framed",
            PageViewport(Offset(-202f, 300f), 1f),
            openingViewport(left, Offset.Zero, 1f, narrow, viewWidth, viewHeight),
        )
    }
}
