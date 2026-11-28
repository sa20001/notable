package com.ethran.notable.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import com.ethran.notable.data.model.SheetSize
import kotlin.math.ceil
import kotlin.math.floor

/*
 * Where a page with a sheet sits in a view [viewWidth] x [viewHeight] pixels. The page is its sheet.
 * Panning and zooming may take the view past the sheet's edges until half of it shows the view
 * around the sheet, on either axis ([fittedToSheet]). Opening a page, a resize and a reset frame the
 * sheet instead ([framedOnSheet]): centred along an axis where it is shorter than the view, within
 * its edges where it is longer. A page without a sheet (null) is an infinite canvas: it only never
 * goes left of or above its origin.
 */

/**
 * Where [sheet] lies on a view [viewWidth] x [viewHeight] showing it at [viewport], cut to the view:
 * the whole view when the sheet covers it or the page has no sheet. Rounded towards the sheet, so
 * the strips around it cover all of the view outside it.
 */
fun sheetAreaFor(sheet: SheetSize?, viewport: PageViewport, viewWidth: Int, viewHeight: Int): IntRect {
    if (sheet == null) return IntRect(0, 0, viewWidth, viewHeight)
    val (scroll, zoom) = viewport
    val left = startOnView(-scroll.x * zoom)
    val top = startOnView(-scroll.y * zoom)
    val right = endOnView((sheet.width - scroll.x) * zoom, viewWidth)
    val bottom = endOnView((sheet.height - scroll.y) * zoom, viewHeight)
    return IntRect(left, top, right.coerceAtLeast(left), bottom.coerceAtLeast(top))
}

// Less than half a pixel inside the view is float rounding of a framed sheet, not a strip.
private fun startOnView(at: Float): Int = if (at < 0.5f) 0 else ceil(at).toInt()
private fun endOnView(at: Float, view: Int): Int = if (at > view - 0.5f) view else floor(at).toInt()

/**
 * [viewport] moved to where panning and zooming may take a view [viewWidth] x [viewHeight] of a page
 * with [sheet]: no more than half the view past any of the sheet's edges.
 */
fun fittedToSheet(viewport: PageViewport, sheet: SheetSize?, viewWidth: Int, viewHeight: Int): PageViewport {
    fun fit(sheetLength: Int?, viewLength: Int, scroll: Float): Float {
        if (sheetLength == null) return scroll.coerceAtLeast(0f)
        val half = viewLength / viewport.zoom / 2
        return scroll.coerceIn(-half, sheetLength - half)
    }
    return viewport.copy(
        scroll = Offset(
            fit(sheet?.width, viewWidth, viewport.scroll.x),
            fit(sheet?.height, viewHeight, viewport.scroll.y),
        ),
    )
}

/**
 * [viewport] moved to frame [sheet] in a view [viewWidth] x [viewHeight]: centred (a negative scroll)
 * along an axis where the sheet is shorter than the view, kept within the sheet's edges where it is
 * longer.
 */
fun framedOnSheet(viewport: PageViewport, sheet: SheetSize?, viewWidth: Int, viewHeight: Int): PageViewport {
    fun frame(sheetLength: Int?, viewLength: Int, scroll: Float): Float {
        if (sheetLength == null) return scroll.coerceAtLeast(0f)
        val slack = sheetLength - viewLength / viewport.zoom
        return if (slack < 0f) slack / 2f else scroll.coerceIn(0f, slack)
    }
    return viewport.copy(
        scroll = Offset(
            frame(sheet?.width, viewWidth, viewport.scroll.x),
            frame(sheet?.height, viewHeight, viewport.scroll.y),
        ),
    )
}

/** A page's viewport when a view [viewWidth] x [viewHeight] left it. */
data class LeftViewport(val viewport: PageViewport, val viewWidth: Int, val viewHeight: Int)

/**
 * The viewport a page with [sheet] opens at in a view [viewWidth] x [viewHeight]: the one it was
 * [left] at, if left at this size; otherwise its scroll — [left]'s, or the [saved] one — at [zoom],
 * since a zoom chosen at another size fits no page at this one, framed on the sheet.
 */
fun openingViewport(
    left: LeftViewport?,
    saved: Offset,
    zoom: Float,
    sheet: SheetSize?,
    viewWidth: Int,
    viewHeight: Int,
): PageViewport =
    if (left != null && left.viewWidth == viewWidth && left.viewHeight == viewHeight) left.viewport
    else framedOnSheet(PageViewport(left?.viewport?.scroll ?: saved, zoom), sheet, viewWidth, viewHeight)
