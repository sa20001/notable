package com.ethran.notable.editor

import androidx.compose.ui.geometry.Offset
import com.ethran.notable.data.model.SheetSize
import kotlin.math.ceil

/*
 * Where a page with a sheet sits in a view [viewWidth] pixels wide. A sheet narrower than the view
 * is centred, with a strip on each side; a wider one is kept inside the view. A page without a
 * sheet (null) is an infinite canvas: it only never goes left of or above its origin.
 */

/**
 * The width of each strip beside [sheet], drawn at [zoom] and centred in a view [viewWidth] wide;
 * 0 when the sheet fills the view or the page has no sheet. Rounded up, so the strips cover all
 * of the view outside the sheet.
 */
fun stripWidthFor(sheet: SheetSize?, viewWidth: Int, zoom: Float): Int {
    if (sheet == null) return 0
    val sheetWidth = sheet.width * zoom
    // Less than half a pixel of slack is float rounding of a fit, not a strip.
    if (sheetWidth > viewWidth - 0.5f) return 0
    return ceil((viewWidth - sheetWidth) / 2).toInt()
}

/**
 * The horizontal scroll that shows [sheet] at [zoom]: centred (negative) when narrower than the
 * view, otherwise [scrollX] kept between the sheet's edges.
 */
fun fitScrollX(sheet: SheetSize?, viewWidth: Int, scrollX: Float, zoom: Float): Float {
    if (sheet == null) return scrollX.coerceAtLeast(0f)
    val slack = sheet.width - viewWidth / zoom
    return if (slack < 0f) slack / 2f else scrollX.coerceIn(0f, slack)
}

/** A page's viewport when a view [viewWidth] x [viewHeight] left it. */
data class LeftViewport(val viewport: PageViewport, val viewWidth: Int, val viewHeight: Int)

/**
 * The viewport a page with [sheet] opens at in a view [viewWidth] x [viewHeight]: the one it was
 * [left] at, if left at this size; otherwise its scroll — [left]'s, or the [saved] one — at [zoom],
 * since a zoom chosen at another size fits no page at this one. Fitted to the sheet.
 */
fun openingViewport(
    left: LeftViewport?,
    saved: Offset,
    zoom: Float,
    sheet: SheetSize?,
    viewWidth: Int,
    viewHeight: Int,
): PageViewport {
    val viewport = when {
        left == null -> PageViewport(saved, zoom)
        left.viewWidth == viewWidth && left.viewHeight == viewHeight -> left.viewport
        else -> left.viewport.copy(zoom = zoom)
    }
    return fittedToSheet(viewport, sheet, viewWidth)
}

/** [viewport] moved to where a view [viewWidth] wide may show a page with [sheet]. */
fun fittedToSheet(viewport: PageViewport, sheet: SheetSize?, viewWidth: Int): PageViewport =
    viewport.copy(
        scroll = Offset(
            fitScrollX(sheet, viewWidth, viewport.scroll.x, viewport.zoom),
            viewport.scroll.y.coerceAtLeast(0f),
        ),
    )
