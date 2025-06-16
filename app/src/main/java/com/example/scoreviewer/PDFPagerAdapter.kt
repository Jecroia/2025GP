package com.example.scoreviewer

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.ViewGroup
import android.widget.ImageView
import androidx.collection.LruCache
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Matrix
import com.github.chrisbanes.photoview.PhotoViewAttacher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PDFPagerAdapter(
    private val pdfManager: PdfManager,
    private val pageCount: Int,
    private val annotationCanvas: AnnotationCanvasView,
    private val viewPager: ViewPager2
) : RecyclerView.Adapter<PDFPagerAdapter.PageViewHolder>() {

    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = maxMemoryKb / 8
    private val bitmapCache = object : LruCache<Int, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: Int, value: Bitmap): Int =
            value.byteCount / 1024
    }

    inner class PageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView) {
        var attacher: PhotoViewAttacher? = null
        var renderJob: Job? = null
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
        // 이전 attacher 정리
        holder.renderJob?.cancel()
        holder.attacher = null
        attachPhotoView(holder, position)

        // 1) 캐시가 있으면 바로 표시
        bitmapCache.get(position)?.let { bmp ->
            if (!bmp.isRecycled) {
                holder.imageView.setImageBitmap(bmp)
                attachPhotoView(holder, position)
                return
            }
        }

        // 2) 로딩 플레이스홀더
        holder.imageView.setImageDrawable(null)
        // 3) IO 스레드에서 렌더링
        holder.renderJob = CoroutineScope(Dispatchers.IO).launch {
            val bitmap = renderPage(position)
            bitmapCache.put(position, bitmap)
            withContext(Dispatchers.Main) {
                holder.imageView.setImageBitmap(bitmap)
                holder.attacher?.update()
            }
        }
    }

    /** MuPDF → Android Bitmap 변환 로직 */
    private fun renderPage(idx: Int): Bitmap {
        val total = pdfManager.pageCount()
        if (idx < 0 || idx >= total) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        val page = pdfManager.loadPage(idx)
        val pix = page.toPixmap(Matrix.Scale(1.0f), ColorSpace.DeviceRGB, true, true)
        val raw = pix.pixels

        // ABGR → ARGB 채널 스왑
        for (i in raw.indices) {
            val px = raw[i]
            val a = (px ushr 24) and 0xFF
            val b = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val r = px and 0xFF
            raw[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        val bitmap = Bitmap.createBitmap(pix.width, pix.height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(raw, 0, pix.width, 0, 0, pix.width, pix.height)

        pix.destroy()
        page.destroy()
        return bitmap
    }

    /** PhotoViewAttacher 연결 및 캔버스 매트릭스 동기화 */
    private fun attachPhotoView(holder: PageViewHolder, position: Int) {
        val attacher = PhotoViewAttacher(holder.imageView)
        holder.attacher = attacher
        attacher.setOnMatrixChangeListener {
            if (position == viewPager.currentItem) {
                annotationCanvas.setTransformationMatrix(holder.imageView.imageMatrix)
                viewPager.isUserInputEnabled = (attacher.scale <= 1.0f)
            }
        }
        // 초기 상태 동기화
        if (position == viewPager.currentItem) {
            annotationCanvas.setTransformationMatrix(holder.imageView.imageMatrix)
            viewPager.isUserInputEnabled = true
        }
    }

    override fun getItemCount(): Int = pageCount

    override fun onViewRecycled(holder: PageViewHolder) {
        super.onViewRecycled(holder)
        holder.renderJob?.cancel()
        holder.attacher = null
        (holder.imageView.drawable as? BitmapDrawable)?.bitmap?.let { b ->
            if (!bitmapCache.snapshot().values.contains(b)) {
                b.recycle()
            }
        }
        holder.imageView.setImageDrawable(null)
    }
}
