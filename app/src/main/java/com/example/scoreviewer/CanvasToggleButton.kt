
package com.example.scoreviewer

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageButton

/**
 * 캔버스 토글 버튼 전용 커스텀 ImageButton.
 * performClick()을 반드시 오버라이드하여,
 * 터치 이벤트 외 접근성(접근성 서비스, 키보드 등)에서도 클릭이 정상 동작하도록 보장합니다.
 */
class CanvasToggleButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageButton(context, attrs) {

    /**
     * performClick()을 오버라이드하면,
     * - OnClickListener가 연결되어 있을 때 호출됨
     * - 접근성 서비스가 클릭 이벤트를 보낼 때도 여기로 들어옴
     */
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
