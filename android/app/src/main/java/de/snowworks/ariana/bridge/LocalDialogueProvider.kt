package de.snowworks.ariana.bridge

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import java.io.File

/** MediaPipe-backed, fully on-device text generator. */
class LocalDialogueProvider(
    context: Context,
    private val modelFile: File,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val lock = Any()
    @Volatile private var inference: LlmInference? = null

    fun generate(text: String): String = synchronized(lock) {
        val engine = inference ?: createEngine().also { inference = it }
        val session = try {
            LlmInferenceSession.createFromOptions(
                engine,
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTemperature(0.65f)
                    .setTopK(40)
                    .setTopP(0.9f)
                    .build(),
            )
        } catch (error: Throwable) {
            throw DialogueRouter.ProviderException("LOCAL_SESSION_FAILED", error)
        }

        try {
            session.addQueryChunk(gemmaPrompt(text))
            val result = session.generateResponse().trim()
            if (result.isBlank()) {
                throw DialogueRouter.ProviderException("LOCAL_EMPTY_REPLY")
            }
            result.take(DialogueRouter.MAX_REPLY_CHARS)
        } catch (error: DialogueRouter.ProviderException) {
            throw error
        } catch (error: OutOfMemoryError) {
            throw DialogueRouter.ProviderException("LOCAL_OUT_OF_MEMORY", error)
        } catch (error: Throwable) {
            throw DialogueRouter.ProviderException("LOCAL_INFERENCE_FAILED", error)
        } finally {
            runCatching { session.close() }
        }
    }

    private fun createEngine(): LlmInference {
        if (!modelFile.isFile) {
            throw DialogueRouter.ProviderException("LOCAL_MODEL_MISSING")
        }
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(1024)
            .build()
        return try {
            LlmInference.createFromOptions(appContext, options)
        } catch (error: OutOfMemoryError) {
            throw DialogueRouter.ProviderException("LOCAL_OUT_OF_MEMORY", error)
        } catch (error: Throwable) {
            throw DialogueRouter.ProviderException("LOCAL_MODEL_LOAD_FAILED", error)
        }
    }

    private fun gemmaPrompt(text: String): String {
        val clean = text.trim().take(DialogueRouter.MAX_INPUT_CHARS)
        return buildString {
            append("<start_of_turn>user\n")
            append("Du bist Ariana X-88. Antworte natürlich, direkt und knapp auf Deutsch, außer der Nutzer verlangt eine andere Sprache. ")
            append("Behaupte keine Geräteaktion, die nicht ausdrücklich vom lokalen Bridge-System bestätigt wurde.\n\n")
            append(clean)
            append("<end_of_turn>\n<start_of_turn>model\n")
        }
    }

    override fun close() {
        synchronized(lock) {
            runCatching { inference?.close() }
            inference = null
        }
    }
}
