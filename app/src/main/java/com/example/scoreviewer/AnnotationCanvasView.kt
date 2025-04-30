package com.example.scoreviewer

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/** 도구 종류 */
enum class Tool { PEN, HIGHLIGHTER, ERASER, TEXT }

class AnnotationCanvasView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 텍스트 터치 좌표 콜백 */
    var onTextTapListener: ((Float, Float) -> Unit)? = null

    private sealed class Stroke {
        data class PathStroke(val path: Path, val paint: Paint, val points: MutableList<PointF>) : Stroke()
        data class TextStroke(val text: String, val x: Float, val y: Float, val paint: Paint) : Stroke()
    }

    private val strokeHistory = mutableListOf<Stroke>()
    private var currentTool: Tool? = null

    /** 툴 설정 */
    fun setTool(tool: Tool?) {
        currentTool = tool
    }

    /** 마지막 어노테이션 되돌리기 */
    fun undoLast(): Boolean =
        if (strokeHistory.isNotEmpty()) {
            strokeHistory.removeAt(strokeHistory.lastIndex)
            invalidate()
            true
        } else false

    /** 텍스트 추가 */
    fun addText(text: String, x: Float, y: Float) {
        val paint = makePaintFor(Tool.TEXT)
        strokeHistory.add(Stroke.TextStroke(text, x, y, paint))
        invalidate()
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val tool = currentTool ?: return false  // null 이면 PDF 뷰어가 터치를 가져감
        val x = ev.x; val y = ev.y
        when (tool) {
            Tool.PEN, Tool.HIGHLIGHTER -> handleDraw(ev, tool, x, y)
            Tool.ERASER               -> handleErase(ev, x, y)
            Tool.TEXT                 ->
                if (ev.action == MotionEvent.ACTION_DOWN)
                    onTextTapListener?.invoke(x, y)
        }
        return true
    }

    private fun handleDraw(ev: MotionEvent, tool: Tool, x: Float, y: Float) {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                val paint = makePaintFor(tool)
                val path = Path().apply { moveTo(x, y) }
                strokeHistory.add(Stroke.PathStroke(path, paint, mutableListOf(PointF(x, y))))
            }
            MotionEvent.ACTION_MOVE -> (strokeHistory.lastOrNull() as? Stroke.PathStroke)?.let {
                it.path.lineTo(x, y)
                it.points.add(PointF(x, y))
            }
            else -> {}
        }
        invalidate()
    }

    private fun handleErase(ev: MotionEvent, x: Float, y: Float) {
        if (ev.action == MotionEvent.ACTION_DOWN || ev.action == MotionEvent.ACTION_MOVE) {
            var erased = false
            val iter = strokeHistory.iterator()
            while (iter.hasNext()) {
                when (val s = iter.next()) {
                    is Stroke.PathStroke ->
                        if (intersectsPath(s.points, x, y, s.paint.strokeWidth)) {
                            iter.remove()
                            erased = true
                        }
                    is Stroke.TextStroke -> {
                        val bounds = RectF(
                            s.x,
                            s.y - s.paint.textSize,
                            s.x + s.paint.measureText(s.text),
                            s.y
                        )
                        if (bounds.contains(x, y)) {
                            iter.remove()
                            erased = true
                        }
                    }
                }
            }
            if (erased) invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        strokeHistory.forEach {
            when (it) {
                is Stroke.PathStroke -> canvas.drawPath(it.path, it.paint)
                is Stroke.TextStroke -> canvas.drawText(it.text, it.x, it.y, it.paint)
            }
        }
    }

    private fun makePaintFor(tool: Tool): Paint = Paint().apply {
        style      = if (tool == Tool.TEXT) Paint.Style.FILL else Paint.Style.STROKE
        isAntiAlias = true
        strokeWidth = when (tool) {
            Tool.PEN         -> 5f
            Tool.HIGHLIGHTER -> 30f
            Tool.ERASER      -> 50f
            else             -> 5f
        }
        color = when (tool) {
            Tool.PEN         -> Color.BLACK
            Tool.HIGHLIGHTER -> 0x33FFFF00.toInt()
            Tool.ERASER      -> Color.TRANSPARENT
            Tool.TEXT        -> Color.BLACK
            else             -> Color.BLACK
        }
        if (tool == Tool.ERASER)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        if (tool == Tool.TEXT)
            textSize = 48f
    }

    private fun intersectsPath(points: List<PointF>, px: Float, py: Float, radius: Float): Boolean {
        for (i in 0 until points.size - 1) {
            val p1 = points[i]; val p2 = points[i + 1]
            if (distancePointToSegment(px, py, p1.x, p1.y, p2.x, p2.y) <= radius)
                return true
        }
        return false
    }

    private fun distancePointToSegment(px: Float, py: Float,
                                       x1: Float, y1: Float,
                                       x2: Float, y2: Float): Float {
        val dx = x2 - x1; val dy = y2 - y1
        if (dx == 0f && dy == 0f) return hypot(px - x1, py - y1)
        val t = ((px - x1) * dx + (py - y1) * dy) / (dx*dx + dy*dy)
        val ct = t.coerceIn(0f, 1f)
        val projX = x1 + ct*dx; val projY = y1 + ct*dy
        return hypot(px - projX, py - projY)
    }
}

