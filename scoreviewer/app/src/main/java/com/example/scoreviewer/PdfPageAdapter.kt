package com.example.scoreviewer

import android.graphics.Bitmap
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Pixmap

class PdfPageAdapter(
    private val document: Document,
    private val pageCount: Int
) : RecyclerView.Adapter<PdfPageAdapter.PdfPageViewHolder>() {

    inner class PdfPageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder {
        val imageView = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        return PdfPageViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
        val page = document.loadPage(position)
        val matrix = Matrix.Scale(1.0f)
        val pixmap = page.toPixmap(matrix, ColorSpace.DeviceRGB, true, true)

        val bitmap = Bitmap.createBitmap(pixmap.width, pixmap.height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixmap.pixels, 0, pixmap.width, 0, 0, pixmap.width, pixmap.height)

        holder.imageView.setImageBitmap(bitmap)

        page.destroy()
        pixmap.destroy()
    }

    override fun getItemCount(): Int = pageCount
}