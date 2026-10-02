package dev.danger.companion.face

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import dev.danger.companion.CompanionState
import dev.danger.companion.Emotion
import kotlin.math.min

object EyeRenderer {

    private const val SCLERA_COLOR = 0xFFF5F7FA.toInt()

    fun render(state: CompanionState, widthPx: Int, heightPx: Int): Bitmap {
        val width = widthPx.coerceAtLeast(1)
        val height = heightPx.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = width.toFloat()
        val h = height.toFloat()

        val color = state.colorArgb

        val eyeR = min(w, h) * 0.16f
        val cx = w / 2f
        val cy = h / 2f
        val dx = w * 0.20f
        val leftX = cx - dx
        val rightX = cx + dx

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = min(w, h) * 0.055f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val sclera = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = SCLERA_COLOR
            style = Paint.Style.FILL
        }

        when (state.emotion) {
            Emotion.NEUTRAL -> {
                drawOpenEye(canvas, leftX, cy, eyeR, sclera, fill, 0f, 0f)
                drawOpenEye(canvas, rightX, cy, eyeR, sclera, fill, 0f, 0f)
            }

            Emotion.HAPPY -> {
                drawHappyArc(canvas, leftX, cy, eyeR, stroke)
                drawHappyArc(canvas, rightX, cy, eyeR, stroke)
            }

            Emotion.THINKING -> {
                drawOpenEye(canvas, leftX, cy + eyeR * 0.15f, eyeR, sclera, fill, 0f, 0f)
                drawOpenEye(canvas, rightX, cy - eyeR * 0.30f, eyeR * 0.9f, sclera, fill, eyeR * 0.28f, -eyeR * 0.28f)
            }

            Emotion.ERROR -> {
                drawCrossEye(canvas, leftX, cy, eyeR, stroke)
                drawCrossEye(canvas, rightX, cy, eyeR, stroke)
            }

            Emotion.SLEEPY -> {
                drawSleepyArc(canvas, leftX, cy, eyeR, stroke)
                drawSleepyArc(canvas, rightX, cy, eyeR, stroke)
            }

            Emotion.LISTENING -> {
                drawOpenEye(canvas, leftX, cy, eyeR * 1.15f, sclera, fill, 0f, 0f)
                drawOpenEye(canvas, rightX, cy, eyeR * 1.15f, sclera, fill, 0f, 0f)
                drawBrow(canvas, leftX, cy - eyeR * 1.35f, eyeR, stroke)
                drawBrow(canvas, rightX, cy - eyeR * 1.35f, eyeR, stroke)
            }
        }

        return bitmap
    }

    private fun drawOpenEye(
        canvas: Canvas,
        x: Float,
        y: Float,
        r: Float,
        sclera: Paint,
        pupil: Paint,
        pupilDx: Float,
        pupilDy: Float,
    ) {
        canvas.drawCircle(x, y, r, sclera)
        canvas.drawCircle(x + pupilDx, y + pupilDy, r * 0.5f, pupil)
    }

    private fun drawHappyArc(canvas: Canvas, x: Float, y: Float, r: Float, stroke: Paint) {
        val path = Path().apply {
            moveTo(x - r, y + r * 0.4f)
            quadTo(x, y - r * 0.7f, x + r, y + r * 0.4f)
        }
        canvas.drawPath(path, stroke)
    }

    private fun drawSleepyArc(canvas: Canvas, x: Float, y: Float, r: Float, stroke: Paint) {
        val path = Path().apply {
            moveTo(x - r, y - r * 0.15f)
            quadTo(x, y + r * 0.55f, x + r, y - r * 0.15f)
        }
        canvas.drawPath(path, stroke)
    }

    private fun drawCrossEye(canvas: Canvas, x: Float, y: Float, r: Float, stroke: Paint) {
        canvas.drawLine(x - r, y - r, x + r, y + r, stroke)
        canvas.drawLine(x - r, y + r, x + r, y - r, stroke)
    }

    private fun drawBrow(canvas: Canvas, x: Float, y: Float, r: Float, stroke: Paint) {
        canvas.drawLine(x - r * 0.8f, y, x + r * 0.8f, y, stroke)
    }
}
