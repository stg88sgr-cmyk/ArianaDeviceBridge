package de.snowworks.ariana.bridge

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Provider-neutral in-process dialogue handoff for AI modules. */
object DialogueRouter {
    const val MAX_INPUT_CHARS = 1200
    const val MAX_REPLY_CHARS = 2400
    const val PROVIDER_TIMEOUT_MS = 25_000L

    data class Outcome(
        val ok: Boolean,
        val providerId: String? = null,
        val reply: String? = null,
        val error: String? = null,
    )

    fun interface Generator {
        fun generate(text: String): String
    }

    private data class Provider(
        val id: String,
        val generator: Generator,
    )

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ArianaDialogueProvider").apply { isDaemon = true }
    }

    @Volatile private var provider: Provider? = null

    @Synchronized
    fun register(providerId: String, generator: Generator): Boolean {
        val id = providerId.trim().take(80)
        if (!id.matches(Regex("[A-Za-z0-9._:-]{1,80}"))) return false
        provider = Provider(id, generator)
        return true
    }

    @Synchronized
    fun unregister() {
        provider = null
    }

    fun providerId(): String? = provider?.id

    fun generate(rawText: String): Outcome {
        val text = rawText
            .replace(Regex("[\\u0000-\\u001f\\u007f]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_INPUT_CHARS)
        if (text.isEmpty()) return Outcome(ok = false, error = "INVALID_INPUT")

        val current = provider ?: return Outcome(ok = false, error = "PROVIDER_UNAVAILABLE")
        val future = executor.submit<String> { current.generator.generate(text) }
        return try {
            val reply = future.get(PROVIDER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .replace(Regex("[\\u0000-\\u001f\\u007f]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(MAX_REPLY_CHARS)
            if (reply.isEmpty()) {
                Outcome(ok = false, providerId = current.id, error = "EMPTY_REPLY")
            } else {
                Outcome(ok = true, providerId = current.id, reply = reply)
            }
        } catch (_: TimeoutException) {
            future.cancel(true)
            Outcome(ok = false, providerId = current.id, error = "PROVIDER_TIMEOUT")
        } catch (_: Exception) {
            future.cancel(true)
            Outcome(ok = false, providerId = current.id, error = "PROVIDER_FAILED")
        }
    }
}
