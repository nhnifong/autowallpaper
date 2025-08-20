package com.example.autowallpaper

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

import com.example.autowallpaper.R

/**
 * This screen uses the NavigationTemplate to get a raw Surface to draw on,
 * bypassing the normal image size restrictions for a high-resolution display.
 *
 * This version uses the correct AppManager.setSurfaceCallback API.
 */
class ImageDisplayScreen(carContext: CarContext) : Screen(carContext) {

    private var surface: Surface? = null
    // This will now store the full screen dimensions, not just the "safe" area.
    private var fullScreenRect: Rect? = null
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        setShadowLayer(5f, 2f, 2f, Color.BLACK) // Add a shadow for readability
    }


    // The SurfaceCallback handles all surface-related events.
    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            surface = surfaceContainer.surface
            // Use the full dimensions of the surface, ignoring safe areas.
            fullScreenRect = Rect(0, 0, surfaceContainer.width, surfaceContainer.height)
            drawBitmapToSurface()
        }

        override fun onVisibleAreaChanged(visibleArea: Rect) {
            // We are intentionally ignoring the safe "visibleArea" to draw fullscreen.
            // Redraw in case the surface itself was resized.
            drawBitmapToSurface()
        }

        override fun onStableAreaChanged(stableArea: Rect) {
            // We are also ignoring the "stableArea".
            drawBitmapToSurface()
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            // The surface is gone. Nullify it.
            surface = null
        }
    }

    init {
        // Use a lifecycle observer to correctly manage the SurfaceCallback.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                // When the screen is resumed, register the callback with the AppManager.
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
            }

            override fun onPause(owner: LifecycleOwner) {
                // When the screen is paused, unregister the callback to prevent leaks.
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
                surface = null // Surface is no longer valid.
            }
        })
    }

    override fun onGetTemplate(): Template {
        // The NavigationTemplate can be built without any extra elements.
        // The crash you saw before was likely due to another issue that is now resolved.
        return NavigationTemplate.Builder()
            .build()
    }

    private fun drawBitmapToSurface() {
        // Ensure we have a valid surface and a drawing area.
        val localSurface = surface ?: return
        val destinationRect = fullScreenRect ?: return
        if (!localSurface.isValid) {
            return // Don't draw on an invalid surface
        }

        // Lock the canvas to get a drawing surface.
        val canvas: Canvas = localSurface.lockCanvas(null) ?: return

        try {
            // Load your high-resolution image from drawables.
            val bitmap = BitmapFactory.decodeResource(carContext.resources, R.drawable.image)

            // Define the source (the whole bitmap) and destination (the full screen).
            val sourceRect = Rect(0, 0, bitmap.width, bitmap.height)

            // Draw the bitmap to the canvas, scaling it to fit the full screen.
            canvas.drawBitmap(bitmap, sourceRect, destinationRect, null)

            // Prepare and draw the resolution text on top of the image.
            val resolutionText = "${destinationRect.width()} x ${destinationRect.height()}"
            val textX = destinationRect.centerX().toFloat()
            val textY = destinationRect.centerY().toFloat()
            canvas.drawText(resolutionText, textX, textY, textPaint)

        } finally {
            // Unlock the canvas and post the new content to the screen.
            localSurface.unlockCanvasAndPost(canvas)
        }
    }
}
