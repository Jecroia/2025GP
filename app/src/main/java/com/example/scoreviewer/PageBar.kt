package com.example.scoreviewer

import android.graphics.Bitmap
import android.util.Log
import android.view.MotionEvent
import android.widget.SeekBar
import android.util.LruCache
import android.view.View
import com.artifex.mupdf.fitz.*

class PageBar(
    private val pdfDocument: Document,
    private val pageCount: Int
) {
    var onThumbnailRequested: ((bitmap: Bitmap, xPos: Int, yPos: Int) -> Unit)? = null
    var onPageSelected: ((page: Int) -> Unit)? = null
    private var pageSeekBar: SeekBar? = null
    private var isLongPress = false
    private var longPressRunnable: Runnable? = null
    private val longPressThreshold = 300L // 롱프레스 기준 시간 (300ms)

    // LruCache: 최대 메모리의 1/8 정도 사용 (단위: 킬로바이트)
    private val thumbnailCache: LruCache<Int, Bitmap>

    init {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8
        thumbnailCache = LruCache(cacheSize)
    }

    fun initializeSeekBar(seekBar: SeekBar) {
        pageSeekBar = seekBar
        seekBar.max = pageCount - 1

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(sb: SeekBar?) {
                isLongPress = true
                updateThumbnail(seekBar.progress, seekBar)
            }

            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (isLongPress && fromUser) {
                    updateThumbnail(progress, seekBar)
                }
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                if (sb != null) {
                    if (isLongPress) {
                        hideThumbnail()
                    }
                    onPageSelected?.invoke(sb.progress)
                    isLongPress = false
                }
            }
        })

        seekBar.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isLongPress = false
                    longPressRunnable = Runnable {
                        isLongPress = true
                        val touchX = event.x.toInt()
                        val progress = calculateProgressFromTouch(seekBar, touchX)
                        seekBar.progress = progress
                        updateThumbnail(progress, seekBar)
                    }
                    seekBar.postDelayed(longPressRunnable!!, longPressThreshold)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isLongPress) {
                        val touchX = event.x.toInt()
                        val progress = calculateProgressFromTouch(seekBar, touchX)
                        seekBar.progress = progress
                        updateThumbnail(progress, seekBar)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { seekBar.removeCallbacks(it) }
                    longPressRunnable = null
                    if (isLongPress) {
                        hideThumbnail()
                    }
                    onPageSelected?.invoke(seekBar.progress)
                    isLongPress = false
                    v.performClick()
                }
            }
            false
        }
    }

    // 캐시 상태를 로그로 출력하는 함수 (몇 페이지가 캐시에 있는지 확인)
    fun logCacheStatus() {
        val cacheSize = thumbnailCache.snapshot().size  // 현재 캐시에 저장된 항목 수
        Log.d("PageBar", "Thumbnail cache contains $cacheSize pages")
    }

    // getThumbnail()는 캐시를 먼저 확인하여 있으면 재사용, 없으면 새로 렌더링하여 캐시에 저장
    private fun getThumbnail(pageIndex: Int): Bitmap {
        thumbnailCache.get(pageIndex)?.let { return it }
        val bitmap = renderPageThumbnail(pageIndex)
        thumbnailCache.put(pageIndex, bitmap)
        // 캐시에 추가 후 상태 로그 출력 (디버깅 용)
        logCacheStatus()
        return bitmap
    }

    private fun updateThumbnail(pageIndex: Int, seekBar: SeekBar) {
        val bitmap = getThumbnail(pageIndex)
        val xPos = calculateThumbX(seekBar, pageIndex)
        val location = IntArray(2)
        seekBar.getLocationOnScreen(location)
        val yPos = location[1] + seekBar.height + 10  // 썸네일은 SeekBar 하단에 표시
        onThumbnailRequested?.invoke(bitmap, xPos, yPos)
    }

    private fun hideThumbnail() {
        onThumbnailRequested?.invoke(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), -1, -1)
    }

    private fun calculateThumbX(seekBar: SeekBar, progress: Int): Int {
        val availableWidth = seekBar.width - seekBar.paddingLeft - seekBar.paddingRight
        return seekBar.paddingLeft + (availableWidth * progress / seekBar.max)
    }

    private fun calculateProgressFromTouch(seekBar: SeekBar, touchX: Int): Int {
        val width = seekBar.width - seekBar.paddingLeft - seekBar.paddingRight
        val x = touchX.coerceIn(0, width)
        return (seekBar.max * x / width.toFloat()).toInt()
    }

    private fun renderPageThumbnail(pageIndex: Int): Bitmap {
        val page = pdfDocument.loadPage(pageIndex)
        val matrix = Matrix.Scale(0.2f)
        val pixmap = page.toPixmap(matrix, ColorSpace.DeviceRGB, true, true)
        val bitmap = Bitmap.createBitmap(pixmap.width, pixmap.height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixmap.pixels, 0, pixmap.width, 0, 0, pixmap.width, pixmap.height)
        pixmap.destroy()
        page.destroy()
        return bitmap
    }

    fun setSeekBarActive(active: Boolean) {
        pageSeekBar?.let {
            it.visibility = if (active) View.VISIBLE else View.GONE
            it.isEnabled = active
        }
    }
}
