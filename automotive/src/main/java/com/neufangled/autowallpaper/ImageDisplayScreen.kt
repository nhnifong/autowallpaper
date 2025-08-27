package com.neufangled.autowallpaper

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
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
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.neufangled.autowallpaper.R
import java.io.FileNotFoundException

// Data classes for storing wallpaper items with crop information
data class SerializableRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun toRect(): Rect = Rect(left, top, right, bottom)
}

data class WallpaperItem(val uriString: String, val cropRect: SerializableRect? = null)

class ImageDisplayScreen(carContext: CarContext) : Screen(carContext) {

    private var surface: Surface? = null
    private var fullScreenRect: Rect? = null
    private var currentImageIndex = 0
    private var wallpaperItems: List<WallpaperItem> = emptyList()
    private val gson = Gson()

    companion object {
        const val PREFS_NAME = "wallpaper_prefs"
        const val KEY_WALLPAPER_ITEMS = "wallpaper_items"
        const val KEY_CAR_DISPLAY_WIDTH = "car_display_width_default_car"
        const val KEY_CAR_DISPLAY_HEIGHT = "car_display_height_default_car"
        const val DEFAULT_CAR_ID = "default_car" // For now, only one car
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        setShadowLayer(5f, 2f, 2f, Color.BLACK)
    }

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            surface = surfaceContainer.surface
            fullScreenRect = Rect(0, 0, surfaceContainer.width, surfaceContainer.height)

            // Save car display dimensions
            val prefs = carContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt(KEY_CAR_DISPLAY_WIDTH, surfaceContainer.width)
                .putInt(KEY_CAR_DISPLAY_HEIGHT, surfaceContainer.height)
                .apply()

            drawBitmapToSurface()
        }

        override fun onVisibleAreaChanged(visibleArea: Rect) {
            // Potentially re-calculate fullScreenRect if it can change dynamically
            // For now, assume it's set in onSurfaceAvailable
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
        loadAndPrepareWallpaperItems()

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                loadAndPrepareWallpaperItems()
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
            }

            override fun onPause(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
                surface = null // Release surface explicitly
            }
        })
    }

    private fun loadAndPrepareWallpaperItems() {
        val prefs = carContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_WALLPAPER_ITEMS, null)
        if (jsonString != null) {
            val type = object : TypeToken<List<WallpaperItem>>() {}.type
            wallpaperItems = gson.fromJson(jsonString, type)
        } else {
            wallpaperItems = emptyList()
        }
        currentImageIndex = 0 // Reset index when items are reloaded
    }

    override fun onGetTemplate(): Template {
        val cycleImageAction = Action.Builder()
            .setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(
                        carContext,
                        R.drawable.icon // Ensure this drawable exists
                    )
                ).build()
            )
            .setOnClickListener {
                if (wallpaperItems.isNotEmpty()) {
                    currentImageIndex = (currentImageIndex + 1) % wallpaperItems.size
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
        val destinationRect = fullScreenRect ?: return // This is the car display's full area
        if (!localSurface.isValid || wallpaperItems.isEmpty()) {
            // Optionally clear canvas or draw a placeholder if no images
            return
        }

        val canvas: Canvas = localSurface.lockCanvas(null) ?: return

        try {
            val currentWallpaperItem = wallpaperItems[currentImageIndex]
            val currentUri = Uri.parse(currentWallpaperItem.uriString)

            try {
                carContext.contentResolver.openInputStream(currentUri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        // Use the saved cropRect if available, otherwise use the full bitmap
                        val sourceRect = currentWallpaperItem.cropRect?.toRect()
                            ?: Rect(0, 0, bitmap.width, bitmap.height)
                        
                        canvas.drawColor(Color.BLACK) // Clear canvas before drawing new bitmap
                        canvas.drawBitmap(bitmap, sourceRect, destinationRect, null)
                    } else {
                        // Failed to decode bitmap, draw error/placeholder
                        canvas.drawColor(Color.DKGRAY)
                        canvas.drawText("Error: Cannot load image", canvas.width / 2f, canvas.height / 2f, textPaint)
                    }
                }
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                // URI not found or not accessible, draw error/placeholder
                 canvas.drawColor(Color.DKGRAY)
                 canvas.drawText("Error: Image not found", canvas.width / 2f, canvas.height / 2f, textPaint)
            } catch (e: SecurityException) {
                e.printStackTrace()
                // Permissions issue
                 canvas.drawColor(Color.DKGRAY)
                 canvas.drawText("Error: Permission denied", canvas.width / 2f, canvas.height / 2f, textPaint)
            }

        } finally {
            if (localSurface.isValid) { // Check if surface is still valid before unlocking
                localSurface.unlockCanvasAndPost(canvas)
            }
        }
    }
}
