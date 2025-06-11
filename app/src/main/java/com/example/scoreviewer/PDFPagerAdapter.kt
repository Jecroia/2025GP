package com.example.scoreviewer

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
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

    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = maxMemoryKb / 8  // 필요에 따라 4 또는 16 등으로 조정
    private val bitmapCache = object : androidx.collection.LruCache<Int, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: Int, value: Bitmap): Int {
            // KB 단위 계산: 바이트 개수 / 1024
            return value.byteCount / 1024
        }
    }

    inner class PageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView) {
        var attacher: PhotoViewAttacher? = null
    }



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

        holder.attacher?.let {
            // cleanup() 메서드가 없는 버전을 위해 단순히 참조만 끊습니다.
            holder.attacher = null
        }

        val cached = bitmapCache.get(position)
        if (cached != null && !cached.isRecycled) {
            holder.imageView.setImageBitmap(cached)
        } else {
            // PDF 페이지 로드
            val page = pdfManager.loadPage(position)
            val pixmap = page.toPixmap(Matrix.Scale(1.0f), ColorSpace.DeviceRGB, true, true)

            // 1) raw 배열 가져오기
            val raw = pixmap.pixels
            // 2) ABGR → ARGB로 R/B 채널 스왑
            for (i in raw.indices) {
                val px = raw[i]
                val a = (px ushr 24) and 0xFF
                val b = (px ushr 16) and 0xFF
                val g = (px ushr  8) and 0xFF
                val r = px and 0xFF
                raw[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            // 3) 스왑된 raw로 비트맵 생성
            val bitmap = Bitmap.createBitmap(pixmap.width, pixmap.height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(raw, 0, pixmap.width, 0, 0, pixmap.width, pixmap.height)

            page.destroy()
            pixmap.destroy()

            // 캐시에 저장 후 세팅
            bitmapCache.put(position, bitmap)
            holder.imageView.setImageBitmap(bitmap)
        }

        val attacher = PhotoViewAttacher(holder.imageView)
        holder.attacher = attacher
        attacher.setOnMatrixChangeListener {
            // 현재 페이지일 때만 캔버스 행렬 갱신
            if (position == viewPager.currentItem) {
                annotationCanvas.setTransformationMatrix(holder.imageView.imageMatrix)
                viewPager.isUserInputEnabled = (attacher.scale <= 1.0f)
            }
        }

        if (position == viewPager.currentItem) {
            annotationCanvas.setTransformationMatrix(holder.imageView.imageMatrix)
            viewPager.isUserInputEnabled = true
        } else {
            viewPager.isUserInputEnabled = false
        }
    }

    override fun getItemCount(): Int = pageCount

    override fun onViewRecycled(holder: PageViewHolder) {
        super.onViewRecycled(holder)
        // ───────────────────────────────────────────────────
        // 1) PhotoViewAttacher 참조만 끊기
        // ───────────────────────────────────────────────────
        holder.attacher?.let {
            holder.attacher = null
        }

        // ───────────────────────────────────────────────────
        // 2) ImageView에 설정된 비트맵 해제 (캐시에 저장된 비트맵이 아닌 경우만)
        // ───────────────────────────────────────────────────
        val drawable = holder.imageView.drawable
        if (drawable is BitmapDrawable) {
            val b = drawable.bitmap
            if (b != null && !b.isRecycled) {
                // 캐시에 보관된 비트맵이 아니라면 recycle 해 줍니다.
                // 만약 캐시된 비트맵일 가능성이 있다면 이 부분을 생략하세요.
                b.recycle()
            }
        }
        holder.imageView.setImageDrawable(null)
    }
}
