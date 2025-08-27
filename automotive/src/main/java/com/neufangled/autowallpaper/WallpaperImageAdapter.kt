package com.neufangled.autowallpaper

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
// import android.widget.ImageView // No longer needed for imageViewThumbnail
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView // Added import
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

class WallpaperImageAdapter(
    private val context: Context,
    private var imageUris: MutableList<Uri>,
    private val onItemClick: (Uri) -> Unit,
    private val onItemLongClick: (Uri) -> Unit
) : RecyclerView.Adapter<WallpaperImageAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_wallpaper_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val uri = imageUris[position]
        holder.bind(uri)
        holder.itemView.setOnClickListener { onItemClick(uri) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick(uri)
            true // Consume the long click
        }
    }

    override fun getItemCount(): Int = imageUris.size

    fun updateUris(newUris: List<Uri>) {
        this.imageUris.clear()
        this.imageUris.addAll(newUris)
        notifyDataSetChanged()
    }

    private fun getImageResolution(context: Context, uri: Uri): String? {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return null

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                return "${options.outWidth}x${options.outHeight}"
            }
        } catch (e: FileNotFoundException) {
            // Log error or handle as needed
            e.printStackTrace()
        } finally {
            try {
                inputStream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return null
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Changed ImageView to ShapeableImageView
        private val imageViewThumbnail: ShapeableImageView = itemView.findViewById(R.id.imageViewThumbnail)
        private val textViewImageName: TextView = itemView.findViewById(R.id.textViewImageName)

        fun bind(uri: Uri) {
            Glide.with(context)
                .load(uri)
                .placeholder(R.drawable.ic_error_image)
                .error(R.drawable.ic_error_image)
                .centerCrop()
                .into(imageViewThumbnail)

            val resolution = getImageResolution(context, uri)
            textViewImageName.text = resolution ?: uri.lastPathSegment ?: "Image"
        }
    }
}
