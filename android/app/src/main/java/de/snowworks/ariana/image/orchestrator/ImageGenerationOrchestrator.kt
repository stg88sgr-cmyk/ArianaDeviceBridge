package de.snowworks.ariana.image.orchestrator

import de.snowworks.ariana.image.generator.EndpointSecurity
import de.snowworks.ariana.image.generator.LocalImageGenerator
import de.snowworks.ariana.image.model.GenerationState
import de.snowworks.ariana.image.model.GeneratorBackend
import de.snowworks.ariana.image.model.ImageGenerationRequest
import de.snowworks.ariana.image.policy.ImagePolicyEngine
import de.snowworks.ariana.image.policy.PolicyDecision
import de.snowworks.ariana.image.storage.PrivateMediaStore
import de.snowworks.ariana.image.validation.ImageRequestValidator
import de.snowworks.ariana.image.validation.OutputValidator
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeout

class ImageGenerationOrchestrator(
    private val policyEngine: ImagePolicyEngine,
    private val privateMediaStore: PrivateMediaStore,
    generators: Collection<LocalImageGenerator>,
    private val requestValidator: ImageRequestValidator = ImageRequestValidator(),
    private val outputValidator: OutputValidator = OutputValidator(),
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val generators = generators.associateBy { it.backend }
    private val activeJobs = ConcurrentHashMap<String, Deferred<GenerationState>>()
    private val stateByRequest = ConcurrentHashMap<String, GenerationState>()
    private val auditLog = ArrayDeque<AuditEntry>()
    private val _state = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val state: StateFlow<GenerationState> = _state

    @Volatile private var emergencyStopped = false

    data class AuditEntry(
        val requestId: String,
        val backend: GeneratorBackend,
        val result: String,
        val durationMs: Long,
        val errorCode: String? = null,
        val timestamp: Long = System.currentTimeMillis(),
    )

    fun getAuditLog(): List<AuditEntry> = synchronized(auditLog) { auditLog.toList() }

    fun setEmergencyStopped(stopped: Boolean) {
        emergencyStopped = stopped
        if (stopped) activeJobs.values.forEach { it.cancel(CancellationException("emergency stop")) }
    }

    suspend fun generate(request: ImageGenerationRequest): GenerationState {
        if (emergencyStopped) return fail(request, "EMERGENCY_STOP", "blocked")

        val validation = requestValidator.validate(request)
        if (!validation.isValid) return fail(request, "VALIDATION_FAILED", validation.error ?: "invalid")

        if (request.backend == GeneratorBackend.CUSTOM_LOCAL_ENDPOINT) {
            try { EndpointSecurity.validate(request.modelId!!) } catch (e: SecurityException) {
                return fail(request, "ENDPOINT_REJECTED", e.message ?: "endpoint rejected")
            }
        }

        val decision = try { withTimeout(5_000) { policyEngine.evaluate(request) } } catch (_: Exception) {
            return fail(request, "POLICY_ERROR", "policy evaluation failed")
        }
        if (decision is PolicyDecision.Block) return fail(request, "POLICY_BLOCK", decision.reason)

        val deferred = scope.async(start = CoroutineStart.LAZY) { runGeneration(request) }
        val existing = activeJobs.putIfAbsent(request.requestId, deferred)
        if (existing != null) return fail(request, "DUPLICATE_REQUEST_ID", "requestId already active")
        deferred.invokeOnCompletion { activeJobs.remove(request.requestId, deferred) }
        deferred.start()
        return try { deferred.await() } catch (_: CancellationException) {
            val cancelled = GenerationState.Cancelled
            updateState(request.requestId, cancelled)
            audit(request, "CANCELLED", 0, "CANCELLED")
            cancelled
        }
    }

    suspend fun cancel(requestId: String) {
        activeJobs[requestId]?.cancel(CancellationException("cancelled"))
        generators.values.forEach { it.cancel(requestId) }
    }

    fun getStatus(requestId: String): GenerationState = stateByRequest[requestId] ?: GenerationState.Idle

    private suspend fun runGeneration(request: ImageGenerationRequest): GenerationState {
        val generator = generators[request.backend] ?: return fail(request, "BACKEND_NOT_CONFIGURED", request.backend.name)
        val started = System.currentTimeMillis()
        return try {
            updateState(request.requestId, GenerationState.Queued)
            val bytes = withTimeout(120_000) {
                generator.generate(request) { progress ->
                    updateState(request.requestId, GenerationState.Running(progress.coerceIn(0f, 1f)))
                }
            }
            val output = outputValidator.validateImageFile(bytes)
            if (!output.isValid) return fail(request, "OUTPUT_INVALID", output.error ?: "invalid output")
            val stored = privateMediaStore.save(request.requestId, bytes, request.width, request.height, request.backend, request.modelId)
            GenerationState.Completed(stored).also {
                updateState(request.requestId, it)
                audit(request, "COMPLETED", System.currentTimeMillis() - started, null)
            }
        } catch (e: CancellationException) {
            updateState(request.requestId, GenerationState.Cancelled)
            throw e
        } catch (e: Exception) {
            fail(request, "GENERATOR_ERROR", e.message ?: "unknown")
        }
    }

    private fun fail(request: ImageGenerationRequest, code: String, message: String): GenerationState.Failed {
        val state = GenerationState.Failed(code, message)
        updateState(request.requestId, state)
        audit(request, "FAILED", 0, code)
        return state
    }

    private fun updateState(requestId: String, state: GenerationState) {
        stateByRequest[requestId] = state
        _state.value = state
    }

    private fun audit(request: ImageGenerationRequest, result: String, durationMs: Long, errorCode: String?) {
        synchronized(auditLog) {
            auditLog.addLast(AuditEntry(request.requestId, request.backend, result, durationMs, errorCode?.take(200)))
            while (auditLog.size > 500) auditLog.removeFirst()
        }
    }

    override fun close() {
        activeJobs.values.forEach { it.cancel() }
        generators.values.forEach { runCatching { it.close() } }
        scope.cancel()
    }
}
