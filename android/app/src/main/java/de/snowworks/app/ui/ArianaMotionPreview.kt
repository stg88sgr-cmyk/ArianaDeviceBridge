package de.snowworks.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import de.snowworks.ariana.avatar.AvatarDriver
import de.snowworks.ariana.avatar.AvatarState
import kotlin.math.abs

/**
 * Tiny renderer-independent motion probe used before the real Live2D asset is
 * ready. It deliberately does not try to look like the final Ariana artwork;
 * it only makes head, eye, blink, breath, mouth and core motion visible on-device.
 */
class ArianaMotionPreviewView(context: Context) : View(context) {

    @Volatile
    private var state: AvatarState = AvatarState()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2f
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B9A8D6")
        textSize = resources.displayMetrics.scaledDensity * 12f
    }

    fun render(next: AvatarState) {
        state = next.normalized()
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = state
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        canvas.drawColor(Color.parseColor("#0F0C18"))

        val cx = w * 0.5f + s.headYaw * w * 0.025f
        val cy = h * 0.40f + s.headPitch * h * 0.02f
        val radius = minOf(w * 0.19f, h * 0.26f)
        val breathOffset = (s.breath - 0.5f) * h * 0.045f
        val breathScale = 0.98f + s.breath * 0.04f

        // Torso probe. Breath changes it subtly instead of scaling the whole view.
        val torsoTop = cy + radius * 0.72f + breathOffset
        val torsoHalfWidth = radius * 1.05f * breathScale
        fill.color = Color.parseColor("#171D2B")
        canvas.drawRoundRect(
            RectF(
                cx - torsoHalfWidth,
                torsoTop,
                cx + torsoHalfWidth,
                h * 0.96f,
            ),
            radius * 0.22f,
            radius * 0.22f,
            fill,
        )
        stroke.color = Color.parseColor("#D5B85A")
        canvas.drawRoundRect(
            RectF(
                cx - torsoHalfWidth,
                torsoTop,
                cx + torsoHalfWidth,
                h * 0.96f,
            ),
            radius * 0.22f,
            radius * 0.22f,
            stroke,
        )

        // Rotate only the head group so ParamAngleZ is visible independently.
        canvas.save()
        canvas.rotate(s.headRoll * 12f, cx, cy)

        // Hair halo / silhouette.
        fill.color = Color.parseColor("#241B34")
        canvas.drawCircle(cx, cy, radius * 1.18f, fill)

        // Face.
        fill.color = Color.parseColor("#EEDDD6")
        canvas.drawOval(
            RectF(cx - radius * 0.78f, cy - radius, cx + radius * 0.78f, cy + radius),
            fill,
        )

        // Eyes. Eye-ball offsets make look direction visible.
        val eyeY = cy - radius * 0.18f
        val eyeDx = radius * 0.32f
        drawEye(canvas, cx - eyeDx, eyeY, s.leftEyeOpen, s.eyeX, s.eyeY, radius)
        drawEye(canvas, cx + eyeDx, eyeY, s.rightEyeOpen, s.eyeX, s.eyeY, radius)

        // Mouth: width follows mouthForm, height follows mouthOpen.
        val mouthWidth = radius * (0.42f + (s.mouthForm + 1f) * 0.10f)
        val mouthHeight = radius * (0.03f + s.mouthOpen * 0.35f)
        val mouthY = cy + radius * 0.43f
        fill.color = Color.parseColor("#4C163D")
        canvas.drawOval(
            RectF(
                cx - mouthWidth,
                mouthY - mouthHeight,
                cx + mouthWidth,
                mouthY + mouthHeight,
            ),
            fill,
        )

        canvas.restore()

        // Ariana core glow probe follows breathing, but not head rotation.
        val glowCx = cx
        val glowCy = h * 0.82f + breathOffset
        val glowRadius = radius * (0.13f + s.coreGlow * 0.12f)
        fill.color = Color.argb(
            (90 + s.coreGlow * 165).toInt().coerceIn(0, 255),
            180,
            74,
            255,
        )
        canvas.drawCircle(glowCx, glowCy, glowRadius, fill)
        stroke.color = Color.parseColor("#E8C66A")
        canvas.drawCircle(glowCx, glowCy, glowRadius * 1.25f, stroke)

        label.color = Color.parseColor("#B9A8D6")
        canvas.drawText(
            if (s.speaking) "MOTION PROBE · SPEAKING" else "MOTION PROBE · IDLE",
            resources.displayMetrics.density * 12f,
            h - resources.displayMetrics.density * 10f,
            label,
        )
    }

    private fun drawEye(
        canvas: Canvas,
        x: Float,
        y: Float,
        openness: Float,
        eyeX: Float,
        eyeY: Float,
        radius: Float,
    ) {
        val open = openness.coerceIn(0f, 1f)
        val eyeWidth = radius * 0.22f
        val eyeHeight = radius * (0.018f + open * 0.12f)

        fill.color = Color.WHITE
        canvas.drawOval(RectF(x - eyeWidth, y - eyeHeight, x + eyeWidth, y + eyeHeight), fill)

        if (open > 0.08f) {
            val pupilRadius = radius * 0.055f * (0.75f + open * 0.25f)
            val px = x + eyeX.coerceIn(-1f, 1f) * eyeWidth * 0.45f
            val py = y + eyeY.coerceIn(-1f, 1f) * maxOf(eyeHeight, radius * 0.04f) * 0.45f
            fill.color = Color.parseColor("#3C89D6")
            canvas.drawCircle(px, py, pupilRadius, fill)
            fill.color = Color.parseColor("#0B1621")
            canvas.drawCircle(px, py, pupilRadius * 0.48f, fill)
        }

        if (abs(open) < 0.05f) {
            stroke.color = Color.parseColor("#3A2630")
            canvas.drawLine(x - eyeWidth, y, x + eyeWidth, y, stroke)
        }
    }
}

/** Bridges renderer-neutral avatar state into [ArianaMotionPreviewView]. */
class PreviewAvatarDriver : AvatarDriver {
    @Volatile
    private var view: ArianaMotionPreviewView? = null

    @Volatile
    private var latest = AvatarState()

    @Volatile
    private var visible = true

    fun attach(target: ArianaMotionPreviewView) {
        view = target
        target.visibility = if (visible) View.VISIBLE else View.GONE
        target.render(latest)
    }

    override fun apply(state: AvatarState) {
        latest = state.normalized()
        view?.let { target -> target.post { target.render(latest) } }
    }

    override fun show() {
        visible = true
        view?.post { view?.visibility = View.VISIBLE }
    }

    override fun hide() {
        visible = false
        view?.post { view?.visibility = View.GONE }
    }

    override fun close() {
        view = null
    }
}
