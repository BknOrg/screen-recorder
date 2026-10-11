package com.system.screenrecorder.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.MotionEvent
import android.view.View

class ScreenDoodleView(context: Context) : View(context) {

    data class DrawAction(
        val path: Path,
        val color: Int,
        val strokeWidth: Float,
        val isEraser: Boolean
    )

    private val actions = mutableListOf<DrawAction>()
    private var currentPath = Path()
    private var currentColor = Color.parseColor("#FF3B30") // One UI vibrant red default
    private var currentStrokeWidth = 12f
    private var isEraserMode = false

    private val drawPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val eraserPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setColor(color: Int) {
        currentColor = color
        isEraserMode = false
    }

    fun setEraser(enable: Boolean) {
        isEraserMode = enable
    }

    fun isEraser(): Boolean = isEraserMode

    fun undo() {
        if (actions.isNotEmpty()) {
            actions.removeAt(actions.size - 1)
            invalidate()
        }
    }

    fun clear() {
        actions.clear()
        currentPath.reset()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (action in actions) {
            val paint = if (action.isEraser) {
                eraserPaint.apply { strokeWidth = action.strokeWidth * 2f }
            } else {
                drawPaint.apply {
                    color = action.color
                    strokeWidth = action.strokeWidth
                }
            }
            canvas.drawPath(action.path, paint)
        }

        // Draw active path
        val activePaint = if (isEraserMode) {
            eraserPaint.apply { strokeWidth = currentStrokeWidth * 2f }
        } else {
            drawPaint.apply {
                color = currentColor
                strokeWidth = currentStrokeWidth
            }
        }
        canvas.drawPath(currentPath, activePaint)
    }

    private var startX = 0f
    private var startY = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath.reset()
                currentPath.moveTo(x, y)
                startX = x
                startY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = kotlin.math.abs(x - startX)
                val dy = kotlin.math.abs(y - startY)
                if (dx >= 4 || dy >= 4) {
                    currentPath.quadTo(startX, startY, (x + startX) / 2, (y + startY) / 2)
                    startX = x
                    startY = y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                currentPath.lineTo(x, y)
                actions.add(
                    DrawAction(
                        path = Path(currentPath),
                        color = currentColor,
                        strokeWidth = currentStrokeWidth,
                        isEraser = isEraserMode
                    )
                )
                currentPath.reset()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
