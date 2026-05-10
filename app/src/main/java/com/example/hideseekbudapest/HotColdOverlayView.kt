package com.example.hideseekbudapest

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.hypot

class HotColdOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var previousPoint: PointF? = null
    private var currentPoint: PointF? = null
    private var isHotter: Boolean = true

    fun updatePoints(prev: PointF?, curr: PointF?, hotter: Boolean) {
        previousPoint = prev
        currentPoint = curr
        isHotter = hotter
        invalidate()
    }

    private val linePaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 6f
        pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
    }

    private val bisectorPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    // NEW: Shading brush
    private val fillPaint = Paint().apply {
        color = Color.parseColor("#44FF0000")
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p1 = previousPoint ?: return
        val p2 = currentPoint ?: return

        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        if (distance == 0f) return // Prevent division by zero

        // Normalize the vector so we can draw off the edges of the screen
        val nx = dx / distance
        val ny = dy / distance

        val midX = (p1.x + p2.x) / 2f
        val midY = (p1.y + p2.y) / 2f

        // The perpendicular bisector vector, scaled massively to cross the screen
        val perpX = -ny * 4000f
        val perpY = nx * 4000f

        // The forward vector pointing into the shaded zone
        val directionMultiplier = if (isHotter) 1f else -1f
        val forwardX = nx * directionMultiplier * 4000f
        val forwardY = ny * directionMultiplier * 4000f

        // Create the massive rectangular polygon for the shaded half-plane
        val path = Path().apply {
            moveTo(midX - perpX, midY - perpY) // Start at bisector left
            lineTo(midX + perpX, midY + perpY) // Draw bisector line to right
            lineTo(midX + perpX + forwardX, midY + perpY + forwardY) // Deep into shaded zone
            lineTo(midX - perpX + forwardX, midY - perpY + forwardY) // Across the bottom
            close() // Back to start
        }

        // Draw the shading first (on the bottom)
        canvas.drawPath(path, fillPaint)

        // Draw the connection line and the bisector on top
        canvas.drawLine(p1.x, p1.y, p2.x, p2.y, linePaint)
        canvas.drawLine(midX - perpX, midY - perpY, midX + perpX, midY + perpY, bisectorPaint)
    }
}