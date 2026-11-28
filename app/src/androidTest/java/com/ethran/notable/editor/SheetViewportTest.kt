package com.ethran.notable.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
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
import com.ethran.notable.utils.AppResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun aDrag_stopsAtTheSheetEdge_andAnInfinitePageAtItsLeftEdge() = runBlocking {
        val sheetWidth = A5_SHEET.width
        val wide = open(A5_SHEET, viewWidth = sheetWidth / 2, viewHeight = 400)
        onMain { wide.updateScroll(wide.currentPageId, Offset(sheetWidth * 2f, 30f)) }
        assertEquals(PageViewport(Offset((sheetWidth - sheetWidth / 2).toFloat(), 30f)), wide.viewport.value)

        val infinite = open(null, viewWidth = 800, viewHeight = 600)
        onMain { infinite.updateScroll(infinite.currentPageId, Offset(50f, 0f)) }
        onMain { infinite.updateScroll(infinite.currentPageId, Offset(-500f, 0f)) }
        assertEquals(PageViewport(), infinite.viewport.value)
    }

    /**
     * A pinch reuses the screen bitmap: the reused pixels must land where the new viewport shows
     * them, and what they leave uncovered must be drawn (an undrawn pixel shows as black). On a
     * narrow sheet the scroll x is negative and re-centred at every zoom.
     */
    @Test(timeout = 60_000)
    fun aPinchOnANarrowSheet_keepsItCentred_andTheScreenWhole() = runBlocking {
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
                onMain { page.updateZoom(page.currentPageId, delta, pivot) }
                assertEquals(zoom, page.zoom, 0.001f)
                assertEquals("centred at $zoom", -(viewWidth / zoom - sheetWidth) / 2f, page.scroll.x, 0.01f)
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
     * A resize drops the zoom, on the screen canvas too, fits the sheet in the new width, and saves
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

        @Suppress("DEPRECATION") // Canvas.getMatrix() is the only public way to read the transform.
        val values = FloatArray(9).also { page.windowedCanvas.matrix.getValues(it) }
        assertEquals(1f, values[Matrix.MSCALE_X], 0.001f)
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

    /** The scroll is saved on both axes, and a new view of the page opens there. */
    @Test(timeout = 60_000)
    fun aReopenedPage_isBackWhereItWasLeft_onBothAxes() = runBlocking {
        val sheetWidth = A5_SHEET.width
        val page = open(A5_SHEET, viewWidth = sheetWidth / 2, viewHeight = 400)
        val pageId = page.currentPageId
        onMain { page.updateScroll(pageId, Offset(sheetWidth * 2f, 120f)) }
        val left = page.viewport.value
        assertEquals(AppResult.Success(Unit), views.data.pageSaver.saveAll())

        page.disposeOldPage()
        assertEquals(left, views.open(pageId, page.viewWidth, page.viewHeight).viewport.value)
    }
}
