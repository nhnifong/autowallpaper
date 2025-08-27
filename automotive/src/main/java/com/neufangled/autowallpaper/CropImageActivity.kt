package com.neufangled.autowallpaper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.FileNotFoundException

class CropImageActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var cropOverlayView: CropOverlayView
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    private var imageUri: Uri? = null
    private val gson = Gson()

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val RESULT_CROP_SUCCESSFUL = "result_crop_successful"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop_image)

        imageView = findViewById(R.id.image_to_crop)
        cropOverlayView = findViewById(R.id.crop_overlay_view)
        saveButton = findViewById(R.id.save_crop_button)
        cancelButton = findViewById(R.id.cancel_crop_button)

        cropOverlayView.setImageView(imageView) // Pass ImageView reference

        imageUri = intent.getParcelableExtra(EXTRA_IMAGE_URI)

        if (imageUri == null) {
            Toast.makeText(this, "No image URI provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Load car display dimensions
        val prefs = getSharedPreferences(ImageDisplayScreen.PREFS_NAME, Context.MODE_PRIVATE)
        val carDisplayWidth = prefs.getInt(ImageDisplayScreen.KEY_CAR_DISPLAY_WIDTH, 0)
        val carDisplayHeight = prefs.getInt(ImageDisplayScreen.KEY_CAR_DISPLAY_HEIGHT, 0)

        if (carDisplayWidth > 0 && carDisplayHeight > 0) {
            val aspectRatio = carDisplayWidth.toFloat() / carDisplayHeight.toFloat()
            cropOverlayView.setCarDisplayAspectRatio(aspectRatio)
        } else {
            Toast.makeText(this, "Car display dimensions not found. Using default crop.", Toast.LENGTH_LONG).show()
            // Fallback: allow freeform or a default aspect ratio if car dimensions aren't set
            // For now, if not set, the crop rectangle might not behave as expected with aspect lock.
            // Consider disabling aspect lock or using a common default like 16:9
            cropOverlayView.setCarDisplayAspectRatio(16f/9f) // Default fallback
        }


        // Load bitmap and set it to ImageView and CropOverlayView once layout is complete
        imageView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                imageView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                try {
                    contentResolver.openInputStream(imageUri!!)?.use { inputStream ->
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap)
                            cropOverlayView.setImageBitmap(bitmap) // This will trigger initial crop rect calculation
                        } else {
                            Toast.makeText(this@CropImageActivity, "Failed to load image", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                } catch (e: FileNotFoundException) {
                    Toast.makeText(this@CropImageActivity, "Image not found", Toast.LENGTH_SHORT).show()
                    Log.e("CropImageActivity", "Error loading image: $imageUri", e)
                    finish()
                }  catch (e: SecurityException) {
                     Toast.makeText(this@CropImageActivity, "Permission denied for image", Toast.LENGTH_SHORT).show()
                     Log.e("CropImageActivity", "Security error loading image: $imageUri", e)
                     finish()
                }
            }
        })

        saveButton.setOnClickListener {
            saveCroppedImage()
        }

        cancelButton.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    private fun saveCroppedImage() {
        val croppedRect = cropOverlayView.getCroppedRect()
        if (imageUri == null || croppedRect == null) {
            Toast.makeText(this, "Could not save crop.", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences(ImageDisplayScreen.PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(ImageDisplayScreen.KEY_WALLPAPER_ITEMS, null)
        val type = object : TypeToken<MutableList<WallpaperItem>>() {}.type
        val wallpaperItems: MutableList<WallpaperItem> = if (jsonString != null) {
            gson.fromJson(jsonString, type)
        } else {
            mutableListOf()
        }

        // Check if this image URI already exists, update if so, otherwise add new
        val existingItemIndex = wallpaperItems.indexOfFirst { it.uriString == imageUri.toString() }
        val serializableCropRect = SerializableRect(croppedRect.left, croppedRect.top, croppedRect.right, croppedRect.bottom)

        if (existingItemIndex != -1) {
            wallpaperItems[existingItemIndex] = WallpaperItem(imageUri.toString(), serializableCropRect)
        } else {
            wallpaperItems.add(WallpaperItem(imageUri.toString(), serializableCropRect))
        }

        val newJsonString = gson.toJson(wallpaperItems)
        prefs.edit().putString(ImageDisplayScreen.KEY_WALLPAPER_ITEMS, newJsonString).apply()

        Toast.makeText(this, "Wallpaper updated!", Toast.LENGTH_SHORT).show()
        val resultIntent = Intent()
        resultIntent.putExtra(RESULT_CROP_SUCCESSFUL, true)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}
