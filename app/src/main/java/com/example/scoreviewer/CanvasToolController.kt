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
    private val btnIncreaseSize: Button,
    private val btnDecreaseSize: Button,
    private val canvasPreviewColor: View,
    private val canvasPreviewSize: TextView,

    private val colorPicker: ColorPicker
) {

    /** 현재 선택된 Tool (기본 PEN) */
    private var currentTool: Tool = Tool.PEN
    private var selectedColor: Int = Color.RED
    private var selectedSize: Float = 48f
    private lateinit var canvasPreview: CanvasPreview

    init {
        // 앱 시작 시 AnnotationCanvasView에 기본 도구 전달 (1단계에서는 실제 반영해도 무방함)
        annotationCanvas.setTool(currentTool)
        btnCanvas.setImageResource(getIconResForTool(currentTool))

        // 툴 버튼 클릭 시
        btnPen.setOnClickListener { selectTool(Tool.PEN) }
        btnHighlighter.setOnClickListener { selectTool(Tool.HIGHLIGHTER) }
        btnText.setOnClickListener { selectTool(Tool.TEXT) }
        btnEraser.setOnClickListener { selectTool(Tool.ERASER) }

        // 크기 증가/감소 버튼 클릭 → selectedSize 변경, UI 갱신, 프리뷰 갱신
        btnIncreaseSize.setOnClickListener {
            selectedSize += 4f
            updateSizeUI()
            updatePreview()
        }
        btnDecreaseSize.setOnClickListener {
            selectedSize = maxOf(4f, selectedSize - 4f)
            updateSizeUI()
            updatePreview()
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
                selectedColor = color
                updateColorUI()
                updatePreview()
            }
        })

        // 패널 외부 클릭 시 포커스 아웃 → 패널 숨김
        panelContainer.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                hidePanel()
            }
        }
    }

    /** ② 프리뷰 뷰 연결 메서드 (MainActivity에서 onCreate 시 호출) */
    fun bindPreview(previewView: CanvasPreview) {
        this.canvasPreview = previewView
        updatePreview()
    }

    /** 툴 선택 로직. 버튼 아이콘 변경 + (1단계) 프리뷰만 갱신 */
    private fun selectTool(tool: Tool) {
        currentTool = tool
        annotationCanvas.setTool(currentTool)
        btnCanvas.setImageResource(getIconResForTool(currentTool))
        updateToolButtonUI()
        updatePreview()
    }

    /** ③ 버튼/툴 변경 시 호출하여 프리뷰를 갱신 */
    private fun updatePreview() {
        if (!::canvasPreview.isInitialized) return

        canvasPreview.apply {
            previewColor = selectedColor
            previewSize = selectedSize
            toolType = when (currentTool) {
                Tool.PEN         -> CanvasPreview.ToolType.PEN
                Tool.HIGHLIGHTER -> CanvasPreview.ToolType.HIGHLIGHTER
                Tool.TEXT        -> CanvasPreview.ToolType.TEXT
                else             -> CanvasPreview.ToolType.PEN
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

    /** 현재 선택된 도구 가져오기 */
    fun getCurrentTool(): Tool = currentTool

    /** 사이즈 레이블(TextView)의 텍스트를 숫자 (selectedSize) 로 갱신 */
    private fun updateSizeUI() {
        // 변수명이 canvasPreviewSize이므로, 바로 텍스트를 덮어씁니다.
        canvasPreviewSize.text = selectedSize.toInt().toString()
    }

    /** 색상 선택 뷰 배경을 선택된 색상으로 바꿉니다. */
    private fun updateColorUI() {
        canvasPreviewColor.setBackgroundColor(selectedColor)
    }

    /** 툴 버튼 UI(선택된 버튼 강조 등)를 업데이트합니다. */
    private fun updateToolButtonUI() {
        btnPen.isSelected         = (currentTool == Tool.PEN)
        btnHighlighter.isSelected = (currentTool == Tool.HIGHLIGHTER)
        btnText.isSelected        = (currentTool == Tool.TEXT)
        btnEraser.isSelected      = (currentTool == Tool.ERASER)
    }
}
