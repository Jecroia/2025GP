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

sealed class Stroke {
    data class PathStroke(val path: Path, val paint: Paint, val points: MutableList<PointF>) : Stroke()
    data class TextStroke(val text: String, val x: Float, val y: Float, val paint: Paint) : Stroke()
}

class AnnotationCanvasView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var currentPage: Int = 0
    private val pageToHistory: LinkedHashMap<Int, MutableList<Stroke>> = linkedMapOf()
    private val globalActionStack = mutableListOf<Triple<Int, Stroke, ActionType>>()
    private val globalRedoStack   = mutableListOf<Triple<Int, Stroke, ActionType>>()
    private val MAX_STROKES_PER_PAGE = 100
    private val MAX_GLOBAL_ACTIONS = 100
    private val MAX_PAGES_IN_MEMORY = 5

    private val imageTransformationMatrix = Matrix()
    private val inverseImageTransformationMatrix = Matrix()

    var onTextTapListener: ((Float, Float) -> Unit)? = null
    private var currentTool: Tool? = null
    private var customColor: Int = Color.RED
    private var customSize: Float = 5f

    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f

    /** 외부에서 color를 바꿀 때 호출 (CanvasToolController에서) */
    fun setCustomColor(color: Int) {
        customColor = color
    }

    /** 외부에서 size를 바꿀 때 호출 (CanvasToolController에서) */
    fun setCustomSize(size: Float) {
        customSize = size
    }

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
        if (globalRedoStack.size > MAX_GLOBAL_ACTIONS) {
                globalRedoStack.removeAt(0)
        }
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
        if (globalActionStack.size > MAX_GLOBAL_ACTIONS) {
                globalActionStack.removeAt(0)
        }
        invalidate()
        return true
    }
    fun peekUndo(): Triple<Int, Stroke, Any>? =
        globalActionStack.lastOrNull()

    fun peekRedo(): Triple<Int, Stroke, Any>? =
        globalRedoStack.lastOrNull()

    /** addText도 페이지별로 저장 */
    fun addText(text: String, x: Float, y: Float) {
        val history = pageToHistory.getOrPut(currentPage) { mutableListOf() }
        globalRedoStack.clear()
        val paint = makePaintFor(Tool.TEXT)
        val newStroke = Stroke.TextStroke(text, x, y, paint)

        history.add(newStroke)
        globalActionStack.add(Triple(currentPage, newStroke, ActionType.ADD))
        if (globalActionStack.size > MAX_GLOBAL_ACTIONS) {
                globalActionStack.removeAt(0)
        }
        invalidate()
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // ① 두 손가락 이상(핀치)이 감지되면 아래로 이벤트 전달
        if (ev.pointerCount > 1) {
            return false
        }

        val tool = currentTool ?: return false

        // 화면 좌표 → 모델 좌표 변환
        val touchPoint = floatArrayOf(ev.x, ev.y)
        inverseImageTransformationMatrix.mapPoints(touchPoint)
        val modelX = touchPoint[0]
        val modelY = touchPoint[1]

        val history = getHistoryForPage(currentPage)
        globalRedoStack.clear()

        when (tool) {
            Tool.PEN, Tool.HIGHLIGHTER -> {
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // ◀───────────────────────────────────────────────────◀
                        // 1) 새로운 Stroke.PathStroke 생성 (기존과 동일하되, lastTouch 초기화 추가)
                        val paint = makePaintFor(tool)
                        val path  = Path().apply { moveTo(modelX, modelY) }
                        val newStroke = Stroke.PathStroke(path, paint,
                            mutableListOf(PointF(modelX, modelY))
                        )
                        history.add(newStroke)
                        if (history.size > MAX_STROKES_PER_PAGE) {
                                history.removeAt(0)
                        }
                        globalActionStack.add(Triple(currentPage, newStroke, ActionType.ADD))

                        // 2) “이전 터치 위치”를 현재 위치로 초기화
                        lastTouchX = modelX
                        lastTouchY = modelY
                        // ◀───────────────────────────────────────────────────◀
                    }
                    MotionEvent.ACTION_MOVE -> (history.lastOrNull() as? Stroke.PathStroke)?.let { stroke ->
                        // ◀───────────────────────────────────────────────────◀
                        // 1) 이전 좌표와 현재 좌표의 중간 지점을 계산
                        val midX = (lastTouchX + modelX) / 2f
                        val midY = (lastTouchY + modelY) / 2f

                        // 2) quadTo를 사용해 베지어 곡선으로 매끄럽게 연결
                        stroke.path.quadTo(lastTouchX, lastTouchY, midX, midY)

                        // 3) points 리스트에도 (midX, midY)를 추가
                        stroke.points.add(PointF(midX, midY))

                        // 4) 이전 좌표를 현재 좌표로 업데이트
                        lastTouchX = modelX
                        lastTouchY = modelY
                        // ◀───────────────────────────────────────────────────◀
                    }
                    MotionEvent.ACTION_UP -> (history.lastOrNull() as? Stroke.PathStroke)?.let { stroke ->
                        // (선택 사항) 마지막에 깔끔하게 마무리: 마지막 좌표로 꼭 그려 주고 싶으면 아래처럼 lineTo
                        stroke.path.lineTo(lastTouchX, lastTouchY)
                        stroke.points.add(PointF(lastTouchX, lastTouchY))
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
                                if (intersectsPath(s.points, modelX, modelY, s.paint.strokeWidth)) {
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
                                if (bounds.contains(modelX, modelY)) {
                                    iter.remove()
                                    removed.add(s)
                                    erased = true
                                }
                            }
                        }
                    }
                    if (erased) {
                        removed.forEach { removedStroke ->
                            globalActionStack.add(Triple(currentPage, removedStroke, ActionType.REMOVE))
                            if (globalActionStack.size > MAX_GLOBAL_ACTIONS) {
                                    globalActionStack.removeAt(0)
                            }
                        }
                        globalRedoStack.clear()
                        invalidate()
                    }
                }
            }

            Tool.TEXT ->
                if (ev.action == MotionEvent.ACTION_DOWN)
                    onTextTapListener?.invoke(modelX, modelY)
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        pageToHistory[currentPage]?.forEach { stroke ->
            when (stroke) {
                is Stroke.PathStroke -> {
                    val rawPoints: List<PointF> = stroke.points
                    if (rawPoints.isNotEmpty()) {
                        // 1) 매끄러운 Bézier 곡선을 담을 새 Path 생성
                        val smoothPath = Path()

                        if (rawPoints.size < 3) {
                            // 점이 2개 미만이면 그냥 직선으로 잇기
                            smoothPath.moveTo(rawPoints[0].x, rawPoints[0].y)
                            for (i in 1 until rawPoints.size) {
                                smoothPath.lineTo(rawPoints[i].x, rawPoints[i].y)
                            }
                        } else {
                            // 점이 3개 이상이면 Catmull–Rom → Cubic Bézier 보간
                            smoothPath.moveTo(rawPoints[0].x, rawPoints[0].y)
                            for (i in 0 until rawPoints.size - 1) {
                                val p1 = rawPoints[i]
                                val p2 = rawPoints[i + 1]
                                val p0 = if (i - 1 >= 0) rawPoints[i - 1] else p1
                                val p3 = if (i + 2 < rawPoints.size) rawPoints[i + 2] else p2

                                val c1x = p1.x + (p2.x - p0.x) / 6f
                                val c1y = p1.y + (p2.y - p0.y) / 6f
                                val c2x = p2.x - (p3.x - p1.x) / 6f
                                val c2y = p2.y - (p3.y - p1.y) / 6f

                                smoothPath.cubicTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
                            }
                        }

                        // 2) “Path.transform(...)” 을 호출해서 화면 좌표로 변환
                        val transformedSmoothPath = Path()
                        smoothPath.transform(imageTransformationMatrix, transformedSmoothPath)

                        // 3) 변환된 Path를 그대로 그리기
                        canvas.drawPath(transformedSmoothPath, stroke.paint)
                    }
                }

                is Stroke.TextStroke -> {
                    // (텍스트는 기존과 동일하게 mapPoints → drawText)
                    val pt = floatArrayOf(stroke.x, stroke.y)
                    imageTransformationMatrix.mapPoints(pt)
                    canvas.drawText(stroke.text, pt[0], pt[1], stroke.paint)
                }
            }
        }
    }


    private fun makePaintFor(tool: Tool): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // ───────────────────────────────────────────────────────────────
        // 1) 기본 스타일: 텍스트는 FILL, 나머지는 STROKE
        // ───────────────────────────────────────────────────────────────
        style = if (tool == Tool.TEXT) Paint.Style.FILL else Paint.Style.STROKE

        // ───────────────────────────────────────────────────────────────
        // 2) 획 끝과 코너를 둥글게 처리하면 더 부드럽게 보입니다.
        // ───────────────────────────────────────────────────────────────
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND

        when (tool) {
            Tool.PEN -> {
                // ─────────────────────────────────────────────────────────
                // • pen 모드: customColor (불투명), strokeWidth = customSize
                // ─────────────────────────────────────────────────────────
                color = customColor
                strokeWidth = customSize
            }

            Tool.HIGHLIGHTER -> {
                // ─────────────────────────────────────────────────────────
                // • highlighter 모드:
                //   - customColor(RGB) 위에 적당한 투명도(Alpha)를 붙여서 덧칠 되도록 함
                //   - strokeWidth = customSize (이미 highlighterSize + 4f 로 넘겨받음)
                // ─────────────────────────────────────────────────────────

                // 예: 알파 0x44(약 27% 불투명)으로 고정
                val baseRgb = customColor and 0x00FFFFFF
                val translucent = (0x44 shl 24) or baseRgb
                color = translucent

                strokeWidth = customSize
                // 지우개 모드가 아니므로 xfermode는 설정하지 않습니다.
            }

            Tool.TEXT -> {
                // ─────────────────────────────────────────────────────────
                // • 텍스트: customColor(불투명), textSize = customSize
                // ─────────────────────────────────────────────────────────
                color = customColor
                textSize = customSize
            }

            Tool.ERASER -> {
                // ─────────────────────────────────────────────────────────
                // • 지우개: 투명 모드(CLEAR) → 해당 경로를 지움
                // ─────────────────────────────────────────────────────────
                color = Color.TRANSPARENT
                strokeWidth = customSize
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
        }
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
    private fun getHistoryForPage(page: Int): MutableList<Stroke> {
        // 이미 존재하는 페이지라면 순서를 갱신
        pageToHistory[page]?.let { existingList ->
            pageToHistory.remove(page)
            pageToHistory[page] = existingList
            return existingList
        }

        // LRU 기준으로 “가장 오래된 페이지”가 있으면 제거
        if (pageToHistory.size >= MAX_PAGES_IN_MEMORY) {
            val oldestKey = pageToHistory.keys.first()
            pageToHistory.remove(oldestKey)
            // 필요시 디스크 저장/플래튼 로직 호출
        }
        val newList = mutableListOf<Stroke>()
        pageToHistory[page] = newList
        return newList
    }


    fun setTransformationMatrix(matrix: Matrix) {
        imageTransformationMatrix.set(matrix)
        imageTransformationMatrix.invert(inverseImageTransformationMatrix)
        invalidate()
    }

    fun clearAll() {
        pageToHistory.clear()
        globalActionStack.clear()
        globalRedoStack.clear()
        invalidate()
    }
}
