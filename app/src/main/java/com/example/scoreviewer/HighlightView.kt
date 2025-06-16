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
    private var musicXMLParser: MusicXMLParser? = null

    fun setMusicXMLParser(parser: MusicXMLParser) {
        musicXMLParser = parser
        invalidate()
    }

    fun setPage(page: Int) {
        currentPage = page
        musicXMLParser?.setPage(page)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        musicXMLParser?.let { parser ->
            // 마진 정보 가져오기
            val (topMarginPercent, bottomMarginPercent) = parser.getPageMargins()
            
            // 실제 사용 가능한 영역 계산
            val usableHeight = height * (1 - (topMarginPercent + bottomMarginPercent) / 100)
            val startY = height * (topMarginPercent / 100)
            
            // 하이라이트를 위한 줄 수 계산 (8페이지는 5등분)
            val highlightLineCount = parser.getLineCount()
            // 실제 내용이 있는 줄 수 계산
            val actualLineCount = parser.getActualLineCount()
            
            Log.d("HighlightView", "Current page has $actualLineCount actual lines, but will be divided into $highlightLineCount sections")
            
            // 각 줄의 높이 계산
            val lineHeight = usableHeight / highlightLineCount
            
            // 현재 시간에 해당하는 줄 계산 (예시: 0-1초는 첫 번째 줄)
            val currentTime = System.currentTimeMillis() / 1000.0
            val lineIndex = (currentTime % highlightLineCount).toInt()
            
            // 하이라이트 영역 그리기
            val highlightTop = startY + (lineHeight * lineIndex)
            val highlightBottom = highlightTop + lineHeight
            
            canvas.drawRect(0f, highlightTop.toFloat(), width.toFloat(), highlightBottom.toFloat(), paint)
        }
    }
} 