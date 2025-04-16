package com.example.scoreviewer

import android.graphics.Bitmap
import android.view.MotionEvent
import android.widget.SeekBar
import com.artifex.mupdf.fitz.*

class PageBar(
    private val pdfDocument: Document,
    private val pageCount: Int
) {
    var onThumbnailRequested: ((bitmap: Bitmap, xPos: Int, yPos: Int) -> Unit)? = null
    var onPageSelected: ((page: Int) -> Unit)? = null

    private var isLongPress = false
    private var longPressRunnable: Runnable? = null
    private val longPressThreshold = 300L // 롱프레스 기준 시간

    fun initializeSeekBar(seekBar: SeekBar) {
        seekBar.max = pageCount - 1

        // 기본 드래그 이벤트 처리
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

        // 터치 이벤트 처리 (드래그가 아닌 단순 터치 시 썸네일 표시되지 않도록, 길게 눌렀을 때만 동작)
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

    // hideThumbnail()를 통해 빈 비트맵으로 썸네일 제거 요청
    private fun hideThumbnail() {
        onThumbnailRequested?.invoke(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), -1, -1)
    }

    private fun updateThumbnail(pageIndex: Int, seekBar: SeekBar) {
        val bitmap = renderPageThumbnail(pageIndex)
        val xPos = calculateThumbX(seekBar, pageIndex)
        val location = IntArray(2)
        seekBar.getLocationOnScreen(location)
        val yPos = location[1] + seekBar.height + 10  // 썸네일은 SeekBar 하단에 뜨게 함
        onThumbnailRequested?.invoke(bitmap, xPos, yPos)
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
}
