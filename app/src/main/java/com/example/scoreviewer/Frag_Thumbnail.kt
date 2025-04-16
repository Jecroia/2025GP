package com.example.scoreviewer

import android.graphics.Bitmap
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import androidx.fragment.app.Fragment

class Frag_Thumbnail : Fragment() {

    private lateinit var thumbnailImageView: ImageView
    private var rootView: View? = null

    private var initialX = 0
    private var initialY = 0

    companion object {
        fun newInstance(x: Int, y: Int): Frag_Thumbnail {
            val fragment = Frag_Thumbnail()
            val args = Bundle()
            args.putInt("x", x)
            args.putInt("y", y)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            initialX = it.getInt("x")
            initialY = it.getInt("y")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        rootView = inflater.inflate(R.layout.fragment_thumbnail, container, false)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        thumbnailImageView = view.findViewById(R.id.thumbnailImageView)
        // 초기 위치는 onPreDrawListener를 통해 한 번만 설정
        thumbnailImageView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                thumbnailImageView.viewTreeObserver.removeOnPreDrawListener(this)
                applyTranslation(initialX, initialY)
                return true
            }
        })
    }

    /**
     * updateThumbnail은 전달받은 비트맵과 (x, y) 좌표를 기반으로 썸네일의 이미지와 위치를 갱신합니다.
     * 뷰의 크기가 아직 측정되지 않은 경우 OnPreDrawListener를 추가하여 적용합니다.
     */
    fun updateThumbnail(bitmap: Bitmap, xPosition: Int, yPosition: Int) {
        if (!this::thumbnailImageView.isInitialized || rootView == null) return

        thumbnailImageView.setImageBitmap(bitmap)

        if (thumbnailImageView.width == 0 || thumbnailImageView.height == 0) {
            thumbnailImageView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    thumbnailImageView.viewTreeObserver.removeOnPreDrawListener(this)
                    applyTranslation(xPosition, yPosition)
                    return true
                }
            })
        } else {
            applyTranslation(xPosition, yPosition)
        }
    }

    /**
     * applyTranslation 계산된 x, y 좌표를 바탕으로 ImageView의 translationX와
     * 부모(rootView)의 translationY를 적용합니다.
     */
    private fun applyTranslation(xPosition: Int, yPosition: Int) {
        val screenWidth = resources.displayMetrics.widthPixels
        val halfWidth = thumbnailImageView.width / 2f

        var adjustedX = (xPosition - halfWidth)
        val leftBound = 0f
        val rightBound = (screenWidth - thumbnailImageView.width).toFloat()
        adjustedX = adjustedX.coerceIn(leftBound, rightBound)

        thumbnailImageView.translationX = adjustedX
        // yPosition은 MainActivity에서 계산해서 넘겨줌 (예: SeekBar 하단 + offset)
        rootView?.translationY = yPosition.toFloat()
    }
}


