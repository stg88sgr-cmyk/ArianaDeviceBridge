package de.snowworks.app.widget

object WakewordSignalBus {
    @Volatile var listener: (() -> Unit)? = null

    fun emit(): Boolean {
        val current = listener ?: return false
        current.invoke()
        return true
    }
}
