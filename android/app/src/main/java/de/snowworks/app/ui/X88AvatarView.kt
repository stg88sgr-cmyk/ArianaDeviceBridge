package de.snowworks.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * ARIANA X-88 procedural renderer v7.
 *
 * Design language:
 * - dark biomechanical shell
 * - white segmented shoulders
 * - cyan eyes / sensor lines
 * - magenta reactive speech accents
 * - gold face/core geometry
 *
 * This remains a light Android View with no added rendering dependency.
 */
class X88AvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class Mode {
        IDLE,
        LISTENING,
        THINKING,
        SPEAKING,
        ATTENTION,
        STOPPED,
    }

    var mode: Mode = Mode.IDLE
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val startMs = SystemClock.uptimeMillis()

    private val cyan = Color.rgb(37, 217, 255)
    private val magenta = Color.rgb(255, 63, 209)
    private val gold = Color.rgb(255, 213, 106)
    private val white = Color.rgb(234, 247, 255)
    private val silver = Color.rgb(168, 184, 198)
    private val steel = Color.rgb(26, 38, 52)
    private val deepSteel = Color.rgb(10, 18, 28)
    private val dark = Color.rgb(5, 7, 13)
    private val red = Color.rgb(255, 92, 122)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = (320f * resources.displayMetrics.density).toInt()
        setMeasuredDimension(
            resolveSize(desired, widthMeasureSpec),
            resolveSize(desired, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val now = SystemClock.uptimeMillis()
        val t = (now - startMs) / 1000f
        val cx = w / 2f
        val cy = h * 0.46f

        val modeColor = when (mode) {
            Mode.LISTENING -> cyan
            Mode.THINKING -> gold
            Mode.SPEAKING -> magenta
            Mode.ATTENTION, Mode.STOPPED -> red
            Mode.IDLE -> gold
        }

        val slow = sin(t * 2f * PI.toFloat() / 2.6f)
        val pulse = 1f + 0.022f * slow
        val outerRadius = w.coerceAtMost(h) * 0.435f * pulse

        paint.style = Paint.Style.FILL
        paint.color = withAlpha(modeColor, 24)
        canvas.drawCircle(cx, cy, outerRadius * 1.10f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = dp(1.0f)
        for (i in 0 until 16) {
            val a = (i / 16f) * PI.toFloat() * 2f + t * 0.09f
            val r1 = outerRadius * 0.96f
            val r2 = outerRadius * if (i % 4 == 0) 1.08f else 1.035f
            paint.color = if (i % 2 == 0) withAlpha(cyan, 85) else withAlpha(gold, 65)
            canvas.drawLine(
                cx + cos(a) * r1,
                cy + sin(a) * r1,
                cx + cos(a) * r2,
                cy + sin(a) * r2,
                paint,
            )
        }

        paint.strokeWidth = dp(1.8f)
        paint.color = withAlpha(modeColor, 220)
        canvas.drawCircle(cx, cy, outerRadius, paint)

        paint.strokeWidth = dp(0.8f)
        paint.color = withAlpha(cyan, 70)
        canvas.drawCircle(cx, cy, outerRadius * 0.83f, paint)

        paint.strokeWidth = dp(1.8f)
        paint.color = withAlpha(gold, 220)
        canvas.drawLine(cx - w * 0.19f, h * 0.20f, cx, h * 0.045f, paint)
        canvas.drawLine(cx + w * 0.19f, h * 0.20f, cx, h * 0.045f, paint)
        canvas.drawLine(cx - w * 0.09f, h * 0.15f, cx, h * 0.08f, paint)
        canvas.drawLine(cx + w * 0.09f, h * 0.15f, cx, h * 0.08f, paint)

        path.reset()
        path.moveTo(cx, h * 0.15f)
        path.cubicTo(
            cx - w * 0.30f, h * 0.15f,
            cx - w * 0.31f, h * 0.46f,
            cx - w * 0.22f, h * 0.74f,
        )
        path.lineTo(cx - w * 0.13f, h * 0.62f)
        path.lineTo(cx + w * 0.13f, h * 0.62f)
        path.lineTo(cx + w * 0.22f, h * 0.74f)
        path.cubicTo(
            cx + w * 0.31f, h * 0.46f,
            cx + w * 0.30f, h * 0.15f,
            cx, h * 0.15f,
        )
        path.close()
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(4, 8, 14)
        canvas.drawPath(path, paint)

        val faceTop = h * 0.19f
        val faceBottom = h * 0.665f
        path.reset()
        path.moveTo(cx, faceTop)
        path.cubicTo(
            cx - w * 0.205f, faceTop,
            cx - w * 0.245f, h * 0.39f,
            cx - w * 0.18f, h * 0.56f,
        )
        path.cubicTo(
            cx - w * 0.11f, h * 0.69f,
            cx - w * 0.05f, faceBottom,
            cx, faceBottom,
        )
        path.cubicTo(
            cx + w * 0.05f, faceBottom,
            cx + w * 0.11f, h * 0.69f,
            cx + w * 0.18f, h * 0.56f,
        )
        path.cubicTo(
            cx + w * 0.245f, h * 0.39f,
            cx + w * 0.205f, faceTop,
            cx, faceTop,
        )
        path.close()

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(19, 29, 40)
        canvas.drawPath(path, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.35f)
        paint.color = withAlpha(gold, 170)
        canvas.drawPath(path, paint)

        val crystalY = h * 0.265f
        val crystalR = w * 0.028f * (1f + 0.08f * slow)
        path.reset()
        path.moveTo(cx, crystalY - crystalR)
        path.lineTo(cx + crystalR * 0.75f, crystalY)
        path.lineTo(cx, crystalY + crystalR)
        path.lineTo(cx - crystalR * 0.75f, crystalY)
        path.close()
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(cyan, 165)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.STROKE
        paint.color = gold
        paint.strokeWidth = dp(1.0f)
        canvas.drawPath(path, paint)

        paint.strokeWidth = dp(1.35f)
        paint.color = withAlpha(cyan, 175)
        canvas.drawLine(cx - w * 0.18f, h * 0.35f, cx - w * 0.095f, h * 0.51f, paint)
        canvas.drawLine(cx - w * 0.205f, h * 0.44f, cx - w * 0.13f, h * 0.57f, paint)

        paint.color = withAlpha(magenta, 150)
        canvas.drawLine(cx + w * 0.18f, h * 0.35f, cx + w * 0.095f, h * 0.51f, paint)
        canvas.drawLine(cx + w * 0.205f, h * 0.44f, cx + w * 0.13f, h * 0.57f, paint)

        paint.strokeWidth = dp(0.9f)
        paint.color = withAlpha(gold, 105)
        canvas.drawLine(cx - w * 0.14f, h * 0.49f, cx - w * 0.07f, h * 0.56f, paint)
        canvas.drawLine(cx + w * 0.14f, h * 0.49f, cx + w * 0.07f, h * 0.56f, paint)

        val blinkPhase = ((t % 4.4f) / 4.4f)
        val blink = if (blinkPhase in 0.84f..0.90f) {
            val x = (blinkPhase - 0.84f) / 0.06f
            abs(2f * x - 1f).coerceIn(0.08f, 1f)
        } else {
            1f
        }

        drawEye(canvas, cx - w * 0.085f, h * 0.405f, blink)
        drawEye(canvas, cx + w * 0.085f, h * 0.405f, blink)

        if (mode == Mode.LISTENING) {
            val scan = (sin(t * 3.2f) + 1f) / 2f
            val y = h * (0.33f + scan * 0.23f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(0.8f)
            paint.color = withAlpha(cyan, 120)
            canvas.drawLine(cx - w * 0.18f, y, cx + w * 0.18f, y, paint)
        }

        paint.strokeWidth = dp(0.9f)
        paint.color = withAlpha(gold, 85)
        canvas.drawLine(cx, h * 0.43f, cx, h * 0.535f, paint)

        val mouthAmp = if (mode == Mode.SPEAKING) {
            0.006f + 0.011f * ((sin(t * 18f) + 1f) / 2f)
        } else {
            0.003f
        }
        val mouth = RectF(
            cx - w * 0.055f,
            h * 0.575f - h * mouthAmp,
            cx + w * 0.055f,
            h * 0.575f + h * mouthAmp,
        )
        paint.style = Paint.Style.FILL
        paint.color = if (mode == Mode.SPEAKING) magenta else withAlpha(gold, 180)
        canvas.drawRoundRect(mouth, dp(3f), dp(3f), paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = w * 0.052f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = deepSteel
        canvas.drawLine(cx - w * 0.065f, h * 0.66f, cx - w * 0.11f, h * 0.80f, paint)
        canvas.drawLine(cx + w * 0.065f, h * 0.66f, cx + w * 0.11f, h * 0.80f, paint)

        paint.strokeWidth = dp(1.5f)
        paint.color = withAlpha(cyan, 145)
        canvas.drawLine(cx - w * 0.035f, h * 0.67f, cx - w * 0.055f, h * 0.785f, paint)
        paint.color = withAlpha(magenta, 130)
        canvas.drawLine(cx + w * 0.035f, h * 0.67f, cx + w * 0.055f, h * 0.785f, paint)

        paint.strokeWidth = w * 0.080f
        paint.color = withAlpha(white, 225)
        canvas.drawLine(cx - w * 0.11f, h * 0.79f, cx - w * 0.34f, h * 0.88f, paint)
        canvas.drawLine(cx + w * 0.11f, h * 0.79f, cx + w * 0.34f, h * 0.88f, paint)

        paint.strokeWidth = dp(2.0f)
        paint.color = withAlpha(steel, 230)
        for (i in 0..2) {
            val d = i * w * 0.06f
            canvas.drawLine(
                cx - w * 0.17f - d,
                h * 0.815f + d * 0.22f,
                cx - w * 0.15f - d,
                h * 0.875f + d * 0.22f,
                paint,
            )
            canvas.drawLine(
                cx + w * 0.17f + d,
                h * 0.815f + d * 0.22f,
                cx + w * 0.15f + d,
                h * 0.875f + d * 0.22f,
                paint,
            )
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.1f)
        paint.color = withAlpha(modeColor, 185)
        val g = w * 0.075f
        path.reset()
        path.moveTo(cx, h * 0.73f)
        path.lineTo(cx + g * 0.58f, h * 0.78f)
        path.lineTo(cx, h * 0.83f)
        path.lineTo(cx - g * 0.58f, h * 0.78f)
        path.close()
        canvas.drawPath(path, paint)

        paint.style = Paint.Style.FILL
        paint.color = withAlpha(modeColor, 34)
        canvas.drawCircle(cx, h * 0.83f, w * 0.070f * pulse, paint)
        paint.color = withAlpha(cyan, 36)
        canvas.drawCircle(cx, h * 0.83f, w * 0.050f * pulse, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.6f)
        paint.color = if (mode == Mode.STOPPED) red else gold
        canvas.drawCircle(cx, h * 0.83f, w * 0.029f * pulse, paint)

        paint.strokeWidth = dp(0.8f)
        paint.color = withAlpha(magenta, 130)
        canvas.drawCircle(cx, h * 0.83f, w * 0.020f * pulse, paint)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = dp(10f)
        paint.color = withAlpha(if (mode == Mode.STOPPED) red else silver, 220)
        canvas.drawText(modeLabel(mode), cx, h * 0.965f, paint)

        if (isAttachedToWindow) postInvalidateOnAnimation()
    }

    private fun drawEye(canvas: Canvas, centerX: Float, centerY: Float, blink: Float) {
        val eyeW = width * 0.055f
        val eyeH = height * 0.013f * blink

        paint.style = Paint.Style.FILL
        paint.color = withAlpha(dark, 230)
        canvas.drawOval(
            RectF(
                centerX - eyeW,
                centerY - height * 0.022f,
                centerX + eyeW,
                centerY + height * 0.022f,
            ),
            paint,
        )

        paint.color = cyan
        canvas.drawOval(
            RectF(
                centerX - eyeW * 0.72f,
                centerY - eyeH,
                centerX + eyeW * 0.72f,
                centerY + eyeH,
            ),
            paint,
        )

        paint.color = withAlpha(white, 220)
        canvas.drawCircle(centerX, centerY, width * 0.008f * blink, paint)
    }

    private fun modeLabel(mode: Mode): String = when (mode) {
        Mode.IDLE -> "X-88 · READY"
        Mode.LISTENING -> "X-88 · LISTENING"
        Mode.THINKING -> "X-88 · THINKING"
        Mode.SPEAKING -> "X-88 · SPEAKING"
        Mode.ATTENTION -> "X-88 · ATTENTION"
        Mode.STOPPED -> "X-88 · STOP ALL"
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
