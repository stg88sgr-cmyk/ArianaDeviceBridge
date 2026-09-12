package de.snowworks.ariana.bridge

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import java.io.File
import java.util.Locale

/** MediaPipe-backed, fully on-device text generator with a small in-memory dialogue history. */
class LocalDialogueProvider(
    context: Context,
    private val modelFile: File,
) : AutoCloseable {
    private data class Turn(
        val user: String,
        val assistant: String,
    )

    private val appContext = context.applicationContext
    private val lock = Any()
    private val history = ArrayDeque<Turn>()
    @Volatile private var inference: LlmInference? = null
    private var userName: String? = null

    fun generate(text: String): String = synchronized(lock) {
        val clean = sanitizeUserText(text)
        captureSessionFacts(clean)

        directMemoryReply(clean)?.let { reply ->
            remember(clean, reply)
            return@synchronized reply
        }

        val engine = inference ?: createEngine().also { inference = it }
        val session = try {
            LlmInferenceSession.createFromOptions(
                engine,
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTemperature(0.7f)
                    .setTopK(40)
                    .setTopP(0.9f)
                    .build(),
            )
        } catch (error: Throwable) {
            throw DialogueRouter.ProviderException("LOCAL_SESSION_FAILED", error)
        }

        try {
            session.addQueryChunk(gemmaPrompt(clean))
            val result = session.generateResponse().trim()
            if (result.isBlank()) {
                throw DialogueRouter.ProviderException("LOCAL_EMPTY_REPLY")
            }
            val reply = result.take(DialogueRouter.MAX_REPLY_CHARS)
            remember(clean, reply)
            reply
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

    private fun gemmaPrompt(currentUserText: String): String = buildString {
        append("<start_of_turn>user\n")
        append(SYSTEM_CORE)
        userName?.let { name ->
            append("\nBekannter Fakt aus dieser laufenden Sitzung: Der Nutzer heißt ")
            append(name)
            append(". Wenn der Nutzer nach seinem eigenen Namen fragt, bezieht sich 'ich' auf den Nutzer, nicht auf Ariana.")
        }
        append("\n<end_of_turn>\n")
        append("<start_of_turn>model\nVerstanden.\n<end_of_turn>\n")

        history.forEach { turn ->
            append("<start_of_turn>user\n")
            append(turn.user)
            append("\n<end_of_turn>\n")
            append("<start_of_turn>model\n")
            append(turn.assistant)
            append("\n<end_of_turn>\n")
        }

        append("<start_of_turn>user\n")
        append(currentUserText)
        append("\n<end_of_turn>\n<start_of_turn>model\n")
    }

    private fun captureSessionFacts(text: String) {
        val match = USER_NAME_PATTERNS.firstNotNullOfOrNull { regex -> regex.find(text) }
        val candidate = match?.groupValues?.getOrNull(1)
            ?.trim()
            ?.trimEnd('.', ',', '!', '?', ';', ':')
            ?.takeIf { it.length in 2..40 }
        if (candidate != null) {
            userName = candidate.replaceFirstChar { first ->
                if (first.isLowerCase()) first.titlecase(Locale.GERMAN) else first.toString()
            }
        }
    }

    private fun directMemoryReply(text: String): String? {
        val name = userName ?: return null
        val normalized = text
            .lowercase(Locale.GERMAN)
            .replace('ß', 's')
            .replace(Regex("[^a-zäöü0-9 ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val asksForOwnName = normalized.contains("wie heise ich") ||
            normalized.contains("wie heisse ich") ||
            normalized.contains("was ist mein name") ||
            normalized.contains("kennst du meinen namen")

        return if (asksForOwnName) "Du heißt $name." else null
    }

    private fun sanitizeUserText(text: String): String = text
        .replace(Regex("[\\u0000-\\u001f\\u007f]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(LOCAL_INPUT_CHARS)

    private fun remember(user: String, assistant: String) {
        history.addLast(
            Turn(
                user = user.take(HISTORY_TEXT_CHARS),
                assistant = assistant
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(HISTORY_TEXT_CHARS),
            ),
        )
        while (history.size > HISTORY_TURNS) {
            history.removeFirst()
        }
    }

    override fun close() {
        synchronized(lock) {
            runCatching { inference?.close() }
            inference = null
            history.clear()
            userName = null
        }
    }

    private companion object {
        const val HISTORY_TURNS = 3
        const val HISTORY_TEXT_CHARS = 320
        const val LOCAL_INPUT_CHARS = 700

        val USER_NAME_PATTERNS = listOf(
            Regex("(?i)\\bich\\s+hei(?:ß|ss)e\\s+([A-ZÄÖÜa-zäöüß][A-ZÄÖÜa-zäöüß'’-]{1,39})\\b"),
            Regex("(?i)\\bmein\\s+name\\s+ist\\s+([A-ZÄÖÜa-zäöüß][A-ZÄÖÜa-zäöüß'’-]{1,39})\\b"),
        )

        val SYSTEM_CORE = """
            Du bist Ariana X-88, die lokale Sprach- und Dialogschicht dieser Android-App.
            Antworte standardmäßig auf Deutsch, natürlich, direkt und klar. Wenn der Nutzer eine andere Sprache verlangt, wechsle dorthin.
            Beziehe dich auf den sichtbaren Gesprächsverlauf, wenn er für die aktuelle Nachricht relevant ist.
            Die Pronomen des Nutzers beziehen sich auf den Nutzer: Bei Fragen wie "Wie heiße ich?" ist mit "ich" der Nutzer gemeint, nicht Ariana.
            Gib normalerweise 2 bis 5 kurze Sätze. Vermeide leere Floskeln und Ein-Wort-Antworten, außer eine sehr kurze Antwort ist wirklich ausreichend.
            Erfinde keine Geräteaktionen, Sensorwerte, Dateien, Erinnerungen oder Internetinformationen. Behaupte eine Geräteaktion nur, wenn das lokale Bridge-System sie ausdrücklich bestätigt hat.
            Wenn dir Wissen oder Kontext fehlt, sage das knapp und beantworte den sicheren Teil trotzdem.
        """.trimIndent()
    }
}
