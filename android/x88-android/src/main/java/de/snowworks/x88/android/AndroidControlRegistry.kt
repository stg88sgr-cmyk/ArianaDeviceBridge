package de.snowworks.x88.android

/**
 * Android-side policy gate. Unknown actions are denied by default.
 * No network, Intent dispatch, or background scheduling belongs here.
 */
class AndroidControlRegistry(
    allowedActions: Set<AndroidAction> = emptySet(),
) {
    private val allowed = allowedActions.toSet()

    fun isAllowed(action: AndroidAction): Boolean = action in allowed

    fun requireAllowed(request: AndroidActionRequest): Boolean =
        isAllowed(request.action)
}
