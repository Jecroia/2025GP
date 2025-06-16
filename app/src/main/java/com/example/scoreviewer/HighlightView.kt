package com.example.scoreviewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View

class HighlightView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.YELLOW
        alpha = 128
    }

    private var currentPage = 1
    private var currentLines: List<MusicXmlParser.Line> = emptyList()

    fun setLines(lines: List<MusicXmlParser.Line>) {
        currentLines = lines
        invalidate()
    }

    fun setPage(page: Int) {
        currentPage = page
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (currentLines.isEmpty()) return

        // 현재 페이지의 줄들만 필터링
        val pageLines = currentLines.filter { it.pageNumber == currentPage }
        if (pageLines.isEmpty()) return

        // 마진 정보 가져오기 (기본값: 상단 10%, 하단 10%)
        val topMarginPercent = 10f
        val bottomMarginPercent = 10f
        
        // 실제 사용 가능한 영역 계산
        val usableHeight = height * (1 - (topMarginPercent + bottomMarginPercent) / 100)
        val startY = height * (topMarginPercent / 100)
        
        // 하이라이트를 위한 줄 수 계산
        val highlightLineCount = pageLines.size
        
        // 각 줄의 높이 계산
        val lineHeight = usableHeight / highlightLineCount
        
        // 현재 시간에 해당하는 줄 계산
        val currentTime = System.currentTimeMillis()
        val currentLine = pageLines.findLast { it.startTimeMs <= currentTime }
        
        currentLine?.let { line ->
            val lineIndex = pageLines.indexOf(line)
            val highlightTop = startY + (lineHeight * lineIndex)
            val highlightBottom = highlightTop + lineHeight
            
            canvas.drawRect(0f, highlightTop.toFloat(), width.toFloat(), highlightBottom.toFloat(), paint)
        }
    }
} 