package de.snowworks.ariana.universal.v2

enum class ArianaMode { HOME, GUARDIAN, STUDIO, CITY, SLEEP }
enum class ArianaTaskStatus { QUEUED, WAITING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }
enum class ArianaTaskPriority(val weight: Int) { LOW(10), NORMAL(50), HIGH(80), CRITICAL(100) }
enum class ArianaTaskType { WORKFLOW, BUILD, INSTALL, RECOVERY, DIAGNOSTIC }
enum class ArianaTaskWeight { LIGHT, MEDIUM, HEAVY }

data class ArianaProjectTask(
    val taskId: String,
    val projectId: String,
    val workflowId: String,
    val type: ArianaTaskType,
    val weight: ArianaTaskWeight = ArianaTaskWeight.LIGHT,
    val priority: ArianaTaskPriority = ArianaTaskPriority.NORMAL,
    val status: ArianaTaskStatus = ArianaTaskStatus.QUEUED,
    val dependsOn: Set<String> = emptySet(),
    val retryCount: Int = 0,
    val message: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class ArianaTelemetry(
    val timestamp: Long = System.currentTimeMillis(),
    val batteryPercent: Int,
    val charging: Boolean,
    val thermalStatus: Int,
    val availableRamMb: Long,
    val totalRamMb: Long,
    val screenOn: Boolean,
    val powerSaveMode: Boolean,
    val processPssMb: Long,
)

data class ArianaExecutionBudget(
    val maxParallelTasks: Int,
    val allowHeavyBuilds: Boolean,
    val allowLocalAi: Boolean,
    val allowBackgroundJobs: Boolean,
    val reason: String,
)

data class TaskAdmissionResult(
    val allowed: Boolean,
    val reasons: List<String>,
)
