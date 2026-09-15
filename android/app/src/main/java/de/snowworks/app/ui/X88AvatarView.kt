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
 * ARIANA X-88 procedural renderer v9.
 *
 * Design language:
 * - dark biomechanical shell
 * - white segmented shoulders
 * - cyan eyes / sensor lines
 * - magenta reactive speech accents
 * - gold face/core geometry
 * - subtle presence motion: gaze, head drift, breathing and mode energy
 * - expression layer: brows, eye squint, state arcs and procedural viseme mouth shapes
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
        val breath = sin(t * 2f * PI.toFloat() / 4.8f)
        val pulse = 1f + 0.022f * slow
        val energyHz = when (mode) {
            Mode.IDLE -> 0.42f
            Mode.LISTENING -> 1.15f
            Mode.THINKING -> 0.78f
            Mode.SPEAKING -> 2.1f
            Mode.ATTENTION -> 1.45f
            Mode.STOPPED -> 0.18f
        }
        val energyAmp = when (mode) {
            Mode.SPEAKING -> 0.10f
            Mode.LISTENING -> 0.075f
            Mode.THINKING -> 0.065f
            Mode.ATTENTION -> 0.08f
            Mode.IDLE -> 0.045f
            Mode.STOPPED -> 0.02f
        }
        val energy = (sin(t * 2f * PI.toFloat() * energyHz) + 1f) / 2f
        val corePulse = 1f + energyAmp * energy
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

        if (mode != Mode.IDLE && mode != Mode.STOPPED) {
            val ring = RectF(
                cx - outerRadius * 1.04f,
                cy - outerRadius * 1.04f,
                cx + outerRadius * 1.04f,
                cy + outerRadius * 1.04f,
            )
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(if (mode == Mode.SPEAKING) 2.2f else 1.5f)
            for (i in 0 until 3) {
                val start = -90f + i * 120f + t * when (mode) {
                    Mode.LISTENING -> 24f
                    Mode.THINKING -> 14f
                    Mode.SPEAKING -> 32f
                    Mode.ATTENTION -> 8f
                    else -> 12f
                }
                val sweep = when (mode) {
                    Mode.LISTENING -> 42f
                    Mode.THINKING -> 28f
                    Mode.SPEAKING -> 54f
                    Mode.ATTENTION -> 34f
                    else -> 30f
                }
                paint.color = withAlpha(modeColor, 125 + i * 28)
                canvas.drawArc(ring, start, sweep, false, paint)
            }
        }

        val tiltBase = when (mode) {
            Mode.LISTENING -> 1.0f
            Mode.THINKING -> 0.45f
            Mode.SPEAKING -> 0.75f
            Mode.ATTENTION -> 0.25f
            Mode.STOPPED -> 0f
            Mode.IDLE -> 0.65f
        }
        val headTilt = tiltBase * sin(t * 0.55f)
        val headBob = if (mode == Mode.STOPPED) 0f else h * 0.0032f * sin(t * 0.82f)
        val gazeScale = when (mode) {
            Mode.LISTENING -> 0.30f
            Mode.THINKING -> 1.0f
            Mode.SPEAKING -> 0.55f
            Mode.ATTENTION -> 0.15f
            Mode.STOPPED -> 0f
            Mode.IDLE -> 0.70f
        }
        val gazeX = w * 0.010f * gazeScale * sin(t * 0.67f)
        val gazeY = h * 0.0045f * gazeScale * sin(t * 0.43f + 1.1f)
        val browLift = when (mode) {
            Mode.LISTENING -> h * 0.009f
            Mode.THINKING -> h * 0.004f
            Mode.SPEAKING -> h * 0.002f
            Mode.ATTENTION -> -h * 0.002f
            Mode.STOPPED -> -h * 0.004f
            Mode.IDLE -> h * 0.003f
        }
        val browPinch = when (mode) {
            Mode.ATTENTION -> h * 0.010f
            Mode.THINKING -> h * 0.004f
            Mode.LISTENING -> -h * 0.003f
            else -> 0f
        }
        val eyeSquint = when (mode) {
            Mode.LISTENING -> 1.08f
            Mode.THINKING -> 0.90f
            Mode.SPEAKING -> 0.96f
            Mode.ATTENTION -> 0.72f
            Mode.STOPPED -> 0.55f
            Mode.IDLE -> 1f
        }

        canvas.save()
        canvas.translate(0f, headBob)
        canvas.rotate(headTilt, cx, h * 0.46f)

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

        drawEye(canvas, cx - w * 0.085f, h * 0.405f, blink, gazeX, gazeY, eyeSquint)
        drawEye(canvas, cx + w * 0.085f, h * 0.405f, blink, gazeX, gazeY, eyeSquint)
        drawBrows(canvas, cx, h, w, browLift, browPinch)

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

        drawMouth(canvas, cx, h, w, t)

        canvas.restore()

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

        val shoulderLift = h * 0.004f * breath
        paint.strokeWidth = w * 0.080f
        paint.color = withAlpha(white, 225)
        canvas.drawLine(cx - w * 0.11f, h * 0.79f + shoulderLift, cx - w * 0.34f, h * 0.88f + shoulderLift, paint)
        canvas.drawLine(cx + w * 0.11f, h * 0.79f + shoulderLift, cx + w * 0.34f, h * 0.88f + shoulderLift, paint)

        paint.strokeWidth = dp(2.0f)
        paint.color = withAlpha(steel, 230)
        for (i in 0..2) {
            val d = i * w * 0.06f
            canvas.drawLine(
                cx - w * 0.17f - d,
                h * 0.815f + d * 0.22f + shoulderLift,
                cx - w * 0.15f - d,
                h * 0.875f + d * 0.22f + shoulderLift,
                paint,
            )
            canvas.drawLine(
                cx + w * 0.17f + d,
                h * 0.815f + d * 0.22f + shoulderLift,
                cx + w * 0.15f + d,
                h * 0.875f + d * 0.22f + shoulderLift,
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
        canvas.drawCircle(cx, h * 0.83f, w * 0.070f * corePulse, paint)
        paint.color = withAlpha(cyan, 36)
        canvas.drawCircle(cx, h * 0.83f, w * 0.050f * corePulse, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.6f)
        paint.color = if (mode == Mode.STOPPED) red else gold
        canvas.drawCircle(cx, h * 0.83f, w * 0.029f * corePulse, paint)

        paint.strokeWidth = dp(0.8f)
        paint.color = withAlpha(magenta, 130)
        canvas.drawCircle(cx, h * 0.83f, w * 0.020f * corePulse, paint)

        if (mode == Mode.THINKING) {
            paint.style = Paint.Style.FILL
            for (i in 0 until 3) {
                val a = t * 1.45f + i * (2f * PI.toFloat() / 3f)
                paint.color = when (i) {
                    0 -> withAlpha(cyan, 210)
                    1 -> withAlpha(gold, 210)
                    else -> withAlpha(magenta, 190)
                }
                canvas.drawCircle(
                    cx + cos(a) * w * 0.052f,
                    h * 0.83f + sin(a) * w * 0.052f,
                    w * 0.0065f,
                    paint,
                )
            }
        }

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = dp(10f)
        paint.color = withAlpha(if (mode == Mode.STOPPED) red else silver, 220)
        canvas.drawText(modeLabel(mode), cx, h * 0.965f, paint)

        if (isAttachedToWindow) postInvalidateOnAnimation()
    }

    private fun drawEye(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        blink: Float,
        gazeX: Float,
        gazeY: Float,
        squint: Float,
    ) {
        val eyeW = width * 0.055f
        val eyeH = height * 0.013f * blink * squint

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
        canvas.drawCircle(
            centerX + gazeX,
            centerY + gazeY,
            width * 0.008f * blink,
            paint,
        )
    }

    private fun drawBrows(
        canvas: Canvas,
        cx: Float,
        h: Float,
        w: Float,
        lift: Float,
        pinch: Float,
    ) {
        val y = h * 0.355f - lift
        val left = cx - w * 0.085f
        val right = cx + w * 0.085f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(if (mode == Mode.ATTENTION) 2.0f else 1.35f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = when (mode) {
            Mode.ATTENTION, Mode.STOPPED -> withAlpha(red, 185)
            Mode.LISTENING -> withAlpha(cyan, 175)
            Mode.SPEAKING -> withAlpha(magenta, 160)
            else -> withAlpha(gold, 150)
        }
        canvas.drawLine(left - w * 0.052f, y + pinch, left + w * 0.040f, y - pinch, paint)
        canvas.drawLine(right - w * 0.040f, y - pinch, right + w * 0.052f, y + pinch, paint)
    }

    private fun drawMouth(canvas: Canvas, cx: Float, h: Float, w: Float, t: Float) {
        if (mode != Mode.SPEAKING) {
            paint.style = Paint.Style.FILL
            paint.color = withAlpha(gold, 180)
            val mouth = RectF(
                cx - w * 0.050f,
                h * 0.572f,
                cx + w * 0.050f,
                h * 0.578f,
            )
            canvas.drawRoundRect(mouth, dp(3f), dp(3f), paint)
            return
        }

        val viseme = (t * 7.5f).toInt() % 5
        val halfW = when (viseme) {
            0 -> w * 0.040f
            1 -> w * 0.061f
            2 -> w * 0.047f
            3 -> w * 0.034f
            else -> w * 0.054f
        }
        val halfH = when (viseme) {
            0 -> h * 0.007f
            1 -> h * 0.011f
            2 -> h * 0.021f
            3 -> h * 0.018f
            else -> h * 0.014f
        }
        val mouth = RectF(
            cx - halfW,
            h * 0.575f - halfH,
            cx + halfW,
            h * 0.575f + halfH,
        )

        paint.style = Paint.Style.FILL
        paint.color = withAlpha(dark, 245)
        canvas.drawRoundRect(mouth, dp(5f), dp(5f), paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.25f)
        paint.color = withAlpha(magenta, 235)
        canvas.drawRoundRect(mouth, dp(5f), dp(5f), paint)

        paint.strokeWidth = dp(0.8f)
        paint.color = withAlpha(white, 120)
        canvas.drawLine(
            cx - halfW * 0.65f,
            h * 0.575f,
            cx + halfW * 0.65f,
            h * 0.575f,
            paint,
        )
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
