package de.snowworks.ariana.xx88y

interface ActionHandler {
    fun canHandle(request: ActionRequest): Boolean
    suspend fun execute(request: ActionRequest): ActionResult
}

interface DeviceBridge {
    val id: String
    suspend fun capabilities(): List<CapabilityState>
    suspend fun execute(request: ActionRequest): ActionResult
}

data class WorkerTask(
    val id: String,
    val prompt: String,
    val metadata: Map<String, String> = emptyMap(),
)

data class WorkerResult(
    val success: Boolean,
    val output: String? = null,
    val error: String? = null,
)

interface AIProvider {
    val providerId: String
    suspend fun isReachable(): Boolean
    suspend fun run(task: WorkerTask): WorkerResult
}

interface AIWorker {
    val state: WorkerState
    suspend fun execute(task: WorkerTask): WorkerResult
}

data class RuntimeCommandResult(
    val success: Boolean,
    val message: String,
    val exitCode: Int? = null,
)

interface RuntimeAdapter {
    suspend fun listSessions(): List<RuntimeSession>
    suspend fun startSession(name: String): RuntimeCommandResult
    suspend fun stopSession(name: String): RuntimeCommandResult
}

interface StudioProvider {
    val state: ProviderState
    suspend fun isReachable(): Boolean
    suspend fun execute(request: ActionRequest): ActionResult
}

interface BuildAdapter {
    suspend fun observe(): BuildState
    suspend fun verify(): ActionResult
}

interface AvatarAdapter {
    suspend fun setMode(mode: AvatarMode): ActionResult
}
