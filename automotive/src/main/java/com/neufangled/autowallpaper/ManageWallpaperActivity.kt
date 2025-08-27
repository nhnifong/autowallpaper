package com.neufangled.autowallpaper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ManageWallpaperActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAddImage: FloatingActionButton
    private var wallpaperItems = mutableListOf<WallpaperItem>()
    private lateinit var imageAdapter: WallpaperImageAdapter
    private val gson = Gson()

    private lateinit var pickImageLauncher: ActivityResultLauncher<PickVisualMediaRequest>
    private lateinit var cropImageLauncher: ActivityResultLauncher<Intent>

    companion object {
        private const val KEY_IS_FIRST_RUN_MANAGE_ACTIVITY = "is_first_run_manage_activity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_wallpaper)
        title = "Auto Wallpapers" // Added title

        checkFirstRun()

        recyclerView = findViewById(R.id.recyclerViewUserImages)
        fabAddImage = findViewById(R.id.fabAddImage)

        loadWallpaperItems()

        // Extract URIs for the adapter
        val displayUris = wallpaperItems.mapNotNull { Uri.parse(it.uriString) }.toMutableList()
        imageAdapter = WallpaperImageAdapter(this, displayUris,
            onItemClick = { clickedUri ->
            // Optional: Handle image click to re-crop or view
            // For now, we launch crop for a new image or to update an existing one via a long press or edit button (not implemented)
            },
            onItemLongClick = { clickedUri ->
                showDeleteConfirmationDialog(clickedUri)
            }
        )
        recyclerView.adapter = imageAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        setupLaunchers()

        fabAddImage.setOnClickListener {
            pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    private fun checkFirstRun() {
        val prefs = getSharedPreferences(ImageDisplayScreen.PREFS_NAME, Context.MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean(KEY_IS_FIRST_RUN_MANAGE_ACTIVITY, true)

        if (isFirstRun) {
            AlertDialog.Builder(this)
                .setTitle("Welcome!")
                .setMessage("Please connect to your car once before choosing wallpapers, so that the correct size will be known.")
                .setPositiveButton("OK") { dialog, _ ->
                    prefs.edit().putBoolean(KEY_IS_FIRST_RUN_MANAGE_ACTIVITY, false).apply()
                    dialog.dismiss()
                }
                .setCancelable(false) // Optional: Prevent dismissing by tapping outside
                .show()
        }
    }

    private fun setupLaunchers() {
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            uri?.let {
                // Persist this URI's permission for long-term access
                try {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    applicationContext.contentResolver.takePersistableUriPermission(uri, takeFlags)

                    // Launch CropImageActivity
                    val cropIntent = Intent(this, CropImageActivity::class.java).apply {
                        putExtra(CropImageActivity.EXTRA_IMAGE_URI, uri)
                    }
                    cropImageLauncher.launch(cropIntent)

                } catch (e: SecurityException) {
                    Log.e("ManageWallpaper", "Failed to take persistable URI permission: ${uri}", e)
                    // Inform user about the error
                }
            }
        }

        cropImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val cropSuccessful = result.data?.getBooleanExtra(CropImageActivity.RESULT_CROP_SUCCESSFUL, false) ?: false
                if (cropSuccessful) {
                    // Reload items from prefs as CropImageActivity saved them
                    loadWallpaperItemsAndRefreshAdapter()
                }
            }
        }
    }

    private fun showDeleteConfirmationDialog(uriToDelete: Uri) {
        AlertDialog.Builder(this)
            .setTitle("Remove image")
            .setMessage("This will remove the image from your set of wallpapers but it will remain on your device.")
            .setPositiveButton("Remove") { _, _ ->
                deleteWallpaperItem(uriToDelete)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteWallpaperItem(uriToDelete: Uri) {
        val itemToRemove = wallpaperItems.find { Uri.parse(it.uriString) == uriToDelete }
        itemToRemove?.let {
            wallpaperItems.remove(it)
            saveWallpaperItems()
            loadWallpaperItemsAndRefreshAdapter() // Refresh the adapter
        }
    }

    private fun saveWallpaperItems() {
        val prefs = getSharedPreferences(ImageDisplayScreen.PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val jsonString = gson.toJson(wallpaperItems)
        editor.putString(ImageDisplayScreen.KEY_WALLPAPER_ITEMS, jsonString)
        editor.apply()
    }

    private fun loadWallpaperItemsAndRefreshAdapter(){
        loadWallpaperItems()
        val displayUris = wallpaperItems.mapNotNull { Uri.parse(it.uriString) }.toMutableList()
        imageAdapter.updateUris(displayUris)
    }

    private fun loadWallpaperItems() {
        val prefs = getSharedPreferences(ImageDisplayScreen.PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(ImageDisplayScreen.KEY_WALLPAPER_ITEMS, null)
        if (jsonString != null) {
            val type = object : TypeToken<MutableList<WallpaperItem>>() {}.type
            wallpaperItems = gson.fromJson(jsonString, type)
        } else {
            wallpaperItems = mutableListOf()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh the list in case changes were made elsewhere or for consistency
        loadWallpaperItemsAndRefreshAdapter()
    }
}
