package com.example.scoreviewer

import android.graphics.Bitmap
import android.util.LruCache
import android.view.MotionEvent
import android.widget.SeekBar
import android.view.View
import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Matrix

class PageBar(
    private val pdfManager: PdfManager,
    private val pageCount: Int
) {
    var onThumbnailRequested: ((bitmap: Bitmap, xPos: Int, yPos: Int) -> Unit)? = null
    var onPageSelected: ((page: Int) -> Unit)? = null

    private var seekBar: SeekBar? = null
    private var isLongPress = false
    private var longPressRunnable: Runnable? = null
    private val longPressThreshold = 300L

    // 캐시: 최대 메모리 1/8 크기 (KB 단위)
    private val thumbnailCache: LruCache<Int, Bitmap> = run {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        LruCache(maxKb / 8)
    }

    fun initializeSeekBar(sb: SeekBar) {
        seekBar = sb.apply {
            max = pageCount - 1

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(s: SeekBar?) {
                    isLongPress = true
                    updateThumbnail(progress)
                }
                override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (isLongPress && fromUser) updateThumbnail(progress)
                }
                override fun onStopTrackingTouch(s: SeekBar?) {
                    if (isLongPress) hideThumbnail()
                    onPageSelected?.invoke(progress)
                    isLongPress = false
                }
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
                    MotionEvent.ACTION_MOVE -> if (isLongPress) {
                        val x = calculateThumbX(ev.x.toInt())
                        progress = x
                        updateThumbnail(x)
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
        val bitmap = thumbnailCache.get(pageIndex)
            ?: renderAndCache(pageIndex)
        seekBar?.let { sb ->
            val loc = IntArray(2).also { sb.getLocationOnScreen(it) }
            val x = calculateThumbXFromProgress(sb, pageIndex)
            val y = loc[1] + sb.height + 10
            onThumbnailRequested?.invoke(bitmap, x, y)
        }
    }

    private fun renderAndCache(idx: Int): Bitmap {
        val page = pdfManager.loadPage(idx)
        val pix = page.toPixmap(Matrix.Scale(0.2f), ColorSpace.DeviceRGB, true, true)
        val bmp = Bitmap.createBitmap(pix.width, pix.height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pix.pixels, 0, pix.width, 0, 0, pix.width, pix.height)
        pix.destroy(); page.destroy()
        thumbnailCache.put(idx, bmp)
        return bmp
    }

    private fun calculateThumbXFromProgress(sb: SeekBar, prog: Int): Int {
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
