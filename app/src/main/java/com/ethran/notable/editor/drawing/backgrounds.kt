package com.ethran.notable.editor.drawing

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import com.ethran.notable.SCREEN_HEIGHT
import com.ethran.notable.SCREEN_WIDTH
import com.ethran.notable.data.datastore.GlobalAppSettings
import com.ethran.notable.data.model.BackgroundType
import com.ethran.notable.data.model.SheetSize
import com.ethran.notable.editor.utils.scaleRect
import com.onyx.android.sdk.extension.copy
import io.shipbook.shipbooksdk.ShipBook
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val log = ShipBook.getLogger("BackgroundsLog")

const val padding = 0
const val lineHeight = 80
const val dotSize = 6f
const val hexVerticalCount = 26


// Default paint for lines, dots, etc
private val defaultPaint = Paint().apply {
    this.color = Color.GRAY
    this.strokeWidth = 1f
}

// The strips beside a sheet: white, dotted in grey on a grid fixed to the screen, so separately
// drawn parts line up. One tile, repeated by the shader.
private const val STRIP_DOT_SPACING = 18
private val stripPaint = Paint().apply {
    val tile = Bitmap.createBitmap(STRIP_DOT_SPACING, STRIP_DOT_SPACING, Bitmap.Config.ARGB_8888)
    Canvas(tile).apply {
        drawColor(Color.WHITE)
        val centre = STRIP_DOT_SPACING / 2f
        drawCircle(centre, centre, 1.25f, Paint().apply { color = Color.rgb(120, 120, 120) })
    }
    shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
}

// For drawing Hexagons
private val defaultPaintStroke = defaultPaint.copy().apply { this.style = Paint.Style.STROKE }
private val marginPaint = Paint().apply {
    this.color = Color.MAGENTA
    this.strokeWidth = 2f
}
private val paginationLinePaint = Paint().apply {
    color = Color.RED
    strokeWidth = 4f
    pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
}
private val pageLabelPaint = Paint().apply {
    color = Color.BLACK
    textSize = 24f
    isAntiAlias = true
}

fun drawLinedBg(canvas: Canvas, scroll: Offset, scale: Float) {
    val height = (canvas.height / scale).toInt()
    val width = (canvas.width / scale).toInt()

    // white bg
    canvas.drawColor(Color.WHITE)


    val offset = IntOffset(lineHeight, lineHeight) - IntOffset(
        scroll.x.toInt() % lineHeight, scroll.y.toInt() % lineHeight
    )

    for (y in 0..height step lineHeight) {
        canvas.drawLine(
            padding.toFloat(),
            y.toFloat() + offset.y,
            (width - padding).toFloat(),
            y.toFloat() + offset.y,
            defaultPaint
        )
    }
}

fun drawDottedBg(canvas: Canvas, scroll: Offset, scale: Float) {
    val height = (canvas.height / scale).toInt()
    val width = (canvas.width / scale).toInt()
    // white bg
    canvas.drawColor(Color.WHITE)


    // dots
    val offset = IntOffset(lineHeight, lineHeight) - IntOffset(
        scroll.x.toInt() % lineHeight, scroll.y.toInt() % lineHeight
    )

    for (y in 0..height step lineHeight) {
        for (x in padding..width - padding step lineHeight) {
            canvas.drawOval(
                x.toFloat() + offset.x - dotSize / 2,
                y.toFloat() + offset.y - dotSize / 2,
                x.toFloat() + offset.x + dotSize / 2,
                y.toFloat() + offset.y + dotSize / 2,
                defaultPaint
            )
        }
    }

}

fun drawSquaredBg(canvas: Canvas, scroll: Offset, scale: Float) {
    val height = (canvas.height / scale).toInt()
    val width = (canvas.width / scale).toInt()

    // white bg
    canvas.drawColor(Color.WHITE)

    // paint

    val offset = IntOffset(lineHeight, lineHeight) - IntOffset(
        scroll.x.toInt() % lineHeight, scroll.y.toInt() % lineHeight
    )

    for (y in 0..height step lineHeight) {
        canvas.drawLine(
            padding.toFloat(),
            y.toFloat() + offset.y,
            (width - padding).toFloat(),
            y.toFloat() + offset.y,
            defaultPaint
        )
    }

    for (x in padding..width - padding step lineHeight) {
        canvas.drawLine(
            x.toFloat() + offset.x,
            padding.toFloat(),
            x.toFloat() + offset.x,
            height.toFloat(),
            defaultPaint
        )
    }
}

fun drawHexedBg(canvas: Canvas, scroll: Offset, scale: Float) {
    val height = (canvas.height / scale)
    val width = (canvas.width / scale)

    // background
    canvas.drawColor(Color.WHITE)


    // https://www.redblobgames.com/grids/hexagons/#spacing
    val r = max(width, height) / (hexVerticalCount * 1.5f) * scale
    val hexHeight = r * 2
    val hexWidth = r * sqrt(3f)

    val rows = (height / hexVerticalCount).toInt()
    val cols = (width / hexWidth).toInt() + 1

    for (row in 0..rows) {
        val offsetX = if (row % 2 == 0) 0f else hexWidth / 2
        for (col in 0..cols) {
            val x = col * hexWidth + offsetX - scroll.x.mod(hexWidth) - hexWidth
            val y = row * hexHeight * 0.75f - scroll.y.mod(hexHeight * 1.5f)
            drawHexagon(canvas, x, y, r)
        }
    }
}

fun drawHexagon(canvas: Canvas, centerX: Float, centerY: Float, r: Float) {
    val path = Path()
    for (i in 0..5) {
        val angle = Math.toRadians((30 + 60 * i).toDouble())
        val x = (centerX + r * cos(angle)).toFloat()
        val y = (centerY + r * sin(angle)).toFloat()
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()
    canvas.drawPath(path, defaultPaintStroke)
}


fun drawTitleBox(canvas: Canvas) {

    // Draw label-like area in center
    val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    val borderPaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    // This might not be actual width in some situations
    // investigate it, in case of problems
    val canvasHeight = max(SCREEN_WIDTH, SCREEN_HEIGHT)
    val canvasWidth = min(SCREEN_WIDTH, SCREEN_HEIGHT)

    // Dimensions for the label box
    val labelWidth = canvasWidth * 0.8f
    val labelHeight = canvasHeight * 0.25f
    val left = (canvasWidth - labelWidth) / 2
    val top = (canvasHeight - labelHeight) / 2
    val right = left + labelWidth
    val bottom = top + labelHeight

    val rectF = RectF(left, top, right, bottom)
    val cornerRadius = 64f

    canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
    canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, borderPaint)
}


// Backgrounds are rendered oversampled (see loadBackgroundBitmap's resolutionModifier) and drawn
// downscaled onto the screen canvas. Bilinear filtering + dithering makes that downscale crisp
// instead of aliased, and the dither maps grey edges cleanly onto e-ink. Costs no extra memory.
private val bgBitmapPaint = Paint().apply {
    isFilterBitmap = true
    isAntiAlias = true
    isDither = true
}

fun drawBitmapToCanvas(
    canvas: Canvas, imageBitmap: Bitmap, scroll: Offset, scale: Float, repeat: Boolean
) {
    canvas.drawColor(Color.WHITE)
    val imageWidth = imageBitmap.width
    val imageHeight = imageBitmap.height


//    val canvasWidth = canvas.width
    val canvasHeight = canvas.height
    val widthOnCanvas = min(SCREEN_WIDTH, SCREEN_HEIGHT)

    val scaleFactor = widthOnCanvas.toFloat() / imageWidth
    val scaledHeight = (imageHeight * scaleFactor).toInt()

    // TODO: It's working, but its not nice -- do it in better style.
    // Draw the first image, considering the scroll offset
    val srcTop = Offset(
        (scroll.x / scaleFactor).coerceAtLeast(0f),
        ((scroll.y / scaleFactor) % imageHeight).coerceAtLeast(0f)
    )
    val rectOnImage = Rect(0, srcTop.y.toInt(), imageWidth, imageHeight)
    // Below the top of the view when the page starts lower, as a sheet centred down the view does.
    val topOnCanvas = (-scroll.y).coerceAtLeast(0f).toInt()
    val rectOnCanvas = Rect(
        -scroll.x.toInt(),
        topOnCanvas,
        widthOnCanvas - scroll.x.toInt(),
        topOnCanvas + ((imageHeight - srcTop.y) * scaleFactor).toInt()
    )

    var filledHeight = 0
    if (repeat || scroll.y < canvasHeight) {
        canvas.drawBitmap(imageBitmap, rectOnImage, rectOnCanvas, bgBitmapPaint)
        filledHeight = rectOnCanvas.bottom
    }
    // TODO: Should we also repeat horizontally?

    if (repeat) {
        var currentTop = filledHeight
        val srcRect = Rect(0, 0, imageWidth, imageHeight)
        while (currentTop < canvasHeight / scale) {

            val dstRect = Rect(
                -scroll.x.toInt(),
                currentTop,
                widthOnCanvas - scroll.x.toInt(),
                currentTop + scaledHeight
            )
            canvas.drawBitmap(imageBitmap, srcRect, dstRect, bgBitmapPaint)
            currentTop += scaledHeight
        }
    }
}

/**
 * The page's template or background image, as an export or a preview shows it. The canvas is
 * scaled by [scale], so its own width and height are in screen pixels.
 */
fun drawTemplate(
    canvas: Canvas,
    backgroundType: BackgroundType,
    background: String,
    scroll: Offset = Offset.Zero,
    resourceBitmap: Bitmap?,
    scale: Float = 1f,
    repeat: Boolean = false,    // for repeating image
) {
    when (backgroundType) {
        // draw native background for it we don't need a resource
        is BackgroundType.Native -> {
            when (background) {
                "blank" -> canvas.drawColor(Color.WHITE)
                "dotted" -> drawDottedBg(canvas, scroll, scale)
                "lined" -> drawLinedBg(canvas, scroll, scale)
                "squared" -> drawSquaredBg(canvas, scroll, scale)
                "hexed" -> drawHexedBg(canvas, scroll, scale)
                else -> {
                    // Reaching the Native branch with an unknown value usually means the *type*
                    // detection misclassified a non-native background (e.g. a .pdf) as Native, not
                    // that the string is merely unknown — surface the type so it's obvious.
                    throw IllegalArgumentException(
                        "Unknown native background '$background' (type=$backgroundType)"
                    )
                }
            }
        }

        else -> {
            if (resourceBitmap != null) {
                drawBitmapToCanvas(canvas, resourceBitmap, scroll, scale, repeat)
                if (backgroundType is BackgroundType.CoverImage) {
                    drawTitleBox(canvas)
                }
            } else {
                log.i("No resource provided to draw, maybe out of pages in pdf?")
                canvas.drawColor(Color.WHITE)
            }
        }
    }
}

/**
 * The background as the editor shows it: [drawTemplate], then what only the editor draws. A page
 * without a [sheet] gets the margin and, if enabled, the breaks of a paginated PDF export.
 */
fun drawBg(
    canvas: Canvas,
    backgroundType: BackgroundType,
    background: String,
    sheet: SheetSize?,
    scroll: Offset = Offset.Zero,
    resourceBitmap: Bitmap?,
    scale: Float = 1f,
    repeat: Boolean = false,    // for repeating image
    clipRect: Rect? = null,     // before the scaling
) {
    log.v("Loading the background")
    clipRect?.let {
        canvas.save()
        canvas.clipRect(scaleRect(it, scale))
    }
    drawTemplate(canvas, backgroundType, background, scroll, resourceBitmap, scale, repeat)
    if (sheet == null) {
        drawMargin(canvas, scroll, scale)
        if (GlobalAppSettings.current.visualizePdfPagination) {
            // The pages of a paginated export: A4, as wide as the screen's short side.
            val width = min(SCREEN_HEIGHT, SCREEN_WIDTH).toFloat()
            drawPageBreaks(canvas, scroll, scale, width, width * 297f / 210f)
        }
    }
    if (clipRect != null) {
        canvas.restore()
    }
}

/**
 * The view around a sheet lying at [sheet] in a view [viewWidth] x [viewHeight], in [area] (screen
 * pixels, on an unscaled [canvas]): dotted strips beside, above and below it, with a line along its
 * edges. Not page drawing: no page content shows there.
 */
fun drawStrips(canvas: Canvas, area: Rect, sheet: IntRect, viewWidth: Int, viewHeight: Int) {
    val strips = listOf(
        Rect(0, 0, sheet.left, viewHeight),
        Rect(sheet.right, 0, viewWidth, viewHeight),
        Rect(sheet.left, 0, sheet.right, sheet.top),
        Rect(sheet.left, sheet.bottom, sheet.right, viewHeight),
    )
    for (strip in strips) {
        if (strip.intersect(area)) canvas.drawRect(strip, stripPaint)
    }
    // The edges that border a strip, where they pass through the area.
    fun near(at: Int, from: Int, until: Int) = at >= from - 2 && at <= until + 2
    val top = max(area.top, sheet.top).toFloat()
    val bottom = min(area.bottom, sheet.bottom).toFloat()
    if (top < bottom) {
        for (x in listOf(sheet.left, sheet.right)) {
            if (x in 1 until viewWidth && near(x, area.left, area.right))
                canvas.drawLine(x.toFloat(), top, x.toFloat(), bottom, marginPaint)
        }
    }
    val left = max(area.left, sheet.left).toFloat()
    val right = min(area.right, sheet.right).toFloat()
    if (left < right) {
        for (y in listOf(sheet.top, sheet.bottom)) {
            if (y in 1 until viewHeight && near(y, area.top, area.bottom))
                canvas.drawLine(left, y.toFloat(), right, y.toFloat(), marginPaint)
        }
    }
}

// TODO: make sure it respects horizontal scroll
fun drawMargin(canvas: Canvas, scroll: Offset, scale: Float) {
    // in landscape orientation add margin to indicate what will be visible in vertical orientation.
    if (SCREEN_WIDTH > SCREEN_HEIGHT || scale < 1.0f || scroll.x > 1) {
        val margin = min(SCREEN_HEIGHT, SCREEN_WIDTH) - scroll.x
        // Draw vertical line with x= SCREEN_HEIGHT
        canvas.drawLine(
            margin, padding.toFloat(), margin, (SCREEN_HEIGHT / scale - padding), marginPaint
        )
    }
}

/** A labelled line every [pageHeight] page pixels down, [pageWidth] long from the page's left edge. */
private fun drawPageBreaks(
    canvas: Canvas,
    scroll: Offset,
    scale: Float,
    pageWidth: Float,
    pageHeight: Float,
) {
    if (pageHeight <= 0f) return
    // The first break below the top of the view, counted from 1.
    var pageNum = floor(scroll.y / pageHeight).toInt() + 1
    var yPos = pageNum * pageHeight - scroll.y
    while (yPos < canvas.height / scale) {
        canvas.drawLine(-scroll.x, yPos, pageWidth - scroll.x, yPos, paginationLinePaint)
        canvas.drawText("Subpage ${pageNum + 1}", 20f - scroll.x, yPos + 30f, pageLabelPaint)
        yPos += pageHeight
        pageNum++
    }
}
