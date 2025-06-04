package com.example.scoreviewer

import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.isVisible
import com.larswerkman.holocolorpicker.ColorPicker

/**
 * CanvasToolController:
 *  - AnnotationCanvasView에 현재 선택된 Tool을 전달
 *  - 툴 패널 열기/닫기 및 btnCanvas 아이콘 갱신 관리
 *  - 1단계: 프리뷰(PreviewCanvasView)와 연결하여, 색상/크기/도구 변경 시 미리보기만 갱신
 *
 *  * 주의 *
 *  - 실제 annotationCanvas에는 1단계에서 값을 반영하지 않음.
 */
class CanvasToolController(
    private val annotationCanvas: AnnotationCanvasView,
    private val btnCanvas: CanvasToggleButton,
    private val panelContainer: View,

    // 툴 버튼
    private val btnPen: ImageButton,
    private val btnHighlighter: ImageButton,
    private val btnText: ImageButton,
    private val btnEraser: ImageButton,

    // 크기/색상 조절 뷰 (ID가 레이아웃 상에 존재하는 것과 동일하게 맞춤)
    private val btnIncreaseSize: ImageButton,
    private val btnDecreaseSize: ImageButton,
    private val canvasPreviewColor: View,
    private val canvasPreviewSize: TextView,

    private val colorPicker: ColorPicker
) {

    /** 현재 선택된 Tool (기본 PEN) */
    private var currentTool: Tool = Tool.PEN
    private lateinit var canvasPreview: CanvasPreview
    private var penColor: Int = Color.RED
    private var penSize: Float = 5f
    private var highlighterColor: Int = Color.YELLOW
    private var highlighterSize: Float = 5f
    private var textColor: Int = Color.RED
    private var textSize: Float = 48f

    init {
        // 앱 시작 시 AnnotationCanvasView에 기본 도구 전달 (1단계에서는 실제 반영해도 무방함)
        annotationCanvas.setTool(null)
        btnCanvas.setImageResource(getIconResForTool(currentTool))
        btnCanvas.alpha = 1f

        // 툴 버튼 클릭 시
        btnPen.setOnClickListener { selectTool(Tool.PEN) }
        btnHighlighter.setOnClickListener { selectTool(Tool.HIGHLIGHTER) }
        btnText.setOnClickListener { selectTool(Tool.TEXT) }
        btnEraser.setOnClickListener { selectTool(Tool.ERASER) }

        // 크기 증가/감소 버튼 클릭 → selectedSize 변경, UI 갱신, 프리뷰 갱신
        btnIncreaseSize.setOnClickListener {
            when (currentTool) {
                    Tool.PEN         -> penSize += 4f
                    Tool.HIGHLIGHTER -> highlighterSize += 4f
                    Tool.TEXT        -> textSize += 4f
                    else             -> { /* ERASER: 동작 없음 */ }
                }
            updateSizeUI()
            updatePreviewAndCanvas()
        }
        btnDecreaseSize.setOnClickListener {
            when (currentTool) {
                    Tool.PEN         -> penSize = maxOf(1f, penSize - 4f)
                    Tool.HIGHLIGHTER -> highlighterSize = maxOf(1f, highlighterSize - 4f)
                    Tool.TEXT        -> textSize = maxOf(8f, textSize - 4f)
                    else             -> { /* ERASER: 동작 없음 */ }
                }
            updateSizeUI()
            updatePreviewAndCanvas()
        }

        canvasPreviewColor.setOnClickListener {
            if (colorPicker.isVisible) {
                colorPicker.visibility = View.GONE
            } else {
                colorPicker.visibility = View.VISIBLE
            }
        }
        colorPicker.setOnColorChangedListener(object : ColorPicker.OnColorChangedListener {
            override fun onColorChanged(color: Int) {
                    when (currentTool) {
                            Tool.PEN         -> penColor = color
                            Tool.HIGHLIGHTER -> highlighterColor = color
                            Tool.TEXT        -> textColor = color
                            else             -> { /* ERASER: 무시 */ }
                        }
                    canvasPreviewColor.setBackgroundColor(color)
                    updatePreviewAndCanvas()
                }
        })

        // 패널 외부 클릭 시 포커스 아웃 → 패널 숨김
        panelContainer.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                hidePanel()
            }
        }

        updateSizeUI()
        updateColorUI()
        updatePreviewAndCanvas()
    }

    /** 프리뷰 뷰 연결 메서드 (MainActivity에서 onCreate 시 호출) */
    fun bindPreview(previewView: CanvasPreview) {
        this.canvasPreview = previewView
        updatePreviewAndCanvas()
    }

    /** 툴 선택 로직. 버튼 아이콘 변경 + (1단계) 프리뷰만 갱신 */
    private fun selectTool(tool: Tool) {
        currentTool = tool
        annotationCanvas.setTool(currentTool)
        btnCanvas.setImageResource(getIconResForTool(currentTool))
        updateToolButtonUI()
        updateSizeUI()
        updateColorUI()
        when (currentTool) {
            Tool.PEN         -> colorPicker.setColor(penColor)
            Tool.HIGHLIGHTER -> colorPicker.setColor(highlighterColor)
            Tool.TEXT        -> colorPicker.setColor(textColor)
            else             -> { /* ERASER일 때는 컬러 휠 숨겨도 상관없음 */ }
        }
        updatePreviewAndCanvas()
    }

    /** 버튼/툴 변경 시 호출하여 프리뷰&캔버스 갱신 */
    private fun updatePreviewAndCanvas() {
        if (!::canvasPreview.isInitialized) return

        when (currentTool) {
            Tool.PEN -> {
                // 1) Preview: 원형 스트로크
                canvasPreview.visibility = View.VISIBLE
                canvasPreview.apply {
                    previewColor = penColor
                    previewSize  = penSize
                    toolType     = CanvasPreview.ToolType.PEN
                }
                // 2) 실제 캔버스 반영
                annotationCanvas.setCustomColor(penColor)
                annotationCanvas.setCustomSize(penSize)
            }

            Tool.HIGHLIGHTER -> {
                // Preview는 (highlighterSize + 4f) 두께
                val dispSize = highlighterSize + 4f
                canvasPreview.visibility = View.VISIBLE
                canvasPreview.apply {
                    previewColor = highlighterColor
                    previewSize  = dispSize
                    toolType     = CanvasPreview.ToolType.HIGHLIGHTER
                }
                // 실제 캔버스에는 (highlighterSize + 4f) 넘겨줌
                annotationCanvas.setCustomColor(highlighterColor)
                annotationCanvas.setCustomSize(highlighterSize + 4f)
            }

            Tool.TEXT -> {
                canvasPreview.visibility = View.VISIBLE
                canvasPreview.apply {
                    previewColor = textColor
                    previewSize  = textSize
                    toolType     = CanvasPreview.ToolType.TEXT
                }
                annotationCanvas.setCustomColor(textColor)
                annotationCanvas.setCustomSize(textSize)
            }

            Tool.ERASER -> {
                // 미리보기 숨기기
                canvasPreview.visibility = View.GONE
                // 지우개 모드(AnnotationCanvasView 내부가 자동으로 처리)
            }
        }
    }

    /** Tool enum 값에 대응하는 Drawable 리소스 ID 반환 */
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

    /** 패널 토글: 열려 있으면 닫고, 닫혀 있으면 엽니다. */
    fun togglePanel() {
        if (panelContainer.isVisible) hidePanel() else showPanel()
    }

    /** 가져오기 */
    fun getCurrentTool(): Tool = currentTool
    fun getTextColor(): Int = textColor
    fun getTextSize(): Float = textSize

    /** 사이즈 갱신 */
    private fun updateSizeUI() {
        canvasPreviewSize.text = when (currentTool) {
                Tool.PEN         -> penSize.toInt().toString()
                Tool.HIGHLIGHTER -> highlighterSize.toInt().toString()
                Tool.TEXT        -> textSize.toInt().toString()
                Tool.ERASER      -> "-"  // 지우개는 크기 표시 없음
            }
    }

    /** 색상 선택 뷰 배경을 선택된 색상으로 바꿉니다. */
    private fun updateColorUI() {
        val c = when (currentTool) {
                Tool.PEN         -> penColor
                Tool.HIGHLIGHTER -> highlighterColor
                Tool.TEXT        -> textColor
                Tool.ERASER      -> Color.TRANSPARENT
        }
        colorPicker.setOldCenterColor(c)
        colorPicker.setColor(c)
        canvasPreviewColor.setBackgroundColor(c)
    }

    /** 툴 버튼 UI(선택된 버튼 강조 등)를 업데이트합니다. */
    private fun updateToolButtonUI() {
        btnPen.isSelected         = (currentTool == Tool.PEN)
        btnHighlighter.isSelected = (currentTool == Tool.HIGHLIGHTER)
        btnText.isSelected        = (currentTool == Tool.TEXT)
        btnEraser.isSelected      = (currentTool == Tool.ERASER)
    }
}
