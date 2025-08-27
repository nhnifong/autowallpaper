package com.neufangled.autowallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class CropOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cropPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val dimPaint = Paint().apply {
        color = Color.argb(120, 0, 0, 0) // Semi-transparent black
        style = Paint.Style.FILL
    }

    private var imageBitmap: Bitmap? = null
    private var imageBounds = RectF() // The bounds of the image within the ImageView
    private var displayedImageRect = RectF() // The actual displayed rect of the image after scaling
    private var imageView: ImageView? = null


    // The actual crop rectangle in image coordinates
    private var cropRect = RectF(100f, 100f, 300f, 300f) // Initial default

    private var carDisplayAspectRatio: Float? = null // width / height

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // These are the bounds of the actual image bitmap, not the ImageView
    private var actualImageWidth = 0
    private var actualImageHeight = 0

    fun setImageView(iv: ImageView) {
        imageView = iv
    }


    fun setImageBitmap(bitmap: Bitmap) {
        imageBitmap = bitmap
        actualImageWidth = bitmap.width
        actualImageHeight = bitmap.height
        calculateDisplayedImageRect()
        initializeCropRect()
        invalidate()
    }

    private fun calculateDisplayedImageRect() {
        val iv = imageView ?: return
        if (actualImageWidth == 0 || actualImageHeight == 0) return

        val viewWidth = iv.width.toFloat()
        val viewHeight = iv.height.toFloat()
        val imageAspect = actualImageWidth.toFloat() / actualImageHeight.toFloat()
        val viewAspect = viewWidth / viewHeight

        val scale: Float
        var newWidth: Float
        var newHeight: Float
        var xOffset = 0f
        var yOffset = 0f

        if (imageAspect > viewAspect) { // Image is wider than view (letterboxed top/bottom)
            scale = viewWidth / actualImageWidth
            newWidth = viewWidth
            newHeight = actualImageHeight * scale
            yOffset = (viewHeight - newHeight) / 2
        } else { // Image is taller than view (pillarboxed left/right)
            scale = viewHeight / actualImageHeight
            newHeight = viewHeight
            newWidth = actualImageWidth * scale
            xOffset = (viewWidth - newWidth) / 2
        }
        displayedImageRect.set(xOffset, yOffset, xOffset + newWidth, yOffset + newHeight)
        imageBounds.set(0f, 0f, newWidth, newHeight) // cropRect will be relative to this
    }


    fun setCarDisplayAspectRatio(aspectRatio: Float) {
        this.carDisplayAspectRatio = aspectRatio
        initializeCropRect()
        invalidate()
    }

    private fun initializeCropRect() {
        if (actualImageWidth == 0 || actualImageHeight == 0 || carDisplayAspectRatio == null) return
        calculateDisplayedImageRect() // Ensure displayedImageRect is up-to-date

        val carAR = carDisplayAspectRatio ?: return

        var rectWidth: Float
        var rectHeight: Float

        // Calculate crop rectangle dimensions based on the *displayed image* aspect ratio vs car aspect ratio
        val displayedImageAR = displayedImageRect.width() / displayedImageRect.height()

        if (carAR > displayedImageAR) {
            // Car display is wider than the (potentially letterboxed/pillarboxed) image space
            // Crop rect should take full width of displayed image, height adjusted
            rectWidth = displayedImageRect.width()
            rectHeight = rectWidth / carAR
            if (rectHeight > displayedImageRect.height()) { //
                rectHeight = displayedImageRect.height()
                rectWidth = rectHeight * carAR
            }
        } else {
            // Car display is taller or same aspect as the image space
            // Crop rect should take full height of displayed image, width adjusted
            rectHeight = displayedImageRect.height()
            rectWidth = rectHeight * carAR
             if (rectWidth > displayedImageRect.width()) {
                rectWidth = displayedImageRect.width()
                rectHeight = rectWidth / carAR
            }
        }

        // Center it on the displayed image
        val left = displayedImageRect.left + (displayedImageRect.width() - rectWidth) / 2
        val top = displayedImageRect.top + (displayedImageRect.height() - rectHeight) / 2
        cropRect.set(left, top, left + rectWidth, top + rectHeight)
        invalidate()
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (actualImageWidth == 0 || actualImageHeight == 0) return

        // Draw dimming effect outside cropRect but within displayedImageRect
        // Top
        canvas.drawRect(displayedImageRect.left, displayedImageRect.top, displayedImageRect.right, cropRect.top, dimPaint)
        // Bottom
        canvas.drawRect(displayedImageRect.left, cropRect.bottom, displayedImageRect.right, displayedImageRect.bottom, dimPaint)
        // Left
        canvas.drawRect(displayedImageRect.left, cropRect.top, cropRect.left, cropRect.bottom, dimPaint)
        // Right
        canvas.drawRect(cropRect.right, cropRect.top, displayedImageRect.right, cropRect.bottom, dimPaint)

        // Draw the crop rectangle
        canvas.drawRect(cropRect, cropPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val carAR = carDisplayAspectRatio ?: return false // Don't handle if no aspect ratio

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (cropRect.contains(event.x, event.y)) {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    return true // Consume event if touch is inside rect
                }
                return false // Don't consume if touch is outside
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY

                val newLeft = cropRect.left + dx
                val newTop = cropRect.top + dy
                var newRight = newLeft + cropRect.width()
                var newBottom = newTop + cropRect.height()

                // Ensure cropRect stays within the displayedImageRect bounds
                val finalNewLeft = max(displayedImageRect.left, min(newLeft, displayedImageRect.right - cropRect.width()))
                val finalNewTop = max(displayedImageRect.top, min(newTop, displayedImageRect.bottom - cropRect.height()))
                
                newRight = finalNewLeft + cropRect.width()
                newBottom = finalNewTop + cropRect.height()

                cropRect.set(finalNewLeft, finalNewTop, newRight, newBottom)

                lastTouchX = event.x
                lastTouchY = event.y
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // This needs to convert the cropRect (in view coordinates, relative to displayed image)
    // to actual image pixel coordinates.
    fun getCroppedRect(): Rect? {
        if (actualImageWidth == 0 || actualImageHeight == 0) return null
        val iv = imageView ?: return null

        // Transformation matrix from ImageView to actual bitmap
        val matrix = Matrix()
        iv.imageMatrix.invert(matrix) // Get the inverse matrix

        // Create a RectF for the crop rectangle in view coordinates
        val viewCropRect = RectF(cropRect)

        // Map this rectangle using the inverse matrix
        // This is tricky because cropRect is relative to the *displayed* image, not the *view* if there are offsets.
        // We need to transform cropRect (which is in the coordinate system of the ImageView's canvas,
        // specifically aligned with how the image is displayed via fitCenter) to the original
        // bitmap's coordinate system.

        // displayedImageRect gives us the location and size of the image as it's drawn by fitCenter.
        // cropRect is currently in the same coordinate system as displayedImageRect (i.e., view coordinates).

        // Calculate scale factor used by fitCenter
        val viewWidth = iv.width.toFloat()
        val viewHeight = iv.height.toFloat()
        val imageAspect = actualImageWidth.toFloat() / actualImageHeight.toFloat()
        val viewAspect = viewWidth / viewHeight

        val scaleFactor: Float
        if (imageAspect > viewAspect) { // Image wider than view, scaled to fit width
            scaleFactor = actualImageWidth / viewWidth
        } else { // Image taller than view, scaled to fit height
            scaleFactor = actualImageHeight / viewHeight
        }
        
        // Adjust cropRect coordinates to be relative to the top-left of the scaled image,
        // then scale them up to original image dimensions.
        val leftInBitmap = ((cropRect.left - displayedImageRect.left) * scaleFactor).roundToInt()
        val topInBitmap = ((cropRect.top - displayedImageRect.top) * scaleFactor).roundToInt()
        val rightInBitmap = ((cropRect.right - displayedImageRect.left) * scaleFactor).roundToInt()
        val bottomInBitmap = ((cropRect.bottom - displayedImageRect.top) * scaleFactor).roundToInt()

        // Clamp values to be within actual image dimensions
        val finalLeft = max(0, leftInBitmap)
        val finalTop = max(0, topInBitmap)
        val finalRight = min(actualImageWidth, rightInBitmap)
        val finalBottom = min(actualImageHeight, bottomInBitmap)
        
        if (finalLeft >= finalRight || finalTop >= finalBottom) return null


        return Rect(finalLeft, finalTop, finalRight, finalBottom)
    }
}
