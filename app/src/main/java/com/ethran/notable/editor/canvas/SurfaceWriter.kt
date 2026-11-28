package com.ethran.notable.editor.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import androidx.annotation.MainThread
import com.ethran.notable.data.datastore.GlobalAppSettings
import com.ethran.notable.data.model.SimplePointF
import com.ethran.notable.editor.EditorViewModel
import com.ethran.notable.editor.PageView
import com.ethran.notable.editor.drawing.selectPaint
import com.ethran.notable.editor.state.Mode
import com.ethran.notable.editor.utils.pointsToPath
import com.ethran.notable.utils.checkMainThread
import com.onyx.android.sdk.api.device.epd.EpdController
import io.shipbook.shipbooksdk.ShipBook

/**
 * The only code that locks and posts the canvas surface. Only [RawDrawingController] calls it; the
 * controller alone decides what is written and when.
 */
class SurfaceWriter(
    private val drawCanvas: DrawCanvas,
    private val page: PageView,
    private val viewModel: EditorViewModel,
) {
    private val log = ShipBook.getLogger("DrawCanvas")
    private val bitmapPaint = Paint()
    private val debugPostPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    /**
     * Locks the surface over [dirtyRect] (screen coordinates; null: all of it), draws [bitmap]
     * (null: the page, with the page-cut line in Select mode) there, and posts it. With [force],
     * the post is forced through to the panel while raw drawing still owns the screen. Returns
     * whether the requested content was drawn and posted (false when the lock, the draw or the
     * post failed).
     */
    @MainThread
    fun write(dirtyRect: Rect?, bitmap: Bitmap?, force: Boolean): Boolean {
        checkMainThread("SurfaceWriter.write")
        val zone = dirtyRect ?: Rect(0, 0, page.viewWidth, page.viewHeight)
        val source = bitmap ?: page.windowedBitmap
        val start = SystemClock.uptimeMillis()
        val canvas = lock(zone) ?: return false
        if (force) {
            // enablePost(0)+enablePost(1) forces the bitmap through to the EPD even while the
            // screen is frozen (a bare unlockCanvasAndPost is swallowed).
            EpdController.enablePost(0)
            EpdController.enablePost(1)
        }
        val drawn = draw(canvas, zone, source, pageCut = bitmap == null)
        val posted = post(canvas) // a locked canvas is always released, drawn or not
        // Let the EPD apply the pushed region.
        if (force) EpdController.resetViewUpdateMode(drawCanvas)
        val submitted = drawn && posted
        log.d(
            "write: page=${page.currentPageId}, rect=$zone, bitmap=${source.hashCode()}, " +
                "force=$force, submitted=$submitted, took ${SystemClock.uptimeMillis() - start} ms"
        )
        return submitted
    }

    /** Locks the surface over [zone]; null, logged, if it cannot be locked. */
    private fun lock(zone: Rect): Canvas? {
        val canvas = try {
            drawCanvas.holder.lockCanvas(zone)
        } catch (e: RuntimeException) {
            log.e("FAILED to lock canvas: rect=$zone", e)
            return null
        }
        if (canvas == null) log.e(
            "FAILED to lock canvas (surface invalid/destroyed or locked by another thread). " +
                "page=${page.currentPageId}, rect=$zone, " +
                "surfaceValid=${drawCanvas.holder.surface?.isValid}"
        )
        return canvas
    }

    /** Draws [source] over [zone], plus the page-cut line if [pageCut]; false if it threw. */
    private fun draw(canvas: Canvas, zone: Rect, source: Bitmap, pageCut: Boolean): Boolean =
        try {
            canvas.drawBitmap(source, zone, zone, bitmapPaint)
            if (pageCut) drawPageCut(canvas)
            // Debug mode: outline what this post covers, on the surface only (the page bitmap
            // stays clean), so the outline lasts until that area is posted again.
            if (GlobalAppSettings.current.debugMode) canvas.drawRect(zone, debugPostPaint)
            true
        } catch (e: RuntimeException) {
            log.w("Surface released or invalid during draw", e)
            false
        }

    /** Unlocks [canvas] and posts it; false if the surface was released meanwhile. */
    private fun post(canvas: Canvas): Boolean =
        try {
            drawCanvas.holder.unlockCanvasAndPost(canvas)
            true
        } catch (e: IllegalStateException) {
            log.w("Surface released during unlock", e)
            false
        }

    /** In Select mode, the selection's page-cut line, in screen coordinates. */
    private fun drawPageCut(canvas: Canvas) {
        if (viewModel.toolbarState.value.mode != Mode.Select) return
        val cutPoints = viewModel.selectionState.firstPageCut ?: return
        log.i("render cut")
        val path = pointsToPath(cutPoints.map { SimplePointF(it.x - page.scroll.x, it.y - page.scroll.y) })
        canvas.drawPath(path, selectPaint)
    }
}

fun Rect.toScreenArea() = ScreenArea(left, top, right, bottom)

fun ScreenArea.toRect() = Rect(left, top, right, bottom)
