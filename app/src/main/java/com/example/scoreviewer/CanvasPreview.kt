package com.example.scoreviewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * CanvasPreview:
 *  - toolType(PEN / HIGHLIGHTER / TEXT)에 따라
 *    미리보기용 선 또는 텍스트를 그려줍니다.
 *  - 외부에서 previewColor, previewSize, toolType을 설정하면 invalidate()하여 다시 그림.
 */
class CanvasPreview @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class ToolType { PEN, HIGHLIGHTER, TEXT }

    var previewColor: Int = Color.RED
        set(value) {
            field = value
            invalidate()
        }

    var previewSize: Float = 48f
        set(value) {
            field = value
            invalidate()
        }

    var toolType: ToolType = ToolType.PEN
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerY = height / 2f
        val centerX = width / 2f

        when (toolType) {
            ToolType.PEN -> {
                paint.apply {
                    color = previewColor
                    strokeWidth = previewSize
                    style = Paint.Style.STROKE
                    alpha = 255
                }
                canvas.drawLine(100f, centerY, width - 100f, centerY, paint)
            }
            ToolType.HIGHLIGHTER -> {
                paint.apply {
                    color = previewColor
                    strokeWidth = previewSize
                    style = Paint.Style.STROKE
                    alpha = 100
                }
                canvas.drawLine(100f, centerY, width - 100f, centerY, paint)
            }
            ToolType.TEXT -> {
                paint.apply {
                    color = previewColor
                    textSize = previewSize
                    style = Paint.Style.FILL
                    alpha = 255
                }
                canvas.drawText("ABC abc 123", centerX, centerY + previewSize / 2f, paint)
            }
        }
    }
}
