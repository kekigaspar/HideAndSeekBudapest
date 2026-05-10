package com.example.hideseekbudapest

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class HotColdOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var previousPoint: PointF? = null
    var currentPoint: PointF? = null
    var isHotter: Boolean = true

    fun updatePoints(prev: PointF?, curr: PointF?, hotter: Boolean) {
        previousPoint = prev
        currentPoint = curr
        isHotter = hotter
        invalidate()
    }

    private val linePaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val bisectorPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p1 = previousPoint ?: return
        val p2 = currentPoint ?: return

        // Draw line between previous and current
        canvas.drawLine(p1.x, p1.y, p2.x, p2.y, linePaint)

        // Calculate midpoint
        val midX = (p1.x + p2.x) / 2f
        val midY = (p1.y + p2.y) / 2f

        // Calculate perpendicular slope direction (dx, dy)
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y

        // Perpendicular vector (-dy, dx), scaled up to act as a cutting line
        val perpX = -dy * 10f
        val perpY = dx * 10f

        // Draw the bisector
        canvas.drawLine(midX - perpX, midY - perpY, midX + perpX, midY + perpY, bisectorPaint)

        // (Optional: Draw an arrow or shade a side based on `isHotter` here)
    }
}