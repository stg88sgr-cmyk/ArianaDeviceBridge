package de.snowworks.ariana.bridge

import de.snowworks.ariana.Feature
import de.snowworks.ariana.universal.v2.TaskAuthority

/**
 * Canonical X-88 execution gate.
 *
 * Order is intentionally fixed and observable in the returned trace:
 * TokenAuth -> InputValidator -> ActionPolicy -> PermissionResolver ->
 * ConfirmationGate -> ActionDispatcher.
 *
 * The chain itself never requests Android permissions or opens system dialogs.
 * Permission requests and other visible user interactions belong to UI/system
 * flows outside this class.
 */
data class SecurityRequest(
    val actionId: String,
    val payload: Map<String, String> = emptyMap(),
    val token: String? = null,
    val approvalGrantId: String? = null,
)

enum class SecurityStage {
    TOKEN_AUTH,
    INPUT_VALIDATION,
    ACTION_POLICY,
    PERMISSION_RESOLVER,
    CONFIRMATION_GATE,
    ACTION_DISPATCHER,
}

data class DispatchResult(
    val ok: Boolean,
    val code: String? = null,
    val message: String? = null,
)

sealed interface SecurityDecision {
    val trace: List<SecurityStage>

    data class Dispatched(
        val descriptor: ActionDescriptor,
        val result: DispatchResult,
        override val trace: List<SecurityStage>,
    ) : SecurityDecision

    data class PendingConfirmation(
        val descriptor: ActionDescriptor,
        val proposalId: String,
        override val trace: List<SecurityStage>,
    ) : SecurityDecision

    data class Denied(
        val code: String,
        val message: String,
        override val trace: List<SecurityStage>,
    ) : SecurityDecision
}

fun interface TokenAuthenticator {
    fun authenticate(token: String?): Boolean
}

fun interface PolicyEvaluator {
    fun evaluate(actionId: String): ActionPolicy.Evaluation
}

sealed interface PermissionResolution {
    data object Ready : PermissionResolution

    data class Missing(
        val features: Set<Feature>,
        val reason: String = "PERMISSION_REQUIRED",
    ) : PermissionResolution
}

fun interface PermissionResolver {
    fun resolve(descriptor: ActionDescriptor): PermissionResolution
}

sealed interface ConfirmationDecision {
    data object Approved : ConfirmationDecision
    data class Pending(val proposalId: String) : ConfirmationDecision
    data class Denied(val code: String, val message: String) : ConfirmationDecision
}

fun interface ConfirmationGate {
    fun evaluate(
        descriptor: ActionDescriptor,
        policy: ActionPolicy.Evaluation,
        approvalGrantId: String?,
    ): ConfirmationDecision
}

fun interface ActionDispatcher {
    fun dispatch(descriptor: ActionDescriptor, payload: Map<String, String>): DispatchResult
}

class ActionInputValidator(
    private val maxPayloadBytes: Int = 8 * 1024,
    private val maxFields: Int = 32,
    private val maxValueChars: Int = 4 * 1024,
) {
    private val actionPattern = Regex("^[a-z][a-z0-9_]{0,79}$")
    private val payloadKeyPattern = Regex("^[A-Za-z][A-Za-z0-9_]{0,79}$")
    private val normalizedAuthorityFields = setOf(
        "confirmationlevel",
        "confirmation_level",
        "requiredcapabilities",
        "required_capabilities",
        "policydecision",
        "policy_decision",
    )

    /** Returns null when valid, otherwise a stable error code. */
    fun validate(request: SecurityRequest): String? {
        val action = request.actionId.trim().lowercase()
        if (!actionPattern.matches(action)) return "INVALID_ACTION_NAME"
        if (request.payload.size > maxFields) return "TOO_MANY_PAYLOAD_FIELDS"

        val exactAuthorityCheck = TaskAuthority.rejectReservedFields(request.payload.keys)
        if (exactAuthorityCheck.isFailure) return "CALLER_AUTHORITY_FORBIDDEN"
        if (request.payload.keys.any { it.lowercase() in normalizedAuthorityFields }) {
            return "CALLER_AUTHORITY_FORBIDDEN"
        }

        var totalBytes = 0
        for ((key, value) in request.payload) {
            if (!payloadKeyPattern.matches(key)) return "INVALID_PAYLOAD_KEY"
            if (value.length > maxValueChars) return "PAYLOAD_VALUE_TOO_LARGE"
            totalBytes += key.toByteArray(Charsets.UTF_8).size
            totalBytes += value.toByteArray(Charsets.UTF_8).size
            if (totalBytes > maxPayloadBytes) return "PAYLOAD_TOO_LARGE"
        }
        return null
    }
}

class SecurityChain(
    private val tokenAuthenticator: TokenAuthenticator,
    private val inputValidator: ActionInputValidator,
    private val policyEvaluator: PolicyEvaluator,
    private val permissionResolver: PermissionResolver,
    private val confirmationGate: ConfirmationGate,
    private val actionDispatcher: ActionDispatcher,
) {
    fun execute(request: SecurityRequest): SecurityDecision {
        val trace = mutableListOf<SecurityStage>()

        trace += SecurityStage.TOKEN_AUTH
        if (!tokenAuthenticator.authenticate(request.token)) {
            return denied("UNAUTHORIZED", "Token authentication failed.", trace)
        }

        trace += SecurityStage.INPUT_VALIDATION
        inputValidator.validate(request)?.let { code ->
            return denied(code, "Input validation failed.", trace)
        }

        trace += SecurityStage.ACTION_POLICY
        val descriptor = ActionDescriptorRegistry.find(request.actionId)
            ?: return denied("ACTION_NOT_ALLOWLISTED", "Action is not allowlisted.", trace)

        ActionDescriptorRegistry.validatePayload(descriptor, request.payload)
            .exceptionOrNull()
            ?.let { error ->
                return denied(
                    error.message ?: "INVALID_PAYLOAD",
                    "Payload does not match the canonical descriptor.",
                    trace,
                )
            }

        val policy = policyEvaluator.evaluate(descriptor.actionId)
        if (policy.decision == ActionPolicy.Decision.BLOCKED) {
            return denied(policy.reason, "Action policy denied execution.", trace)
        }

        trace += SecurityStage.PERMISSION_RESOLVER
        when (val permission = permissionResolver.resolve(descriptor)) {
            PermissionResolution.Ready -> Unit
            is PermissionResolution.Missing -> {
                val ids = permission.features.map { it.id }.sorted().joinToString(",")
                return denied(
                    permission.reason,
                    "Required capability permission is not ready: $ids",
                    trace,
                )
            }
        }

        trace += SecurityStage.CONFIRMATION_GATE
        when (val confirmation = confirmationGate.evaluate(descriptor, policy, request.approvalGrantId)) {
            ConfirmationDecision.Approved -> Unit
            is ConfirmationDecision.Pending -> {
                return SecurityDecision.PendingConfirmation(
                    descriptor = descriptor,
                    proposalId = confirmation.proposalId,
                    trace = trace.toList(),
                )
            }
            is ConfirmationDecision.Denied -> {
                return denied(confirmation.code, confirmation.message, trace)
            }
        }

        trace += SecurityStage.ACTION_DISPATCHER
        val dispatch = actionDispatcher.dispatch(descriptor, request.payload)
        return SecurityDecision.Dispatched(
            descriptor = descriptor,
            result = dispatch,
            trace = trace.toList(),
        )
    }

    private fun denied(
        code: String,
        message: String,
        trace: List<SecurityStage>,
    ): SecurityDecision.Denied = SecurityDecision.Denied(
        code = code,
        message = message,
        trace = trace.toList(),
    )
}
