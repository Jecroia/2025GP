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

class PDFPagerAdapter(
    private val pdfManager: PdfManager,
    private val pageCount: Int,
    private val annotationCanvas: AnnotationCanvasView,
    private val viewPager: ViewPager2
) : RecyclerView.Adapter<PDFPagerAdapter.PageViewHolder>() {
    private val bitmapCache = object : LruCache<Int, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
    }
    private val pageRenderJobs = mutableMapOf<Int, Job>()
    private var isClosed = false
    fun closeAdapter() {
        isClosed = true
    }
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
        // 0. 이미 어댑터가 close 상태라면 바로 return
        if (isClosed) return

        // 0-1. 이전 작업 정리
        holder.renderJob?.cancel()
        holder.renderJob = null
        holder.attacher = null

        // 1. 캐시 비트맵 있으면 즉시 사용
        bitmapCache.get(position)?.let { bmp ->
            if (!bmp.isRecycled) {
                holder.originalBitmap = bmp
                holder.highlightedBitmap = bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, true)
                holder.imageView.setImageBitmap(bmp)
                attachPhotoView(holder, position)
                return
            }
        }

        // 2. 플레이스홀더 & attacher 초기화
        holder.originalBitmap = null
        holder.highlightedBitmap = null
        holder.imageView.setImageDrawable(null)
        attachPhotoView(holder, position)

        // 3. 비동기 PDF 렌더링 시작 (close 방어 추가)
        val job = CoroutineScope(Dispatchers.IO).launch {
            // PDF가 이미 닫혔는지 체크
            if (isClosed) return@launch
            val bmp = try {
                renderPage(position)
            } catch (e: Exception) {
                null
            }

            if (bmp == null || isClosed) return@launch
            val copyForHL = bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, true)
            bitmapCache.put(position, bmp)
            withContext(Dispatchers.Main) {
                if (isClosed) return@withContext
                // position mismatch 체크
                if (holder.adapterPosition == position) {
                    holder.originalBitmap = bmp
                    holder.highlightedBitmap = copyForHL
                    holder.imageView.setImageBitmap(bmp)
                    holder.attacher?.update()
                } else {
                    if (!bitmapCache.snapshot().values.contains(bmp)) bmp.recycle()
                }
            }
        }
        holder.renderJob = job
    }


    /** MuPDF → Android Bitmap 변환 로직 */
    private fun renderPage(idx: Int): Bitmap? {
        // 1. 페이지 범위 및 PDFManager 닫힘 체크
        val total = pdfManager.pageCount()
        if (idx < 0 || idx >= total || pdfManager.isClosed) {
            return null
        }

        // 2. 페이지 로드 시 null 체크 및 예외 처리
        val page = try {
            pdfManager.loadPage(idx)
        } catch (e: Exception) {
            null
        }
        if (page == null) return null

        // 3. toPixmap 및 픽셀 변환 예외 처리
        return try {
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
            bitmap
        } catch (e: Exception) {
            try { page.destroy() } catch (_: Exception) {}
            null
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
        holder.renderJob = null
        holder.attacher = null

        // 현재 뷰홀더에 표시된 비트맵이 캐시에 없는 경우만 recycle
        (holder.imageView.drawable as? BitmapDrawable)?.bitmap?.let { b ->
            if (!bitmapCache.snapshot().values.contains(b) && !b.isRecycled) {
                b.recycle()
            }
        }
        holder.imageView.setImageDrawable(null)
        holder.originalBitmap = null
        holder.highlightedBitmap = null
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
    fun releaseAll() {
        // 1. 모든 렌더링 Job 취소
        for (job in runningJobs) {
            job.cancel()
        }
        runningJobs.clear()

        // 2. 모든 비트맵 캐시 recycle & clear
        for (bmp in bitmapCache.snapshot().values) {
            if (!bmp.isRecycled) bmp.recycle()
        }
        bitmapCache.evictAll()
    }
