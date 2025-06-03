package com.example.scoreviewer

import android.view.View
import android.widget.ImageButton
import androidx.core.view.isVisible

/**
 * CanvasToolController:
 *  AnnotationCanvasView에 현재 선택된 Tool을 전달
 *  툴 패널 열기/닫기 및 btnCanvas 아이콘 갱신 관리
 * Tool에 대응하는 Drawable 리소스 ID를 리턴합니다.
 */

class CanvasToolController(
    private val annotationCanvas: AnnotationCanvasView,
    private val btnCanvas: CanvasToggleButton,
    private val panelContainer: View,
    btnPen: ImageButton,
    btnHighlighter: ImageButton,
    btnText: ImageButton,
    btnEraser: ImageButton
) {
    private var currentTool: Tool = Tool.PEN

    init {
        // 앱 시작 시 AnnotationCanvasView에 기본 도구(PEN) 전달
        annotationCanvas.setTool(currentTool)
        // btnCanvas 아이콘도 PEN으로 초기화
        btnCanvas.setImageResource(getIconResForTool(currentTool))

        // 도구 버튼 클릭 시 selectTool(...) 호출
        btnPen.setOnClickListener {
            selectTool(Tool.PEN)
        }
        btnHighlighter.setOnClickListener {
            selectTool(Tool.HIGHLIGHTER)
        }
        btnText.setOnClickListener {
            selectTool(Tool.TEXT)
        }
        btnEraser.setOnClickListener {
            selectTool(Tool.ERASER)
        }

        // 패널 외부를 터치해서 포커스가 빠질 때 패널을 닫고, currentTool 유지
        panelContainer.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                hidePanel()
            }
        }
    }

    /** 사용자가 패널에서 도구를 선택했을 때 호출 */
    private fun selectTool(tool: Tool) {
        currentTool = tool
        annotationCanvas.setTool(currentTool)
        btnCanvas.setImageResource(getIconResForTool(currentTool))
        hidePanel()
    }

    /** Tool enum 값에 대응하는 Drawable 리소스 ID를 반환 */
    private fun getIconResForTool(tool: Tool): Int {
        return when (tool) {
            Tool.PEN         -> R.drawable.tool_pen_24
            Tool.HIGHLIGHTER -> R.drawable.tool_highlight_24
            Tool.TEXT        -> R.drawable.tool_text_24
            Tool.ERASER      -> R.drawable.tool_eraser_24
        }
    }

    /** 툴 설정 패널 보이기 */
    fun showPanel() {
        panelContainer.visibility = View.VISIBLE
        panelContainer.isFocusableInTouchMode = true
        panelContainer.requestFocus()
    }

    /** 툴 설정 패널 숨기기 */
    fun hidePanel() {
        panelContainer.visibility = View.GONE
        panelContainer.clearFocus()
    }

    /** 패널 토글: 보이면 닫고, 닫혀 있으면 연다 */
    fun togglePanel() {
        if (panelContainer.isVisible) hidePanel() else showPanel()
    }

    /** 외부에서 현재 선택된 도구 값을 가져갈 때 사용 */
    fun getCurrentTool(): Tool = currentTool
}
