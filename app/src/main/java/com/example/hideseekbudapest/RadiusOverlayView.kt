package com.example.hideseekbudapest

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt

class RadiusOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // When this changes, it tells the view to redraw itself immediately
    var radiusPixels: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    // The brush used to draw the dashed circle
    private val paint = Paint().apply {
        color = "#0000FF".toColorInt() // Blue
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
        // 20 pixels drawn, 20 pixels empty
        pathEffect = DashPathEffect(floatArrayOf(20f, 20f), 0f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Only draw if we have a valid radius
        if (radiusPixels > 0f) {
            val centerX = width / 2f
            val centerY = height / 2f
            canvas.drawCircle(centerX, centerY, radiusPixels, paint)
        }
    }
}