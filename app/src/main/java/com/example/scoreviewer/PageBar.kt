package com.example.scoreviewer

import android.graphics.Bitmap
import android.util.LruCache
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Matrix

class PageBar(

    private val pdfManager: PdfManager,
    private val pageCount: Int
) {
    var onThumbnailRequested: ((bitmap: Bitmap, xPos: Int, yPos: Int) -> Unit)? = null
    var onPageSelected: ((page: Int) -> Unit)? = null

    private var seekBar: BookmarkSeekBar? = null
    private var isLongPress = false
    private var longPressRunnable: Runnable? = null
    private val longPressThreshold = 300L
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingThumbnailPage: Int? = null

    // 캐시: 최대 메모리 1/8 크기 (KB 단위)
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = maxMemoryKb / 8

    private val thumbnailCache: LruCache<Int, Bitmap> = object : LruCache<Int, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: Int, value: Bitmap): Int {
            // value.byteCount는 “전체 바이트 수”이므로, KB 단위로 리턴
            return value.byteCount / 1024
        }
    }

    /** 외부에서 최신 북마크 집합을 전달할 때 호출 */
    private var bookmarks: Set<Int> = emptySet()
    fun setBookmarks(bookmarks: Set<Int>) {
        this.bookmarks = bookmarks
        // SeekBar 를 다시 그려서 onDraw 혹은 커스텀 레이어에서 북마크 마커를 표시하게 함
        seekBar?.setBookmarks(bookmarks)
    }

    fun initializeSeekBar(sb: BookmarkSeekBar) {
        seekBar = sb.apply {
            // (1) 전체 페이지 수 설정
            setPageCount(pageCount)
            // (2) 기존에 저장된 북마크 표시
            setBookmarks(bookmarks)
            max = pageCount - 1

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!isLongPress) {
                        onPageSelected?.invoke(progress)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            setOnTouchListener { v, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isLongPress = false
                        longPressRunnable = Runnable {
                            isLongPress = true
                            val x = calculateThumbX(ev.x.toInt())
                            progress = x
                            updateThumbnail(x)
                        }.also { postDelayed(it, longPressThreshold) }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isLongPress) {
                            val x = calculateThumbX(ev.x.toInt())
                            progress = x
                            updateThumbnail(x)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        longPressRunnable?.let { removeCallbacks(it) }
                        if (isLongPress) hideThumbnail()
                        onPageSelected?.invoke(progress)
                        isLongPress = false
                        v.performClick()
                    }
                }
                false
            }
        }
    }

    private fun hideThumbnail() {
        onThumbnailRequested?.invoke(
            Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888),
            -1, -1
        )
    }

    private fun updateThumbnail(pageIndex: Int) {
        val cached = thumbnailCache.get(pageIndex)
        if (cached != null) {
            showThumbnail(cached, pageIndex)
            return
        }

        // 중복 요청 방지
        pendingThumbnailPage = pageIndex

        handler.postDelayed({
            if (pendingThumbnailPage != pageIndex) return@postDelayed
            val rendered = renderAndCache(pageIndex)
            showThumbnail(rendered, pageIndex)
        }, 50)
    }

    private fun showThumbnail(bitmap: Bitmap, pageIndex: Int) {
        seekBar?.let { sb ->
            val loc = IntArray(2).also { sb.getLocationOnScreen(it) }
            val x = calculateThumbXFromProgress(sb, pageIndex)
            val y = loc[1] + sb.height + 10
            onThumbnailRequested?.invoke(bitmap, x, y)
        }
    }

    private fun renderAndCache(idx: Int): Bitmap {
        val page = pdfManager.loadPage(idx)
        val pix = page.toPixmap(Matrix.Scale(1.0f), ColorSpace.DeviceRGB, true, true)
        val bmp = Bitmap.createBitmap(pix.width, pix.height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pix.pixels, 0, pix.width, 0, 0, pix.width, pix.height)
        pix.destroy()
        page.destroy()
        thumbnailCache.put(idx, bmp)
        return bmp
    }

    private fun calculateThumbXFromProgress(sb: BookmarkSeekBar, prog: Int): Int {
        val w = sb.width - sb.paddingLeft - sb.paddingRight
        return sb.paddingLeft + w * prog / sb.max
    }

    private fun calculateThumbX(touchX: Int): Int {
        val sb = seekBar ?: return 0
        val w = sb.width - sb.paddingLeft - sb.paddingRight
        val x = touchX.coerceIn(0, w)
        return (sb.max * x / w.toFloat()).toInt()
    }

    /** 버튼으로 SeekBar 활성화/비활성화할 때 호출 */
    fun setSeekBarActive(active: Boolean) {
        seekBar?.apply {
            visibility = if (active) View.VISIBLE else View.GONE
            isEnabled  = active
        }
    }
}
