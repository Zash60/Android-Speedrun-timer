package com.example.floatingspeedruntimer.ui.autosplit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class ResizableRectangleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs) {

    private val borderPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }
    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#50000000") // Fundo escuro semi-transparente
        style = Paint.Style.FILL
    }
    private val handlePaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.FILL
    }
    private val handleSize = 40f
    private var currentRect = RectF(100f, 100f, 400f, 300f)

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchMode = TouchMode.NONE

    private enum class TouchMode { NONE, DRAG, RESIZE_BR }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Desenha o fundo escuro FORA do retângulo
        canvas.drawPath(
            android.graphics.Path().apply {
                addRect(0f, 0f, width.toFloat(), height.toFloat(), android.graphics.Path.Direction.CW)
                addRect(currentRect, android.graphics.Path.Direction.CCW)
            },
            backgroundPaint
        )
        // Desenha a borda do retângulo
        canvas.drawRect(currentRect, borderPaint)
        // Desenha o "cabo" de redimensionamento no canto inferior direito
        canvas.drawCircle(currentRect.right, currentRect.bottom, handleSize / 2, handlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = x
                lastTouchY = y
                val resizeHandle = RectF(
                    currentRect.right - handleSize, currentRect.bottom - handleSize,
                    currentRect.right + handleSize, currentRect.bottom + handleSize
                )
                if (resizeHandle.contains(x, y)) {
                    touchMode = TouchMode.RESIZE_BR
                } else if (currentRect.contains(x, y)) {
                    touchMode = TouchMode.DRAG
                } else {
                    touchMode = TouchMode.NONE
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastTouchX
                val dy = y - lastTouchY
                when (touchMode) {
                    TouchMode.DRAG -> currentRect.offset(dx, dy)
                    TouchMode.RESIZE_BR -> {
                        currentRect.right += dx
                        currentRect.bottom += dy
                        // Garante um tamanho mínimo
                        if (currentRect.width() < 100) currentRect.right = currentRect.left + 100
                        if (currentRect.height() < 100) currentRect.bottom = currentRect.top + 100
                    }
                    TouchMode.NONE -> return false
                }
                
                // Mantém o retângulo dentro dos limites da tela
                currentRect.left = currentRect.left.coerceIn(0f, (width - currentRect.width()).toFloat())
                currentRect.right = currentRect.right.coerceIn(currentRect.width(), width.toFloat())
                currentRect.top = currentRect.top.coerceIn(0f, (height - currentRect.height()).toFloat())
                currentRect.bottom = currentRect.bottom.coerceIn(currentRect.height(), height.toFloat())

                lastTouchX = x
                lastTouchY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                touchMode = TouchMode.NONE
                return true
            }
        }
        return false
    }

    fun getRectCoordinates(): RectF = currentRect

    fun setRectCoordinates(rect: RectF?) {
        if (rect != null) {
            currentRect = rect
        }
        invalidate()
    }
}
