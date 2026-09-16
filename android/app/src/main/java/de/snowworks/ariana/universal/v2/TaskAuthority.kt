package de.snowworks.ariana.universal.v2

/**
 * X88 task authority boundary.
 *
 * External callers may describe intent only. They cannot supply policy authority
 * such as confirmation level or required capabilities. Those values are derived
 * from the canonical ActionDescriptorRegistry before execution.
 */
data class X88TaskRequest(
    val taskId: String,
    val actionId: String,
    val payload: Map<String, String> = emptyMap(),
    val token: String? = null,
) {
    init {
        require(taskId.isNotBlank()) { "taskId must not be blank" }
        require(actionId.isNotBlank()) { "actionId must not be blank" }
    }
}

/**
 * Fields that must never be accepted as caller-controlled execution authority.
 * This set is useful at JSON/bridge boundaries that receive generic maps.
 */
object TaskAuthority {
    val reservedAuthorityFields: Set<String> = setOf(
        "confirmationLevel",
        "confirmation_level",
        "requiredCapabilities",
        "required_capabilities",
        "policyDecision",
        "policy_decision",
    )

    fun rejectReservedFields(rawKeys: Set<String>): Result<Unit> {
        val forbidden = rawKeys.intersect(reservedAuthorityFields)
        return if (forbidden.isEmpty()) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalArgumentException(
                    "Caller attempted to supply execution authority: ${forbidden.sorted().joinToString()}",
                ),
            )
        }
    }
}
