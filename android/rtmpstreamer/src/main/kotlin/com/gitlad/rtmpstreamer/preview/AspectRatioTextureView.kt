package com.gitlad.rtmpstreamer.preview

import android.content.Context
import android.util.AttributeSet
import android.view.TextureView

class AspectRatioTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextureView(context, attrs) {

    fun setAspectRatio(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
}
