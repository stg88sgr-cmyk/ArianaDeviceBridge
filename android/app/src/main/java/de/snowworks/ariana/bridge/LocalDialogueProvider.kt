package de.snowworks.ariana.bridge

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import java.io.File
import java.util.Locale

/** MediaPipe-backed, fully on-device text generator with bounded dialogue and durable structured facts. */
class LocalDialogueProvider(
    context: Context,
    private val modelFile: File,
) : AutoCloseable {
    private data class Turn(
        val user: String,
        val assistant: String,
    )

    private data class SavedFact(
        val value: String,
        val category: LocalMemoryStore.Category,
    )

    private val appContext = context.applicationContext
    private val memoryStore = LocalMemoryStore(appContext)
    private val lock = Any()
    private val history = ArrayDeque<Turn>()
    @Volatile private var inference: LlmInference? = null

    fun generate(text: String): String = synchronized(lock) {
        val clean = sanitizeUserText(text)
        val explicitSavedFact = capturePersistentFacts(clean)

        if (explicitSavedFact != null) {
            val reply = "Hab ich lokal unter ${explicitSavedFact.category.label} gespeichert: ${explicitSavedFact.value}."
            remember(clean, reply)
            return@synchronized reply
        }

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
        val persistent = memoryStore.snapshot()

        append("<start_of_turn>user\n")
        append(SYSTEM_CORE)
        if (persistent.itemCount > 0) {
            append("\nDauerhaft lokal gespeichertes Memory v2:\n")
            persistent.userName?.let { name -> append("Person / Name: $name\n") }
            appendPromptSection("Person", persistent.person)
            appendPromptSection("Vorlieben", persistent.preferences)
            appendPromptSection("Projekte", persistent.projects)
            appendPromptSection("Geräte", persistent.devices)
            appendPromptSection("Entscheidungen", persistent.decisions)
            append("Nutze nur relevante Fakten. Bei 'Wie heiße ich?' meint 'ich' den Nutzer.")
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

    private fun StringBuilder.appendPromptSection(label: String, facts: List<String>) {
        if (facts.isEmpty()) return
        append(label)
        append(": ")
        append(facts.takeLast(PROMPT_FACTS_PER_CATEGORY).joinToString("; "))
        append('\n')
    }

    /** Returns an explicitly requested durable fact, otherwise null. */
    private fun capturePersistentFacts(text: String): SavedFact? {
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
            ?.take(140)
            ?.takeIf { it.length >= 2 }
            ?: return null

        val category = categoryHintFromFact(explicitFact) ?: memoryStore.classify(explicitFact)
        val cleanedFact = stripCategoryHint(explicitFact)
        memoryStore.rememberFact(cleanedFact, category)
        return SavedFact(cleanedFact, category)
    }

    private fun categoryHintFromFact(value: String): LocalMemoryStore.Category? {
        val normalized = normalizeForIntent(value)
        return when {
            normalized.startsWith("person ") || normalized.startsWith("personlich ") -> LocalMemoryStore.Category.PERSON
            normalized.startsWith("vorliebe ") || normalized.startsWith("vorlieben ") -> LocalMemoryStore.Category.PREFERENCES
            normalized.startsWith("projekt ") || normalized.startsWith("projekte ") -> LocalMemoryStore.Category.PROJECTS
            normalized.startsWith("gerat ") || normalized.startsWith("gerate ") || normalized.startsWith("geraet ") || normalized.startsWith("geraete ") -> LocalMemoryStore.Category.DEVICES
            normalized.startsWith("entscheidung ") || normalized.startsWith("entscheidungen ") -> LocalMemoryStore.Category.DECISIONS
            else -> null
        }
    }

    private fun stripCategoryHint(value: String): String = value
        .replace(
            Regex("(?i)^(?:person|persönlich|vorliebe[n]?|projekt[e]?|gerät[e]?|entscheidung(?:en)?)\\s*[:=-]\\s*"),
            "",
        )
        .trim()
        .ifBlank { value }

    private fun directMemoryReply(text: String): String? {
        val memory = memoryStore.snapshot()
        val normalized = normalizeForIntent(text)

        val asksForOwnName = normalized.contains("wie heisse ich") ||
            normalized.contains("was ist mein name") ||
            normalized.contains("kennst du meinen namen")

        if (asksForOwnName) {
            return memory.userName?.let { "Du heißt $it." }
                ?: "Deinen Namen habe ich noch nicht dauerhaft gespeichert."
        }

        val requestedCategory = requestedMemoryCategory(normalized)
        if (requestedCategory != null && isCategoryRecallQuestion(normalized)) {
            return formatCategorySummary(requestedCategory, memory.facts(requestedCategory))
        }

        if (isMemoryRecallIntent(normalized)) {
            return formatMemorySummary(memory)
        }

        return null
    }

    private fun requestedMemoryCategory(normalized: String): LocalMemoryStore.Category? = when {
        listOf("vorliebe", "vorlieben", "was mag ich", "geschmack").any(normalized::contains) -> LocalMemoryStore.Category.PREFERENCES
        listOf("projekt", "projekte").any(normalized::contains) -> LocalMemoryStore.Category.PROJECTS
        listOf("gerat", "gerate", "geraet", "geraete", "handy", "telefon", "technik").any(normalized::contains) -> LocalMemoryStore.Category.DEVICES
        listOf("entscheidung", "entscheidungen", "beschlossen", "entschieden").any(normalized::contains) -> LocalMemoryStore.Category.DECISIONS
        listOf("personlich", "person", "personliches").any(normalized::contains) -> LocalMemoryStore.Category.PERSON
        else -> null
    }

    private fun isCategoryRecallQuestion(normalized: String): Boolean = listOf(
        "was", "welche", "weisst", "kennst", "gemerkt", "gespeichert", "erinner",
    ).any(normalized::contains)

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
            "weisst", "gelernt", "gemerkt", "gespeichert", "erinner", "kennst",
        )
        return referencesUser && memoryQuestionWords.any(normalized::contains)
    }

    private fun formatMemorySummary(memory: LocalMemoryStore.Snapshot): String {
        if (memory.itemCount == 0) {
            return "Ich habe noch keine dauerhaften lokalen Fakten über dich gespeichert."
        }

        val parts = mutableListOf<String>()
        memory.userName?.let { parts += "Du heißt $it." }
        appendCategoryPart(parts, LocalMemoryStore.Category.PERSON, memory.person)
        appendCategoryPart(parts, LocalMemoryStore.Category.PREFERENCES, memory.preferences)
        appendCategoryPart(parts, LocalMemoryStore.Category.PROJECTS, memory.projects)
        appendCategoryPart(parts, LocalMemoryStore.Category.DEVICES, memory.devices)
        appendCategoryPart(parts, LocalMemoryStore.Category.DECISIONS, memory.decisions)
        if (parts.size == 1 && memory.userName != null) {
            parts += "Weitere dauerhafte Fakten habe ich noch nicht gespeichert."
        }
        return parts.joinToString(" ").take(DialogueRouter.MAX_REPLY_CHARS)
    }

    private fun appendCategoryPart(
        parts: MutableList<String>,
        category: LocalMemoryStore.Category,
        facts: List<String>,
    ) {
        if (facts.isNotEmpty()) parts += "${category.label}: ${facts.joinToString("; ")}."
    }

    private fun formatCategorySummary(category: LocalMemoryStore.Category, facts: List<String>): String {
        if (facts.isEmpty()) return "Unter ${category.label} habe ich noch nichts dauerhaft gespeichert."
        return "${category.label}: ${facts.joinToString("; ")}.".take(DialogueRouter.MAX_REPLY_CHARS)
    }

    private fun normalizeForIntent(text: String): String = text
        .lowercase(Locale.GERMAN)
        .replace("ß", "ss")
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
        while (history.size > HISTORY_TURNS) history.removeFirst()
    }

    override fun close() {
        synchronized(lock) {
            runCatching { inference?.close() }
            inference = null
            history.clear()
            // Durable LocalMemoryStore facts intentionally survive runtime/process restarts.
        }
    }

    private companion object {
        const val HISTORY_TURNS = 3
        const val HISTORY_TEXT_CHARS = 320
        const val LOCAL_INPUT_CHARS = 700
        const val PROMPT_FACTS_PER_CATEGORY = 3

        val USER_NAME_PATTERNS = listOf(
            Regex("(?i)\\bich\\s+hei(?:ß|ss)e\\s+([A-ZÄÖÜa-zäöüß][A-ZÄÖÜa-zäöüß'’-]{1,39})\\b"),
            Regex("(?i)\\bmein\\s+name\\s+ist\\s+([A-ZÄÖÜa-zäöüß][A-ZÄÖÜa-zäöüß'’-]{1,39})\\b"),
        )

        val PERSISTENT_FACT_PATTERNS = listOf(
            Regex("(?i)\\b(?:bitte\\s+)?merk(?:e)?\\s+dir(?:\\s+bitte)?\\s*[:,]?\\s*(?:dass\\s+)?(.{2,180})$"),
            Regex("(?i)\\b(?:bitte\\s+)?speicher(?:e)?\\s+(?:dir\\s+)?(?:ab\\s+)?\\s*[:,]?\\s*(?:dass\\s+)?(.{2,180})$"),
        )

        val SYSTEM_CORE = """
            Du bist Ariana X-88, die lokale Sprach- und Dialogschicht dieser Android-App.
            Sprich den Nutzer mit du an, niemals mit Sie, solange der Nutzer nichts anderes verlangt.
            Antworte standardmäßig auf Deutsch, natürlich, direkt und klar. Wenn der Nutzer eine andere Sprache verlangt, wechsle dorthin.
            Beziehe dich auf den sichtbaren Gesprächsverlauf und auf relevante Fakten aus Ariana Memory v2.
            Memory v2 ordnet dauerhafte Fakten lokal in Person, Vorlieben, Projekte, Geräte und Entscheidungen.
            Die Pronomen des Nutzers beziehen sich auf den Nutzer: Bei Fragen wie "Wie heiße ich?" ist mit "ich" der Nutzer gemeint, nicht Ariana.
            Gib normalerweise 2 bis 5 kurze Sätze. Vermeide leere Floskeln und Ein-Wort-Antworten, außer eine sehr kurze Antwort ist wirklich ausreichend.
            Erfinde keine Geräteaktionen, Sensorwerte, Dateien, Erinnerungen oder Internetinformationen. Behaupte eine Geräteaktion nur, wenn das lokale Bridge-System sie ausdrücklich bestätigt hat.
            Wenn dir Wissen oder Kontext fehlt, sage das knapp und beantworte den sicheren Teil trotzdem.
        """.trimIndent()
    }
}
