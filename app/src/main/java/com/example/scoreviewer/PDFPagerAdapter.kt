package com.example.scoreviewer

import android.graphics.Bitmap
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Matrix

class PDFPagerAdapter(
    private val pdfManager: PdfManager,
    private val pageCount: Int
) : RecyclerView.Adapter<PDFPagerAdapter.PageViewHolder>() {

    class PageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val imageView = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        return PageViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        // PdfManager 로부터 페이지 로드
        val page = pdfManager.loadPage(position)
        val pixmap = page.toPixmap(Matrix.Scale(1.0f), ColorSpace.DeviceRGB, true, true)

        val bitmap = Bitmap.createBitmap(
            pixmap.width,
            pixmap.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.setPixels(pixmap.pixels, 0, pixmap.width, 0, 0, pixmap.width, pixmap.height)

        holder.imageView.setImageBitmap(bitmap)
        page.destroy()
        pixmap.destroy()
    }

    override fun getItemCount(): Int = pageCount
}
