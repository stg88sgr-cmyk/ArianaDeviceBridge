package de.snowworks.ariana.xx88y

interface XX88YActionPolicy {
    fun levelFor(actionType: String): ActionLevel
}

class DefaultXX88YActionPolicy : XX88YActionPolicy {
    override fun levelFor(actionType: String): ActionLevel = when (actionType) {
        "core.status", "audit.read", "build.status", "runtime.status" -> ActionLevel.SAFE
        "bridge.start", "bridge.stop",
        "runtime.start", "runtime.stop",
        "worker.enable", "worker.disable",
        "studio.generate" -> ActionLevel.CONFIRM
        else -> ActionLevel.BLOCKED
    }
}

class X88ActionBus(
    private val stateHolder: X88StateHolder,
    private val policy: XX88YActionPolicy = DefaultXX88YActionPolicy(),
) {
    private val handlers = mutableListOf<ActionHandler>()

    fun register(handler: ActionHandler) {
        handlers += handler
    }

    suspend fun dispatch(request: ActionRequest, confirmed: Boolean = false): ActionResult {
        val state = stateHolder.state.value

        if (state.emergencyStopActive) {
            return ActionResult(
                actionId = request.id,
                success = false,
                status = ActionStatus.BLOCKED,
                message = "Emergency Stop is active.",
            )
        }

        if (!state.masterEnabled && request.type !in setOf("core.status", "audit.read")) {
            return ActionResult(
                actionId = request.id,
                success = false,
                status = ActionStatus.BLOCKED,
                message = "Master Control is disabled.",
            )
        }

        val result = when (policy.levelFor(request.type)) {
            ActionLevel.BLOCKED -> ActionResult(
                request.id, false, ActionStatus.BLOCKED, "Action is blocked by policy."
            )
            ActionLevel.CONFIRM -> if (!confirmed) {
                ActionResult(
                    request.id, false, ActionStatus.PENDING, "Confirmation required."
                )
            } else {
                executeRealHandler(request)
            }
            ActionLevel.SAFE -> executeRealHandler(request)
        }

        stateHolder.audit(
            source = request.source,
            category = "ActionBus",
            severity = if (result.success) Severity.INFO else Severity.WARNING,
            message = "${request.type}: ${result.status} - ${result.message}",
            metadata = mapOf("actionId" to request.id, "target" to request.target),
        )
        return result
    }

    private suspend fun executeRealHandler(request: ActionRequest): ActionResult {
        val handler = handlers.firstOrNull { it.canHandle(request) }
            ?: return ActionResult(
                actionId = request.id,
                success = false,
                status = ActionStatus.NOT_IMPLEMENTED,
                message = "No real handler is connected for '${request.type}'.",
            )

        return handler.execute(request)
    }
}
