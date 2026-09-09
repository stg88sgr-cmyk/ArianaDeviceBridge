package de.snowworks.ariana.avatar.live2d

import java.io.Closeable

/**
 * Small SDK-neutral output port for a concrete Live2D runtime.
 *
 * The Presence layer only produces parameter frames. A future Cubism SDK
 * implementation can own GL surfaces, model loading and texture lifetime here
 * without leaking those details into ArianaPresenceController.
 */
interface Live2DFrameSink : Closeable {
    fun setVisible(visible: Boolean)
    fun setExpression(expressionId: String)
    fun setParameter(parameterId: String, value: Float)
    fun commitFrame()

    override fun close() = Unit

    object NOOP : Live2DFrameSink {
        override fun setVisible(visible: Boolean) = Unit
        override fun setExpression(expressionId: String) = Unit
        override fun setParameter(parameterId: String, value: Float) = Unit
        override fun commitFrame() = Unit
    }
}
