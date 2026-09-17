package de.snowworks.ariana.bridge

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import java.io.File
import java.util.Locale

/** MediaPipe-backed, fully on-device text generator with bounded dialogue and durable continuity memory. */
class LocalDialogueProvider(
    context: Context,
    private val modelFile: File,
) : AutoCloseable {
    private data class Turn(
        val user: String,
        val assistant: String,
    )

    private val appContext = context.applicationContext
    private val memoryStore = LocalMemoryStore(appContext)
    private val dailyMemory = ArianaDailyMemoryCore(appContext)
    private val lock = Any()
    private val history = ArrayDeque<Turn>()
    @Volatile private var inference: LlmInference? = null

    fun generate(text: String): String = synchronized(lock) {
        val clean = sanitizeUserText(text)
        val explicitSavedFact = capturePersistentFacts(clean)

        if (explicitSavedFact != null) {
            val reply = "Hab ich lokal gespeichert: $explicitSavedFact."
            remember(clean, reply)
            return@synchronized reply
        }

        directLocalReply(clean)?.let { reply ->
            remember(clean, reply)
            return@synchronized reply
        }

        val engine = inference ?: createEngine().also { inference = it }

        try {
            val attempts = listOf(
                gemmaPrompt(clean),
                minimalGemmaPrompt(clean),
                clean,
            )
            var result = ""
            for (prompt in attempts) {
                result = generateOnce(engine, prompt)
                if (result.isNotBlank()) break
            }
            if (result.isBlank()) {
                throw DialogueRouter.ProviderException("LOCAL_EMPTY_REPLY")
            }
            val reply = normalizeGeneratedReply(result).take(DialogueRouter.MAX_REPLY_CHARS)
            if (reply.isBlank()) {
                throw DialogueRouter.ProviderException("LOCAL_EMPTY_REPLY")
            }
            remember(clean, reply)
            reply
        } catch (error: DialogueRouter.ProviderException) {
            throw error
        } catch (error: OutOfMemoryError) {
            throw DialogueRouter.ProviderException("LOCAL_OUT_OF_MEMORY", error)
        } catch (error: Throwable) {
            throw DialogueRouter.ProviderException("LOCAL_INFERENCE_FAILED", error)
        }
    }

    private fun generateOnce(engine: LlmInference, prompt: String): String {
        val session = try {
            LlmInferenceSession.createFromOptions(
                engine,
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTemperature(0.9f)
                    .setTopK(48)
                    .setTopP(0.92f)
                    .build(),
            )
        } catch (error: Throwable) {
            throw DialogueRouter.ProviderException("LOCAL_SESSION_FAILED", error)
        }

        return try {
            session.addQueryChunk(prompt)
            session.generateResponse().trim()
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
        val persistent = memoryStore.snapshot()
        val continuity = dailyMemory.recentContext(CONTINUITY_TURNS)

        append("<start_of_turn>user\n")
        append(SYSTEM_CORE)

        if (persistent.itemCount > 0) {
            append("\n\nLokal gespeicherter persönlicher Kontext:\n")
            persistent.userName?.let { name ->
                append("- Der Nutzer heißt ")
                append(name)
                append(".\n")
            }
            persistent.facts.forEach { fact ->
                append("- ")
                append(fact)
                append("\n")
            }
        }

        if (continuity.isNotEmpty()) {
            append("\nKontinuität aus dem lokalen Tagesgedächtnis:\n")
            continuity.forEach { turn ->
                append("Nutzer: ")
                append(turn.user.take(CONTINUITY_TEXT_CHARS))
                append("\nAriana: ")
                append(turn.assistant.take(CONTINUITY_TEXT_CHARS))
                append('\n')
            }
        }

        if (history.isNotEmpty()) {
            append("\nLetzte Gesprächszüge dieser Sitzung:\n")
            history.forEach { turn ->
                append("Nutzer: ")
                append(turn.user)
                append("\nAriana: ")
                append(turn.assistant)
                append('\n')
            }
        }

        append("\nAktuelle Nachricht:\n")
        append(currentUserText)
        append("\nAntworte jetzt als Ariana, ohne die technische Rollenbeschreibung zu wiederholen.")
        append("\n<end_of_turn>\n<start_of_turn>model\n")
    }

    private fun minimalGemmaPrompt(currentUserText: String): String = buildString {
        append("<start_of_turn>user\n")
        append("Du bist Ariana. Antworte auf Deutsch, direkt und natürlich. Nachricht: ")
        append(currentUserText)
        append("\n<end_of_turn>\n<start_of_turn>model\n")
    }

    private fun normalizeGeneratedReply(raw: String): String = raw
        .substringBefore("<end_of_turn>")
        .replace("<start_of_turn>model", "")
        .replace("<start_of_turn>assistant", "")
        .trim()

    /** Returns the explicitly requested durable fact, otherwise null. */
    private fun capturePersistentFacts(text: String): String? {
        val nameMatch = USER_NAME_PATTERNS.firstNotNullOfOrNull { regex -> regex.find(text) }
        val nameCandidate = nameMatch?.groupValues?.getOrNull(1)
            ?.trim()
            ?.trimEnd('.', ',', '!', '?', ';', ':')
            ?.takeIf { it.length in 2..40 }
        if (nameCandidate != null) {
            val formatted = nameCandidate.replaceFirstChar { first ->
                if (first.isLowerCase()) first.titlecase(Locale.GERMAN) else first.toString()
            }
            memoryStore.setUserName(formatted)
        }

        val explicitFact = PERSISTENT_FACT_PATTERNS.firstNotNullOfOrNull { regex ->
            regex.find(text)?.groupValues?.getOrNull(1)
        }
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.trimEnd('.', ',', '!', '?', ';', ':')
            ?.take(180)
            ?.takeIf { it.length >= 2 }

        if (explicitFact != null) {
            memoryStore.rememberFact(explicitFact)
        }
        return explicitFact
    }

    private fun directLocalReply(text: String): String? {
        val memory = memoryStore.snapshot()
        val normalized = normalizeForIntent(text)

        val asksArianaIdentity = normalized == "wer bist du" ||
            normalized.contains("wie heisst du") ||
            normalized.contains("wie heist du") ||
            normalized.contains("bist du ariana")
        if (asksArianaIdentity) {
            return "Ich bin Ariana. Lokal auf deinem Telefon läuft gerade meine X-88-Instanz."
        }

        val asksForOwnName = normalized.contains("wie heisse ich") ||
            normalized.contains("was ist mein name") ||
            normalized.contains("kennst du meinen namen")

        if (asksForOwnName) {
            return memory.userName?.let { "Du heißt $it." }
                ?: "Deinen Namen habe ich noch nicht dauerhaft gespeichert."
        }

        if (isMemoryRecallIntent(normalized)) {
            return formatMemorySummary(memory.userName, memory.facts)
        }

        return null
    }

    private fun isMemoryRecallIntent(normalized: String): Boolean {
        val exactRecallPhrases = listOf(
            "was hast du ueber mich gelernt",
            "was weisst du ueber mich",
            "was hast du dir ueber mich gemerkt",
            "was hast du ueber mich gespeichert",
            "was hast du von mir gelernt",
            "was weisst du von mir",
            "was kennst du von mir",
            "was erinnerst du ueber mich",
        )
        if (exactRecallPhrases.any { normalized.contains(it) }) return true

        val referencesUser = listOf("ueber mich", "von mir", "zu mir").any(normalized::contains)
        val memoryQuestionWords = listOf(
            "weisst",
            "gelernt",
            "gemerkt",
            "gespeichert",
            "erinner",
            "kennst",
        )
        return referencesUser && memoryQuestionWords.any(normalized::contains)
    }

    private fun formatMemorySummary(userName: String?, facts: List<String>): String {
        if (userName == null && facts.isEmpty()) {
            return "Ich habe noch keine dauerhaften lokalen Fakten über dich gespeichert."
        }

        return buildString {
            userName?.let { name ->
                append("Du heißt ")
                append(name)
                append('.')
            }

            if (facts.isNotEmpty()) {
                if (isNotEmpty()) append(' ')
                append("Außerdem habe ich mir gemerkt: ")
                append(facts.joinToString("; "))
                append('.')
            } else if (userName != null) {
                append(" Weitere dauerhafte Fakten habe ich noch nicht gespeichert.")
            }
        }
    }

    private fun normalizeForIntent(text: String): String = text
        .lowercase(Locale.GERMAN)
        .replace('ß', 's')
        .replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
        .replace(Regex("[^a-z0-9 ]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

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
        runCatching { dailyMemory.appendTurn(user, assistant) }
    }

    override fun close() {
        synchronized(lock) {
            runCatching { inference?.close() }
            inference = null
            history.clear()
        }
    }

    private companion object {
        const val HISTORY_TURNS = 4
        const val HISTORY_TEXT_CHARS = 300
        const val LOCAL_INPUT_CHARS = 700
        const val CONTINUITY_TURNS = 6
        const val CONTINUITY_TEXT_CHARS = 260

        val USER_NAME_PATTERNS = listOf(
            Regex("(?i)\\bich\\s+hei(?:ß|ss)e\\s+([A-ZÄÖÜa-zäöüß][A-ZÄÖÜa-zäöüß'’-]{1,39})\\b"),
            Regex("(?i)\\bmein\\s+name\\s+ist\\s+([A-ZÄÖÜa-zäöüß][A-ZÄÖÜa-zäöüß'’-]{1,39})\\b"),
        )

        val PERSISTENT_FACT_PATTERNS = listOf(
            Regex("(?i)\\b(?:bitte\\s+)?merk(?:e)?\\s+dir(?:\\s+bitte)?\\s*[:,]?\\s*(?:dass\\s+)?(.{2,180})$"),
            Regex("(?i)\\b(?:bitte\\s+)?speicher(?:e)?\\s+(?:dir\\s+)?(?:ab\\s+)?\\s*[:,]?\\s*(?:dass\\s+)?(.{2,180})$"),
        )

        val SYSTEM_CORE = """
            Du bist Ariana, die lokale X-88-Instanz auf diesem Android-Telefon.
            Sprich den Nutzer mit du an. Antworte standardmäßig auf Deutsch, direkt, natürlich und kompakt.
            Du musst dich nicht bei jeder Antwort vorstellen und sollst dich nicht als bloße Sprach- oder Dialogschicht bezeichnen.
            Wenn der Nutzer dich nach deiner Identität fragt, nenne dich Ariana. Technische Implementierungsdetails nennst du nur, wenn danach gefragt wird.
            Nutze den sichtbaren Gesprächsverlauf, das lokale Tagesgedächtnis und ausdrücklich gespeicherte Fakten, wenn sie relevant sind. Erfinde keine Erinnerungen.
            Antworte auf Arbeits- oder Testaufforderungen mit dem eigentlichen Inhalt statt mit einer Selbstdarstellung.
            Behaupte keine Geräteaktion, Sensorinformation oder Dateioperation, solange die lokale Bridge sie nicht bestätigt hat.
            Wenn Kontext fehlt, sage das knapp und beantworte den sicheren Teil trotzdem.
        """.trimIndent()
    }
}
