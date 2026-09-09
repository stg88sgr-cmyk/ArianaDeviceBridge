package de.snowworks.ariana.avatar

import java.io.Closeable

/**
 * Stable renderer boundary for Ariana's visual presence.
 *
 * A VTube Studio adapter can implement this first; a native Live2D renderer
 * can replace it later without changing the rest of the presence layer.
 */
interface AvatarDriver : Closeable {
    fun apply(state: AvatarState)
    fun show()
    fun hide()

    override fun close() = Unit

    object NOOP : AvatarDriver {
        override fun apply(state: AvatarState) = Unit
        override fun show() = Unit
        override fun hide() = Unit
    }
}
