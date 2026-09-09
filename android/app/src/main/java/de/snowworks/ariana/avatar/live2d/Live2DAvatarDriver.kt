package de.snowworks.ariana.avatar.live2d

import de.snowworks.ariana.avatar.AvatarDriver
import de.snowworks.ariana.avatar.AvatarState
import kotlin.math.abs

/**
 * Production-facing Ariana avatar driver that converts renderer-neutral state
 * into the fixed Live2D parameter contract and forwards it to a concrete sink.
 *
 * It also suppresses no-op parameter writes so a native Cubism renderer does
 * not receive needless updates every idle tick.
 */
class Live2DAvatarDriver(
    private val sink: Live2DFrameSink,
    private val epsilon: Float = DEFAULT_EPSILON,
) : AvatarDriver {

    private val lastParameters = linkedMapOf<String, Float>()
    private var lastExpression: String? = null
    private var visible = false
    private var closed = false

    override fun apply(state: AvatarState) {
        if (closed) return

        val frame = Live2DParameterMapper.map(state)
        var changed = false

        if (frame.expressionId != lastExpression) {
            sink.setExpression(frame.expressionId)
            lastExpression = frame.expressionId
            changed = true
        }

        frame.parameters.forEach { (id, value) ->
            val previous = lastParameters[id]
            if (previous == null || abs(previous - value) > epsilon) {
                sink.setParameter(id, value)
                lastParameters[id] = value
                changed = true
            }
        }

        if (changed) sink.commitFrame()
    }

    override fun show() {
        if (closed || visible) return
        visible = true
        sink.setVisible(true)
    }

    override fun hide() {
        if (closed || !visible) return
        visible = false
        sink.setVisible(false)
    }

    override fun close() {
        if (closed) return
        closed = true
        if (visible) sink.setVisible(false)
        visible = false
        lastParameters.clear()
        lastExpression = null
        sink.close()
    }

    companion object {
        const val DEFAULT_EPSILON = 0.001f
    }
}
