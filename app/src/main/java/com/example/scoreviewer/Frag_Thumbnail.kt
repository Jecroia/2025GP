package com.example.scoreviewer

import android.graphics.Bitmap
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import androidx.fragment.app.Fragment

class Frag_Thumbnail : Fragment() {

    private lateinit var thumbnailImageView: ImageView
    private var rootView: View? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        rootView = inflater.inflate(R.layout.fragment_thumbnail, container, false)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        thumbnailImageView = view.findViewById(R.id.thumbnailImageView)
    }

    fun updateThumbnail(bitmap: Bitmap, xPosition: Int, yPosition: Int) {
        if (!this::thumbnailImageView.isInitialized || rootView == null) return

        thumbnailImageView.setImageBitmap(bitmap)

        thumbnailImageView.post {
            val screenWidth = resources.displayMetrics.widthPixels
            val halfWidth = thumbnailImageView.width / 2f
            val fullHeight = thumbnailImageView.height

            var adjustedX = (xPosition - halfWidth)
            val leftBound = 0f
            val rightBound = (screenWidth - thumbnailImageView.width).toFloat()
            adjustedX = adjustedX.coerceIn(leftBound, rightBound)

            thumbnailImageView.translationX = adjustedX
            rootView?.translationY = (yPosition - fullHeight - 10).toFloat()
        }
    }
}


