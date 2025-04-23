// AnnotationCanvasView.kt
package com.example.scoreviewer

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

// 사용할 도구 정의
enum class Tool { PEN, HIGHLIGHTER, ERASER, TEXT }

class AnnotationCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // 내부 데이터 모델: 한 획을 기록하는 클래스
    private data class Stroke(val path: Path, val paint: Paint)

    private val strokeHistory = mutableListOf<Stroke>()
    private var currentTool: Tool = Tool.PEN

    // 페인트 설정
    private fun makePaintFor(tool: Tool): Paint {
        return Paint().apply {
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeWidth = when (tool) {
                Tool.PEN -> 5f
                Tool.HIGHLIGHTER -> 30f
                Tool.ERASER -> 50f
                else -> 5f
            }
            color = when (tool) {
                Tool.PEN -> Color.BLACK
                Tool.HIGHLIGHTER -> 0x33FFFF00   // 반투명 노랑
                Tool.ERASER -> Color.WHITE
                else -> Color.BLACK
            }
            if (tool == Tool.ERASER) {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
        }
    }

    // 새 Path 추가
    private var currentPath = Path()
    private var currentPaint = makePaintFor(currentTool)

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val x = ev.x
        val y = ev.y
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath = Path().apply { moveTo(x, y) }
                currentPaint = makePaintFor(currentTool)
                strokeHistory += Stroke(currentPath, currentPaint)
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath.lineTo(x, y)
            }
            MotionEvent.ACTION_UP -> {
                // nothing extra
            }
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for ((path, paint) in strokeHistory) {
            canvas.drawPath(path, paint)
        }
    }

    /** 1) 도구 설정 */
    fun setTool(tool: Tool) {
        currentTool = tool
    }

    /** 2) 마지막 획 취소 */
    fun undoLastStroke() {
        if (strokeHistory.isNotEmpty()) {
            strokeHistory.removeAt(strokeHistory.lastIndex)
            invalidate()
        }
    }

    /** 3) 전체 지우기 */
    fun clearAll() {
        strokeHistory.clear()
        invalidate()
    }
}
