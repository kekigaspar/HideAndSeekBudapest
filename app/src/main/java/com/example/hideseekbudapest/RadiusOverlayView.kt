package com.example.hideseekbudapest

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class RadiusOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var radiusPixels: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    // NEW: Tracks the state of the "Shade Inside" switch
    var isInside: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private val linePaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(20f, 20f), 0f)
    }

    // NEW: The brush used for the semi-transparent red shading
    private val fillPaint = Paint().apply {
        color = Color.parseColor("#44FF0000") // 26% opacity Red
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (radiusPixels > 0f) {
            val centerX = width / 2f
            val centerY = height / 2f

            if (isInside) {
                // Shade the inside
                canvas.drawCircle(centerX, centerY, radiusPixels, fillPaint)
            } else {
                // Shade the outside by drawing a full screen with a hole punched in it
                val path = Path().apply {
                    addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
                    addCircle(centerX, centerY, radiusPixels, Path.Direction.CCW)
                }
                canvas.drawPath(path, fillPaint)
            }

            // Always draw the dashed outline on top
            canvas.drawCircle(centerX, centerY, radiusPixels, linePaint)
        }
    }
}