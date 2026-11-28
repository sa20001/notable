package com.ethran.notable.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethran.notable.editor.EditorViewModel
import com.ethran.notable.editor.PageView
import com.ethran.notable.ui.convertDpToPixel
import kotlin.math.max

/**
 * Vertical scroll indicator (right side).
 * Shows the part of the sheet on the screen, or, on a page without one, the vertical scroll of
 * [PageView.viewport] against [PageView.contentHeight].
 */
@Composable
fun ScrollIndicator(viewModel: EditorViewModel, page: PageView) {
    val toolbarState by viewModel.toolbarState.collectAsStateWithLifecycle()
    val viewport by page.viewport.collectAsStateWithLifecycle()
    val contentHeight by page.contentHeight.collectAsStateWithLifecycle()
    BoxWithConstraints(
        modifier = Modifier
            .width(5.dp)
            .fillMaxHeight()
    ) {
        val viewportHeightPx = convertDpToPixel(this.maxHeight, LocalContext.current).toInt()
        val scrollY = viewport.scroll.y

        // Without a sheet, an approximation: contentHeight is the total content height (page
        // coordinates), and scrollY + viewportHeightPx keeps the indicator showing near the bottom.
        val bar = barAlong(
            page.sheet?.height, max(contentHeight, scrollY.toInt() + viewportHeightPx).toFloat(),
            viewportHeightPx, scrollY, viewport.zoom,
        ) ?: return@BoxWithConstraints
        val indicatorSizeDp = (bar.endInclusive - bar.start) * this.maxHeight.value
        val indicatorPositionDp = bar.start * this.maxHeight.value

        if (!toolbarState.isToolbarOpen) return@BoxWithConstraints

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(
                    y = indicatorPositionDp.dp
                )
                .background(Color.Black)
                .height(indicatorSizeDp.dp)
        )
    }
}

/**
 * Horizontal scroll indicator (bottom).
 * Shows the part of the sheet on the screen, or, on a page without one, the horizontal scroll of
 * [PageView.viewport].
 */
@Composable
fun HorizontalScrollIndicator(viewModel: EditorViewModel, page: PageView) {
    val toolbarState by viewModel.toolbarState.collectAsStateWithLifecycle()
    val viewport by page.viewport.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(1f))
        BoxWithConstraints(
            modifier = Modifier
                .height(5.dp)
                .fillMaxWidth()
        ) {
            val viewportWidthPx = convertDpToPixel(this.maxWidth, LocalContext.current).toInt()
            val scrollX = viewport.scroll.x

            // Without a sheet, an approximation: page.viewWidth is the total content width (page
            // coordinates), and scrollX + viewportWidthPx keeps the indicator showing near the edge.
            val bar = barAlong(
                page.sheet?.width, max(page.viewWidth, scrollX.toInt() + viewportWidthPx).toFloat(),
                viewportWidthPx, scrollX, viewport.zoom,
            ) ?: return@BoxWithConstraints
            val indicatorSizeDp = (bar.endInclusive - bar.start) * this.maxWidth.value
            val indicatorPositionDp = bar.start * this.maxWidth.value

            if (!toolbarState.isToolbarOpen) return@BoxWithConstraints

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .offset(
                        x = indicatorPositionDp.dp
                    )
                    .background(Color.Black)
                    .width(indicatorSizeDp.dp)
            )
        }
    }
}

/**
 * Which part of the page the view shows along one axis, as fractions of the track; null when it
 * shows all of it. On a page with a sheet [sheetLength] long, the part of the sheet on the screen,
 * from [scroll] over [viewLength] screen pixels at [zoom]; otherwise the view's [viewLength] from
 * [scroll] in [total].
 */
private fun barAlong(sheetLength: Int?, total: Float, viewLength: Int, scroll: Float, zoom: Float): ClosedFloatingPointRange<Float>? {
    val length = sheetLength?.toFloat() ?: total
    val from = if (sheetLength != null) scroll.coerceAtLeast(0f) else scroll
    val to = if (sheetLength != null) (scroll + viewLength / zoom).coerceAtMost(length) else scroll + viewLength
    if (from <= 0f && to >= length) return null
    return from / length..to / length
}
