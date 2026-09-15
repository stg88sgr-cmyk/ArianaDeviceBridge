package de.snowworks.app.widget

object WakewordSignalBus {
    @Volatile var listener: (() -> Unit)? = null
    @Volatile var statusListener: ((String) -> Unit)? = null

    fun emit(): Boolean {
        val current = listener ?: return false
        current.invoke()
        return true
    }

    fun publishStatus(status: String) {
        statusListener?.invoke(status)
    }
}
