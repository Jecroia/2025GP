package com.example.scoreviewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.content.ContextCompat

class BookmarkSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.seekBarStyle
) : AppCompatSeekBar(context, attrs, defStyleAttr) {

    private var bookmarks: Set<Int> = emptySet()
    private var pageCount: Int = 1

    // 노란색 마커용 페인트
    private val markerPaint = Paint().apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.bookmarkYellow)
    }

    /** 전체 페이지 수를 알려주면 내부 계산에 사용합니다 */
    fun setPageCount(count: Int) {
        pageCount = if (count > 0) count else 1
        invalidate()
    }

    /** 북마크된 페이지 인덱스 집합을 받아 저장하고 다시 그립니다 */
    fun setBookmarks(bookmarks: Set<Int>) {
        this.bookmarks = bookmarks
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (pageCount <= 1 || bookmarks.isEmpty()) return

        // SeekBar 내부 실제 그릴 수 있는 너비
        val usableWidth = width - paddingLeft - paddingRight

        // 각 북마크 위치에 작은 사각형(또는 선)을 그림
        bookmarks.forEach { pageIndex ->
            // 0..(pageCount-1) 범위의 인덱스라고 가정
            val ratio = pageIndex.toFloat() / (pageCount - 1).toFloat()
            val x = paddingLeft + ratio * usableWidth
            // 마커 너비 4px로
            canvas.drawRect(x - 6f, 0f, x + 6f, height.toFloat(), markerPaint)
        }
    }
}