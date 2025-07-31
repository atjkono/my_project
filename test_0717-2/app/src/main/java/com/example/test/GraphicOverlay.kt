package com.example.test

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class GraphicOverlay(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val gridPaint = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val selectedPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        alpha = 180  // 約70%透過
    }

    private val cellRects = mutableMapOf<Int, Rect>()
    private val selectedCells = mutableSetOf<Int>()

    private var cellWidth = 0
    private var cellHeight = 0

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        cellWidth = width / 3
        cellHeight = height / 3

        for (row in 0 until 3) {
            for (col in 0 until 3) {
                val index = row * 3 + col
                val left = col * cellWidth
                val top = row * cellHeight
                val right = left + cellWidth
                val bottom = top + cellHeight
                val rect = Rect(left, top, right, bottom)
                cellRects[index] = rect

                if (selectedCells.contains(index)) {
                    canvas.drawRect(rect, selectedPaint)
                }
                canvas.drawRect(rect, gridPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val col = (event.x / cellWidth).toInt()
            val row = (event.y / cellHeight).toInt()
            val index = row * 3 + col
            if (index in 0..8) {
                if (selectedCells.contains(index)) {
                    selectedCells.remove(index)
                } else {
                    selectedCells.add(index)
                }
                invalidate()
            }
        }
        return true
    }

    fun getSelectedCells(): Map<Int, Rect> {
        return selectedCells.associateWith { cellRects[it] ?: Rect(0, 0, 0, 0) }
    }
}
