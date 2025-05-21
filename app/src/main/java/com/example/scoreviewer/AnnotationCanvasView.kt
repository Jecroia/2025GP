package com.example.scoreviewer

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/** 도구 종류 */
enum class Tool { PEN, HIGHLIGHTER, ERASER, TEXT }

private enum class ActionType { ADD, REMOVE }

private sealed class Stroke {
    data class PathStroke(val path: Path, val paint: Paint, val points: MutableList<PointF>) : Stroke()
    data class TextStroke(val text: String, val x: Float, val y: Float, val paint: Paint) : Stroke()
}

class AnnotationCanvasView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var currentPage: Int = 0
    private val pageToHistory = mutableMapOf<Int, MutableList<Stroke>>()
    private val globalActionStack = mutableListOf<Triple<Int, Stroke, ActionType>>()
    private val globalRedoStack   = mutableListOf<Triple<Int, Stroke, ActionType>>()

    var onTextTapListener: ((Float, Float) -> Unit)? = null
    private var currentTool: Tool? = null

    fun setPage(page: Int) {
        // 페이지 전환 시 히스토리·스택 초기화 없이 해당 페이지만 다시 그리기
        currentPage = page
        invalidate()
    }

    /** 툴 설정 */
    fun setTool(tool: Tool?) { currentTool = tool }

    /** undo/redo도 페이지별로 작동하도록 수정 */
    fun undoLast(): Boolean {
        if (globalActionStack.isEmpty()) return false
        val (page, stroke, type) = globalActionStack.removeAt(globalActionStack.lastIndex)
        val history = pageToHistory.getOrPut(page) { mutableListOf() }

        when (type) {
            ActionType.ADD    -> history.remove(stroke)
            ActionType.REMOVE -> history.add(stroke)
        }
        globalRedoStack.add(Triple(page, stroke, type))
        invalidate()
        return true
    }

    fun redoLast(): Boolean {
        if (globalRedoStack.isEmpty()) return false
        val (page, stroke, type) = globalRedoStack.removeAt(globalRedoStack.lastIndex)
        val history = pageToHistory.getOrPut(page) { mutableListOf() }

        when (type) {
            ActionType.ADD    -> history.add(stroke)
            ActionType.REMOVE -> history.remove(stroke)
        }
        globalActionStack.add(Triple(page, stroke, type))
        invalidate()
        return true
    }

    /** addText도 페이지별로 저장 */
    fun addText(text: String, x: Float, y: Float) {
        val history = pageToHistory.getOrPut(currentPage) { mutableListOf() }
        globalRedoStack.clear()
        val paint = makePaintFor(Tool.TEXT)
        val newStroke = Stroke.TextStroke(text, x, y, paint)

        history.add(newStroke)
        globalActionStack.add(Triple(currentPage, newStroke, ActionType.ADD))
        invalidate()
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val tool = currentTool ?: return false  // PDF 스크롤 방지를 위한 기존 로직
        val x = ev.x; val y = ev.y

        /**각 페이지별 리스트를 미리 오픈 */
        val history     = pageToHistory   .getOrPut(currentPage) { mutableListOf() }
        globalRedoStack.clear()

        when (tool) {
            Tool.PEN, Tool.HIGHLIGHTER -> {
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        val paint = makePaintFor(tool)
                        val path  = Path().apply { moveTo(x, y) }
                        val newStroke = Stroke.PathStroke(path, paint, mutableListOf(PointF(x, y)))
                        history.add(newStroke)
                        globalActionStack.add(Triple(currentPage, newStroke, ActionType.ADD))
                    }
                    MotionEvent.ACTION_MOVE -> (history.lastOrNull() as? Stroke.PathStroke)?.let {
                        it.path.lineTo(x, y)
                        it.points.add(PointF(x, y))
                    }
                    else -> {}
                }
                invalidate()
            }

            Tool.ERASER -> {
                if (ev.action == MotionEvent.ACTION_DOWN || ev.action == MotionEvent.ACTION_MOVE) {
                    var erased = false
                    val removed = mutableListOf<Stroke>()
                    val iter = history.iterator()
                    while (iter.hasNext()) {
                        when (val s = iter.next()) {
                            is Stroke.PathStroke ->
                                if (intersectsPath(s.points, x, y, s.paint.strokeWidth)) {
                                    iter.remove()
                                    removed.add(s)
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
                                    removed.add(s)
                                    erased = true
                                }
                            }
                        }
                    }
                    if (erased) {
                        removed.forEach { globalActionStack.add(Triple(currentPage, it, ActionType.REMOVE)) }
                        globalRedoStack.clear()
                        invalidate()
                    }
                }
            }

            Tool.TEXT ->
                if (ev.action == MotionEvent.ACTION_DOWN)
                    onTextTapListener?.invoke(x, y)
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        pageToHistory[currentPage]?.forEach {
            when (it) {
                is Stroke.PathStroke -> canvas.drawPath(it.path, it.paint)
                is Stroke.TextStroke -> canvas.drawText(it.text, it.x, it.y, it.paint)
            }
        }
    }
    private fun makePaintFor(tool: Tool): Paint = Paint().apply {
        style       = if (tool == Tool.TEXT) Paint.Style.FILL else Paint.Style.STROKE
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

    private fun distancePointToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1; val dy = y2 - y1
        if (dx == 0f && dy == 0f) return hypot(px - x1, py - y1)
        val t = ((px - x1) * dx + (py - y1) * dy) / (dx*dx + dy*dy)
        val ct = t.coerceIn(0f, 1f)
        val projX = x1 + ct*dx; val projY = y1 + ct*dy
        return hypot(px - projX, py - projY)
    }
}
