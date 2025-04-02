package com.example.scoreviewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix

class PDFPagerAdapter(
    private val context: Context,
    private val document: Document
) : RecyclerView.Adapter<PDFPagerAdapter.PageViewHolder>() {

    inner class PageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val imageView = ImageView(context)
        imageView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        return PageViewHolder(imageView)
    }

    override fun getItemCount(): Int = document.countPages()

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val page = document.loadPage(position)
        val matrix = Matrix.Scale(1.0f)
        val pixmap = page.toPixmap(matrix, ColorSpace.DeviceRGB, true, true)

        val width = pixmap.width
        val height = pixmap.height
        val pixels = pixmap.pixels
        val bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        holder.imageView.setImageBitmap(bitmap)

        // 자원 정리
        page.destroy()
        pixmap.destroy()
    }
}
