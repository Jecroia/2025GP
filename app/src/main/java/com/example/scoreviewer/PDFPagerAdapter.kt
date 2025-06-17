package com.example.scoreviewer

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import com.example.scoreviewer.MuPdfDispatcher

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

    /** 페이지별 (width,height) 픽셀 정보를 저장해 다른 컴포넌트에서 접근 가능하도록 유지 */
    private val pageSizeMap = mutableMapOf<Int, Pair<Int, Int>>()

    fun getPageSize(index: Int): Pair<Int, Int>? = pageSizeMap[index]

    fun getBitmap(index: Int): Bitmap? = bitmapCache.get(index)

    inner class PageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView) {
        var attacher: PhotoViewAttacher? = null
        var renderJob: Job? = null
        var originalBitmap: Bitmap? = null
        var highlightedBitmap: Bitmap? = null
        var currentLine = -1
    }

    private val linePaint = Paint().apply {
        color = Color.YELLOW
        alpha = 100
        style = Paint.Style.FILL
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
        // ───────── 0. 이전 작업 정리 ─────────
        holder.renderJob?.cancel()
        holder.attacher = null   // PhotoViewAttacher 해제

        // ───────── 1. 캐시 비트맵 있으면 즉시 사용 ─────────
        bitmapCache.get(position)?.let { bmp ->
            if (!bmp.isRecycled) {
                holder.originalBitmap = bmp                    // ⭐ origin 브랜치 기능 살림
                holder.highlightedBitmap = bmp.copy(
                    bmp.config ?: Bitmap.Config.ARGB_8888, true
                )
                holder.imageView.setImageBitmap(bmp)
                attachPhotoView(holder, position)                 // attacher 재생성
                pageSizeMap[position] = Pair(bmp.width, bmp.height)
                return                                            // 더 이상 작업 불필요
            }
        }

        // 플레이스홀더 & 기본 attacher
        holder.imageView.setImageDrawable(null)
        attachPhotoView(holder, position)                         // 스케일 1.0 상태의 매트릭스 반영

        // 코루틴으로 페이지 렌더링
        holder.renderJob = CoroutineScope(Dispatchers.IO).launch {
            val bmp = renderPage(position)                        // pixmap → Bitmap 변환 포함
            val copyForHL = bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, true)

            bitmapCache.put(position, bmp)                        // 캐시 저장

            withContext(Dispatchers.Main) {
                holder.originalBitmap = bmp
                holder.highlightedBitmap = copyForHL
                holder.imageView.setImageBitmap(bmp)
                holder.attacher?.update()                         // 스케일/팬 상태 유지
            }
        }
    }

    /** MuPDF → Android Bitmap 변환 로직 */
    private suspend fun renderPage(idx: Int): Bitmap {
        return withContext(MuPdfDispatcher.dispatcher) {
            val total = pdfManager.pageCount()
            if (idx < 0 || idx >= total) {
                return@withContext Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
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
            // 페이지 크기 캐시
            pageSizeMap[idx] = Pair(bitmap.width, bitmap.height)
            bitmap
        }
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
    fun highlightLine(pageNumber: Int, lineNumber: Int) {
        val rv = viewPager.getChildAt(0) as? RecyclerView
        val holder = rv?.findViewHolderForAdapterPosition(pageNumber) as? PageViewHolder
        holder?.let {
            if (it.currentLine != lineNumber) {
                it.currentLine = lineNumber
                it.originalBitmap?.let { original ->
                    val highlighted =
                        original.copy(original.config ?: Bitmap.Config.ARGB_8888, true)
                    val canvas = Canvas(highlighted)

                    // 페이지를 4개의 줄로 나누어 하이라이트
                    val lineHeight = original.height / 4
                    val y = lineNumber * lineHeight

                    canvas.drawRect(
                        0f,
                        y.toFloat(),
                        original.width.toFloat(),
                        (y + lineHeight).toFloat(),
                        linePaint
                    )

                    it.highlightedBitmap = highlighted
                    it.imageView.setImageBitmap(highlighted)
                }
            }
        }
    }
}
