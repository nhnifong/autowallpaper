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
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.autowallpaper.R // Import the R class

/**
 * This screen uses the NavigationTemplate to get a raw Surface to draw on,
 * bypassing the normal image size restrictions for a high-resolution display.
 *
 * This version uses the correct AppManager.setSurfaceCallback API and cycles images.
 */
class ImageDisplayScreen(carContext: CarContext) : Screen(carContext) {

    private var surface: Surface? = null
    private var fullScreenRect: Rect? = null
    private var currentImageIndex = 0
    private val imageResources: List<Int>

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
            drawBitmapToSurface()
        }

        override fun onStableAreaChanged(stableArea: Rect) {
            drawBitmapToSurface()
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            surface = null
        }
    }

    init {
        // Dynamically find all image resources named image1, image2, etc., up to image10.
        imageResources = (1..10).mapNotNull { i ->
            val resourceId = carContext.resources.getIdentifier("image$i", "drawable", carContext.packageName)
            if (resourceId != 0) resourceId else null
        }

        // Use a lifecycle observer to correctly manage the SurfaceCallback.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
            }

            override fun onPause(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
                surface = null // Surface is no longer valid.
            }
        })
    }

    override fun onGetTemplate(): Template {
        // Create an action to cycle through the images.
        val cycleImageAction = Action.Builder()
            .setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(
                        carContext,
                        R.drawable.icon
                    )
                ).build()
            )
            .setOnClickListener {
                if (imageResources.isNotEmpty()) {
                    // Move to the next image, looping back to the start if at the end.
                    currentImageIndex = (currentImageIndex + 1) % imageResources.size
                    // Redraw the surface with the new image.
                    drawBitmapToSurface()
                }
            }
            .build()

        val actionStrip = ActionStrip.Builder()
            .addAction(cycleImageAction)
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .build()
    }

    private fun drawBitmapToSurface() {
        val localSurface = surface ?: return
        val destinationRect = fullScreenRect ?: return
        if (!localSurface.isValid || imageResources.isEmpty()) {
            return // Don't draw if there's no surface or no images.
        }

        val canvas: Canvas = localSurface.lockCanvas(null) ?: return

        try {
            // Load the current high-resolution image from drawables.
            val bitmap = BitmapFactory.decodeResource(carContext.resources, imageResources[currentImageIndex])

            val sourceRect = Rect(0, 0, bitmap.width, bitmap.height)

            // Draw the bitmap to the canvas, scaling it to fit the full screen.
            canvas.drawBitmap(bitmap, sourceRect, destinationRect, null)

            // Prepare and draw the resolution text on top of the image.
//            val resolutionText = "${destinationRect.width()} x ${destinationRect.height()}"
//            val textX = destinationRect.centerX().toFloat()
//            val textY = destinationRect.centerY().toFloat()
//            canvas.drawText(resolutionText, textX, textY, textPaint)

        } finally {
            localSurface.unlockCanvasAndPost(canvas)
        }
    }
}
