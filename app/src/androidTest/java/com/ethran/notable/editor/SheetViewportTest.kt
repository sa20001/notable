package com.ethran.notable.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ethran.notable.SCREEN_HEIGHT
import com.ethran.notable.SCREEN_WIDTH
import com.ethran.notable.data.datastore.GlobalAppSettings
import com.ethran.notable.data.model.SheetSize
import com.ethran.notable.testing.A5_SHEET
import com.ethran.notable.testing.A6_SHEET
import com.ethran.notable.testing.SheetPageViews
import com.ethran.notable.testing.onMain
import com.ethran.notable.testing.testStroke
import com.ethran.notable.utils.AppResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/**
 * A [PageView] on pages with a sheet: every scroll and zoom step goes through the sheet's fit, the
 * screen bitmap follows it, and the scroll it settles on is the one saved. Where a viewport may be
 * is [SheetLayout]'s, tested on the JVM; these tests check the view applies it.
 */
@RunWith(AndroidJUnit4::class)
class SheetViewportTest {

    private lateinit var views: SheetPageViews
    private val screen = SCREEN_WIDTH to SCREEN_HEIGHT

    @Before
    fun setUp() {
        // A portrait screen, so a discrete pinch out snaps to 1200 / 1600 = 0.75.
        SCREEN_WIDTH = 1200
        SCREEN_HEIGHT = 1600
        views = SheetPageViews(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() {
        views.close()
        SCREEN_WIDTH = screen.first
        SCREEN_HEIGHT = screen.second
    }

    private suspend fun open(sheet: SheetSize?, viewWidth: Int, viewHeight: Int): PageView =
        views.open(views.seedPage(sheet), viewWidth, viewHeight)

    @Test(timeout = 60_000)
    fun aDrag_stopsHalfAViewPastTheSheetEdge() = runBlocking {
        val sheetWidth = A5_SHEET.width
        val viewWidth = sheetWidth / 2
        val page = open(A5_SHEET, viewWidth, viewHeight = 400)
        onMain { page.updateScroll(page.currentPageId, Offset(sheetWidth * 2f, 30f)) }
        assertEquals(PageViewport(Offset(sheetWidth - viewWidth / 2f, 30f)), page.viewport.value)
    }

    /**
     * A pinch reuses the screen bitmap: the reused pixels must land where the new viewport shows
     * them, and what they leave uncovered must be drawn (an undrawn pixel shows as black). The page
     * point under the fingers stays under them, on a narrow sheet too, where the scroll is negative.
     */
    @Test(timeout = 60_000)
    fun aPinchOnANarrowSheet_keepsThePointUnderTheFingers_andTheScreenWhole() = runBlocking {
        val sheetWidth = A6_SHEET.width.toFloat()
        val viewWidth = (sheetWidth * 2f).toInt()
        val viewHeight = (viewWidth * 0.6f).toInt()
        val page = open(A6_SHEET, viewWidth, viewHeight)
        val previousSettings = GlobalAppSettings.current
        GlobalAppSettings.update(previousSettings.copy(continuousZoom = false))
        try {
            // Off-centre, as fingers rarely pinch around the middle of the screen.
            val pivot = Offset(viewWidth * 0.7f, viewHeight * 0.4f)
            for ((delta, zoom) in listOf(-0.5f to 0.75f, 0.5f to 1f)) {
                val underFingers = page.scroll + pivot / page.zoom
                onMain { page.updateZoom(page.currentPageId, delta, pivot) }
                assertEquals(zoom, page.zoom, 0.001f)
                assertEquals("x under the fingers at $zoom", underFingers.x, (page.scroll + pivot / zoom).x, 0.01f)
                assertEquals("y under the fingers at $zoom", underFingers.y, (page.scroll + pivot / zoom).y, 0.01f)
                assertEveryScreenPixelDrawn(page, "at zoom $zoom")
            }
        } finally {
            GlobalAppSettings.update(previousSettings)
        }
    }

    /** Every redraw paints its area opaque, so a pixel that is not opaque was never drawn. */
    private fun assertEveryScreenPixelDrawn(page: PageView, situation: String) {
        val bitmap = page.windowedBitmap
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            val x = row.indexOfFirst { Color.alpha(it) != 255 }
            assertTrue("screen pixel ($x, $y) was never drawn $situation", x < 0)
        }
    }

    /**
     * A resize drops the zoom, fits the sheet in the new width, and saves
     * the scroll it fits: the record says where the sheet sits now, not where it sat before.
     */
    @Test(timeout = 60_000)
    fun aResize_dropsTheZoom_andSavesTheScrollItFits() = runBlocking {
        val sheetWidth = A5_SHEET.width.toFloat()
        val page = open(A5_SHEET, viewWidth = (sheetWidth / 2f).toInt(), viewHeight = 400)
        onMain { page.updateScroll(page.currentPageId, Offset(sheetWidth * 2f, 60f)) }
        onMain { page.simpleUpdateZoom(page.currentPageId, -0.5f) }
        assertTrue("the page is zoomed", page.zoom != 1f)

        val newWidth = (sheetWidth * 2f).toInt()
        onMain { page.updateDimensions(newWidth, 600) }

        val centred = -(newWidth - sheetWidth) / 2f
        assertEquals(PageViewport(Offset(centred, page.scroll.y)), page.viewport.value)
        assertEquals(AppResult.Success(Unit), views.data.pageSaver.saveAll())
        assertEquals(centred.roundToInt(), views.data.db.pageDao().getById(page.currentPageId)!!.positionX)
    }

    /** A page left before a resize comes back unzoomed and fitted to the new width too. */
    @Test(timeout = 60_000)
    fun aPageLeftBeforeAResize_comesBackUnzoomed() = runBlocking {
        val sheetWidth = A6_SHEET.width.toFloat()
        val page = open(A6_SHEET, viewWidth = (sheetWidth * 2f).toInt(), viewHeight = 700)
        val firstPageId = page.currentPageId
        val otherPageId = views.seedPage(A6_SHEET)
        views.show(page, otherPageId)
        onMain { page.simpleUpdateZoom(otherPageId, -0.5f) }
        assertTrue("the other page is zoomed", page.zoom != 1f)
        views.show(page, firstPageId)

        val newWidth = (sheetWidth * 1.2f).toInt()
        onMain { page.updateDimensions(newWidth, 900) }
        views.show(page, otherPageId)
        assertEquals(1f, page.zoom, 0.001f)
        assertEquals(-(newWidth - sheetWidth) / 2f, page.scroll.x, 0.01f)
    }

    /**
     * Page content shows on the sheet only. A stroke beside, above or below a sheet, moved there or
     * written on another device, is kept in the page but not drawn over the strips.
     */
    @Test(timeout = 60_000)
    fun aStrokeOffTheSheet_isNotDrawnOverTheStrips() = runBlocking {
        val sheet = A6_SHEET
        val pageId = views.seedPage(sheet)
        views.data.db.strokeDao().create(
            listOf(
                testStroke(pageId, x = 100f, y = 100f),
                testStroke(pageId, x = sheet.width + 50f, y = 100f),
                testStroke(pageId, x = 100f, y = sheet.height + 50f),
                testStroke(pageId, x = 100f, y = -50f),
            ),
        )
        // Wider and taller than the sheet, so there are strips on every side of it.
        val viewWidth = sheet.width * 2
        val viewHeight = sheet.height + 150
        val page = views.open(pageId, viewWidth, viewHeight)
        onMain { page.drawAreaScreenCoordinates(Rect(0, 0, viewWidth, viewHeight)) }

        // At zoom 1 the sheet is centred at its own size.
        val left = (viewWidth - sheet.width) / 2
        val right = left + sheet.width
        val top = (viewHeight - sheet.height) / 2
        val bottom = top + sheet.height
        assertTrue("the stroke on the sheet is drawn", inkIn(page, Rect(left, top, right, bottom)))
        assertFalse("the one beside it is not", inkIn(page, Rect(right + 3, 0, viewWidth, viewHeight)))
        assertFalse("the one above it is not", inkIn(page, Rect(0, 0, viewWidth, top - 3)))
        assertFalse("the one below it is not", inkIn(page, Rect(0, bottom + 3, viewWidth, viewHeight)))
    }

    /**
     * The strips are fixed to the screen: after a scroll or a pinch, which move the screen bitmap,
     * they look as a full redraw at the same viewport draws them.
     */
    @Test(timeout = 60_000)
    fun theStrips_stayPut_throughAScrollAndAPinch() = runBlocking {
        val viewWidth = A6_SHEET.width * 2
        val page = open(A6_SHEET, viewWidth, 700)
        val previousSettings = GlobalAppSettings.current
        GlobalAppSettings.update(previousSettings.copy(continuousZoom = false))
        try {
            val steps = listOf<suspend () -> Unit>(
                { page.updateScroll(page.currentPageId, Offset(0f, 4f)) },
                { page.updateZoom(page.currentPageId, -0.5f, null) },
            )
            for (step in steps) {
                onMain { step() }
                val moved = offSheet(page)
                onMain { page.drawAreaScreenCoordinates(Rect(0, 0, viewWidth, 700)) }
                assertArrayEquals("at zoom ${page.zoom}", offSheet(page), moved)
            }
        } finally {
            GlobalAppSettings.update(previousSettings)
        }
    }

    /** The pixels of [page]'s screen off the sheet: its left strip and the strips above and below, short of the edge lines. */
    private fun offSheet(page: PageView): IntArray {
        val area = page.sheetArea
        val bitmap = page.windowedBitmap
        val regions = listOf(
            Rect(0, 0, area.left - 2, bitmap.height),
            Rect(area.left, 0, area.right, area.top - 2),
            Rect(area.left, area.bottom + 2, area.right, bitmap.height),
        ).filter { it.width() > 0 && it.height() > 0 }
        return regions.flatMap { r ->
            IntArray(r.width() * r.height()).also { bitmap.getPixels(it, 0, r.width(), r.left, r.top, r.width(), r.height()) }.toList()
        }.toIntArray()
    }

    /** Whether [page]'s screen has dark ink (not the strips' grey dots) in [area]. */
    private fun inkIn(page: PageView, area: Rect): Boolean {
        val bitmap = page.windowedBitmap
        val row = IntArray(area.width())
        return (area.top until area.bottom).any { y ->
            bitmap.getPixels(row, 0, row.size, area.left, y, row.size, 1)
            row.any { Color.red(it) < 60 && Color.green(it) < 60 && Color.blue(it) < 60 }
        }
    }
}
