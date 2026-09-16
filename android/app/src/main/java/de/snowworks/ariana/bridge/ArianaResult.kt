package de.snowworks.ariana.bridge

/**
 * Canonical result envelope for guarded X-88 runtime actions.
 *
 * Keep payloads metadata-only. Prompts, notification contents and other user
 * content must not be copied into the audit/result channel by default.
 */
data class ArianaResult(
    val actionId: String,
    val ok: Boolean,
    val status: Status,
    val code: String,
    val message: String,
    val metadata: Map<String, String> = emptyMap(),
    val timestampMs: Long = System.currentTimeMillis(),
) {
    enum class Status {
        OK,
        ACKNOWLEDGED,
        DENIED,
        BLOCKED,
        INVALID,
        ERROR,
    }

    companion object {
        fun ok(
            actionId: String,
            code: String = "OK",
            message: String = "completed",
            metadata: Map<String, String> = emptyMap(),
        ) = ArianaResult(actionId, true, Status.OK, code, message, metadata)

        fun acknowledged(
            actionId: String,
            code: String = "ACKNOWLEDGED",
            message: String = "acknowledged",
            metadata: Map<String, String> = emptyMap(),
        ) = ArianaResult(actionId, true, Status.ACKNOWLEDGED, code, message, metadata)

        fun blocked(
            actionId: String,
            code: String,
            message: String,
            metadata: Map<String, String> = emptyMap(),
        ) = ArianaResult(actionId, false, Status.BLOCKED, code, message, metadata)

        fun denied(
            actionId: String,
            code: String,
            message: String,
            metadata: Map<String, String> = emptyMap(),
        ) = ArianaResult(actionId, false, Status.DENIED, code, message, metadata)

        fun error(
            actionId: String,
            code: String,
            message: String,
            metadata: Map<String, String> = emptyMap(),
        ) = ArianaResult(actionId, false, Status.ERROR, code, message, metadata)
    }
}
