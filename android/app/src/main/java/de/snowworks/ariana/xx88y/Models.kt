package de.snowworks.ariana.xx88y

import java.util.UUID

enum class ConnectionStatus { NOT_IMPLEMENTED, NOT_CONNECTED, CONNECTING, CONNECTED, ERROR }
enum class PermissionStatus { NOT_REQUIRED, UNKNOWN, NOT_GRANTED, GRANTED }
enum class ActionLevel { SAFE, CONFIRM, BLOCKED }
enum class ActionStatus { PENDING, RUNNING, SUCCESS, FAILED, BLOCKED, NOT_IMPLEMENTED }
enum class WorkerStatus { DISABLED, IDLE, RUNNING, SUCCESS, FAILED, OFFLINE }
enum class PipelineStageStatus { IDLE, RUNNING, SUCCESS, FAILED, BLOCKED }
enum class BuildStatusValue { UNKNOWN, RUNNING, SUCCESS, FAILED }
enum class Severity { INFO, WARNING, ERROR, CRITICAL }
enum class ProviderType { IMAGE, VIDEO, AUDIO, VOICE, AI }
enum class AvatarMode { NEUTRAL, FOCUSED, THINKING, SPEAKING, LISTENING, WARNING, OFFLINE }

data class CapabilityState(
    val id: String,
    val title: String,
    val status: ConnectionStatus = ConnectionStatus.NOT_IMPLEMENTED,
    val permissionStatus: PermissionStatus = PermissionStatus.UNKNOWN,
    val lastError: String? = null,
    val logs: List<String> = emptyList(),
)

data class WorkerState(
    val id: String,
    val name: String,
    val provider: String,
    val status: WorkerStatus = WorkerStatus.OFFLINE,
    val endpoint: String? = null,
    val enabled: Boolean = false,
    val currentTask: String? = null,
    val lastResult: String? = null,
    val lastError: String? = null,
)

data class PipelineStage(
    val name: String,
    val status: PipelineStageStatus = PipelineStageStatus.IDLE,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val result: String? = null,
    val log: List<String> = emptyList(),
)

data class PipelineState(
    val stages: List<PipelineStage> = listOf(
        "OBSERVE", "PLAN", "MUTATE", "BUILD", "TEST", "REPAIR", "VERIFIED RESULT"
    ).map(::PipelineStage),
)

data class RuntimeSession(
    val name: String,
    val running: Boolean = false,
    val pid: Long? = null,
    val lastActivity: Long? = null,
    val lastError: String? = null,
)

data class ProviderState(
    val id: String,
    val name: String,
    val type: ProviderType,
    val endpoint: String? = null,
    val connected: Boolean = false,
    val model: String? = null,
    val capabilities: Set<String> = emptySet(),
    val lastError: String? = null,
)

data class BuildState(
    val repository: String? = null,
    val branch: String? = null,
    val commit: String? = null,
    val buildState: BuildStatusValue = BuildStatusValue.UNKNOWN,
    val testState: BuildStatusValue = BuildStatusValue.UNKNOWN,
    val artifact: String? = null,
    val version: String = "1.14.7-rc1",
    val lastBuild: Long? = null,
)

data class AuditEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val source: String,
    val category: String,
    val severity: Severity,
    val message: String,
    val metadata: Map<String, String> = emptyMap(),
)

data class ActionRequest(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val source: String,
    val target: String,
    val payload: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
)

data class ActionResult(
    val actionId: String,
    val success: Boolean,
    val status: ActionStatus,
    val message: String,
    val data: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
)

data class X88State(
    val masterEnabled: Boolean = false,
    val emergencyStopActive: Boolean = false,
    val bridgeStatus: ConnectionStatus = ConnectionStatus.NOT_CONNECTED,
    val runtimeStatus: ConnectionStatus = ConnectionStatus.NOT_CONNECTED,
    val avatarStatus: ConnectionStatus = ConnectionStatus.NOT_IMPLEMENTED,
    val studioStatus: ConnectionStatus = ConnectionStatus.NOT_CONNECTED,
    val build: BuildState = BuildState(),
    val avatarMode: AvatarMode = AvatarMode.OFFLINE,
    val capabilities: List<CapabilityState> = emptyList(),
    val workers: List<WorkerState> = emptyList(),
    val pipeline: PipelineState = PipelineState(),
    val runtimeSessions: List<RuntimeSession> = emptyList(),
    val providers: List<ProviderState> = emptyList(),
    val auditEvents: List<AuditEvent> = emptyList(),
)
