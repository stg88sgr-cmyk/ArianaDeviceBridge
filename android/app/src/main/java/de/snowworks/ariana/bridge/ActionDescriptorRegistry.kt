package de.snowworks.ariana.bridge

import de.snowworks.ariana.Feature

/**
 * Canonical metadata for an action. Callers provide only actionId + payload;
 * policy level and capabilities are derived here.
 */
enum class ConfirmationRequirement { SAFE, CONFIRM }

enum class PayloadFieldType { STRING, BOOLEAN, INT }

data class PayloadField(
    val name: String,
    val type: PayloadFieldType,
    val required: Boolean = false,
)

data class ActionDescriptor(
    val actionId: String,
    val confirmation: ConfirmationRequirement,
    val requiredFeatures: Set<Feature> = emptySet(),
    val payloadSchema: List<PayloadField> = emptyList(),
)

object ActionDescriptorRegistry {
    private val descriptors: Map<String, ActionDescriptor> = listOf(
        ActionDescriptor("get_device_status", ConfirmationRequirement.SAFE),
        ActionDescriptor("get_permission_status", ConfirmationRequirement.SAFE),
        ActionDescriptor("get_active_sessions", ConfirmationRequirement.SAFE),
        ActionDescriptor("camera_stop", ConfirmationRequirement.SAFE, setOf(Feature.CAMERA)),
        ActionDescriptor("microphone_stop", ConfirmationRequirement.SAFE, setOf(Feature.MICROPHONE)),
        ActionDescriptor("screen_stop", ConfirmationRequirement.SAFE, setOf(Feature.SCREEN)),
        ActionDescriptor("stop_all", ConfirmationRequirement.SAFE),
        ActionDescriptor("presence_clear", ConfirmationRequirement.SAFE),
        ActionDescriptor("apk_status", ConfirmationRequirement.SAFE),
        ActionDescriptor("apk_list", ConfirmationRequirement.SAFE),
        ActionDescriptor("apk_inspect_latest", ConfirmationRequirement.SAFE),

        ActionDescriptor("open_settings", ConfirmationRequirement.CONFIRM),
        ActionDescriptor("camera_start", ConfirmationRequirement.CONFIRM, setOf(Feature.CAMERA)),
        ActionDescriptor("microphone_start", ConfirmationRequirement.CONFIRM, setOf(Feature.MICROPHONE)),
        ActionDescriptor("screen_start", ConfirmationRequirement.CONFIRM, setOf(Feature.SCREEN)),
        ActionDescriptor("camera_snapshot", ConfirmationRequirement.CONFIRM, setOf(Feature.CAMERA)),
        ActionDescriptor("notification_list", ConfirmationRequirement.CONFIRM, setOf(Feature.NOTIFY_READ)),
        ActionDescriptor("notification_clear", ConfirmationRequirement.CONFIRM, setOf(Feature.NOTIFY_READ)),
        ActionDescriptor("presence_thinking", ConfirmationRequirement.CONFIRM),
        ActionDescriptor("presence_done", ConfirmationRequirement.CONFIRM),
        ActionDescriptor("presence_attention", ConfirmationRequirement.CONFIRM),
        ActionDescriptor("presence_quiet", ConfirmationRequirement.CONFIRM),
        ActionDescriptor("presence_test", ConfirmationRequirement.CONFIRM),
        ActionDescriptor("apk_install_latest", ConfirmationRequirement.CONFIRM),
    ).associateBy { it.actionId }

    fun find(rawActionId: String): ActionDescriptor? = descriptors[normalize(rawActionId)]

    fun require(rawActionId: String): Result<ActionDescriptor> =
        find(rawActionId)?.let { Result.success(it) }
            ?: Result.failure(IllegalArgumentException("ACTION_NOT_ALLOWLISTED"))

    fun validatePayload(descriptor: ActionDescriptor, payload: Map<String, String>): Result<Unit> {
        val schemaByName = descriptor.payloadSchema.associateBy { it.name }
        val unknown = payload.keys - schemaByName.keys
        if (unknown.isNotEmpty()) {
            return Result.failure(
                IllegalArgumentException("UNKNOWN_PAYLOAD_FIELDS:${unknown.sorted().joinToString()}")
            )
        }

        val missing = descriptor.payloadSchema
            .filter { it.required && !payload.containsKey(it.name) }
            .map { it.name }
        if (missing.isNotEmpty()) {
            return Result.failure(
                IllegalArgumentException("MISSING_PAYLOAD_FIELDS:${missing.sorted().joinToString()}")
            )
        }

        for (field in descriptor.payloadSchema) {
            val value = payload[field.name] ?: continue
            val valid = when (field.type) {
                PayloadFieldType.STRING -> true
                PayloadFieldType.BOOLEAN -> value.equals("true", true) || value.equals("false", true)
                PayloadFieldType.INT -> value.toIntOrNull() != null
            }
            if (!valid) {
                return Result.failure(IllegalArgumentException("INVALID_PAYLOAD_FIELD:${field.name}"))
            }
        }
        return Result.success(Unit)
    }

    fun all(): Collection<ActionDescriptor> = descriptors.values

    private fun normalize(value: String): String = value.trim().lowercase()
}
