package com.example.focustrace.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class CountdownRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val strokeWidthPx = 4f * resources.displayMetrics.density
    private val arcBounds = RectF()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EAF5DF")
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#88C46A")
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.BUTT
    }

    private var remainingFraction = 1f

    fun setRemainingFraction(fraction: Float) {
        remainingFraction = fraction.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val availableWidth = (width - paddingLeft - paddingRight).toFloat()
        val availableHeight = (height - paddingTop - paddingBottom).toFloat()
        val diameter = minOf(availableWidth, availableHeight) - strokeWidthPx

        if (diameter <= 0f) return

        val centerX = paddingLeft + availableWidth / 2f
        val centerY = paddingTop + availableHeight / 2f
        val radius = diameter / 2f

        arcBounds.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )

        canvas.drawOval(arcBounds, trackPaint)

        // The disappearing edge moves counterclockwise from 12 o'clock.
        if (remainingFraction > 0f) {
            canvas.drawArc(
                arcBounds,
                -90f,
                360f * remainingFraction,
                false,
                progressPaint
            )
        }
    }
}