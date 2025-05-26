package com.example.scoreviewer

import android.graphics.Bitmap
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Matrix
import com.github.chrisbanes.photoview.PhotoViewAttacher

class PDFPagerAdapter(
    private val pdfManager: PdfManager,
    private val pageCount: Int,
    private val annotationCanvas: AnnotationCanvasView,
    private val viewPager: ViewPager2
) : RecyclerView.Adapter<PDFPagerAdapter.PageViewHolder>() {

    class PageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val imageView = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.MATRIX
        }
        return PageViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        // PdfManager 로부터 페이지 로드
        val page = pdfManager.loadPage(position)
        val pixmap = page.toPixmap(Matrix.Scale(1.0f), ColorSpace.DeviceRGB, true, true)

        // Bitmap 생성
        val bitmap = Bitmap.createBitmap(
            pixmap.width,
            pixmap.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.setPixels(pixmap.pixels, 0, pixmap.width, 0, 0, pixmap.width, pixmap.height)

        holder.imageView.setImageBitmap(bitmap)
        page.destroy()
        pixmap.destroy()

        // PhotoViewAttacher 로 핀치줌/팬/회전 기능 추가
        if (annotationCanvas != null && viewPager != null) {
            val attacher = PhotoViewAttacher(holder.imageView)
            attacher.setOnMatrixChangeListener {
                // 현재 보고 있는 페이지에 대해서만 매트릭스 반영
                if (position == viewPager.currentItem) {
                    annotationCanvas.setTransformationMatrix(holder.imageView.imageMatrix)
                    viewPager.isUserInputEnabled = (attacher.scale <= 1.0f)
                }
            }

            // 초기 매트릭스 설정과 페이지 스와이프 허용도 현재 페이지에 한정
            if (position == viewPager.currentItem) {
                annotationCanvas.setTransformationMatrix(holder.imageView.imageMatrix)
                viewPager.isUserInputEnabled = true
            }
        }
    }

    override fun getItemCount(): Int = pageCount
}
