package de.snowworks.ariana.bridge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * App-private continuity memory for Ariana/X-88.
 *
 * Design goals:
 * - save continuously, not only at shutdown
 * - keep one bounded file per local calendar day
 * - expose recent context for the next session/day
 * - never store secrets, tokens or arbitrary binary data
 * - tolerate partial/corrupt files by failing closed to an empty day
 */
class ArianaDailyMemoryCore(context: Context) {
    data class MemoryTurn(
        val id: String,
        val timestampEpochMs: Long,
        val user: String,
        val assistant: String,
    )

    data class DaySnapshot(
        val date: String,
        val updatedAtEpochMs: Long,
        val turns: List<MemoryTurn>,
    )

    private val root = File(context.applicationContext.filesDir, ROOT_DIR).apply { mkdirs() }
    private val zone: ZoneId = ZoneId.systemDefault()
    private val lock = Any()

    fun appendTurn(user: String, assistant: String, nowEpochMs: Long = System.currentTimeMillis()) {
        val cleanUser = clean(user, MAX_USER_CHARS) ?: return
        val cleanAssistant = clean(assistant, MAX_ASSISTANT_CHARS) ?: return

        synchronized(lock) {
            val date = localDate(nowEpochMs)
            val current = readDay(date)
            val turns = current.turns.toMutableList()
            turns += MemoryTurn(
                id = UUID.randomUUID().toString(),
                timestampEpochMs = nowEpochMs,
                user = cleanUser,
                assistant = cleanAssistant,
            )
            while (turns.size > MAX_TURNS_PER_DAY) turns.removeAt(0)
            writeDay(
                DaySnapshot(
                    date = date,
                    updatedAtEpochMs = nowEpochMs,
                    turns = turns,
                ),
            )
            pruneOldDays()
        }
    }

    fun recentContext(maxTurns: Int = DEFAULT_CONTEXT_TURNS): List<MemoryTurn> = synchronized(lock) {
        root.listFiles()
            ?.filter { it.isFile && it.name.startsWith(DAY_PREFIX) && it.extension == "json" }
            ?.sortedByDescending { it.name }
            ?.take(CONTEXT_DAY_WINDOW)
            ?.flatMap { file -> readFile(file).turns }
            ?.sortedByDescending { it.timestampEpochMs }
            ?.take(maxTurns.coerceIn(1, MAX_CONTEXT_TURNS))
            ?.sortedBy { it.timestampEpochMs }
            ?: emptyList()
    }

    fun todaySnapshot(nowEpochMs: Long = System.currentTimeMillis()): DaySnapshot = synchronized(lock) {
        readDay(localDate(nowEpochMs))
    }

    fun clearAll() = synchronized(lock) {
        root.listFiles()?.forEach { file -> runCatching { file.delete() } }
    }

    private fun readDay(date: String): DaySnapshot = readFile(dayFile(date), fallbackDate = date)

    private fun readFile(file: File, fallbackDate: String = dateFromFile(file)): DaySnapshot {
        if (!file.isFile) return DaySnapshot(fallbackDate, 0L, emptyList())
        return runCatching {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            val array = json.optJSONArray("turns") ?: JSONArray()
            val turns = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val user = clean(item.optString("user"), MAX_USER_CHARS) ?: continue
                    val assistant = clean(item.optString("assistant"), MAX_ASSISTANT_CHARS) ?: continue
                    add(
                        MemoryTurn(
                            id = item.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                            timestampEpochMs = item.optLong("timestampEpochMs", 0L),
                            user = user,
                            assistant = assistant,
                        ),
                    )
                }
            }.takeLast(MAX_TURNS_PER_DAY)
            DaySnapshot(
                date = json.optString("date").takeIf { it.isNotBlank() } ?: fallbackDate,
                updatedAtEpochMs = json.optLong("updatedAtEpochMs", 0L),
                turns = turns,
            )
        }.getOrElse {
            DaySnapshot(fallbackDate, 0L, emptyList())
        }
    }

    private fun writeDay(snapshot: DaySnapshot) {
        val target = dayFile(snapshot.date)
        val temp = File(root, target.name + ".tmp")
        val json = JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("date", snapshot.date)
            put("updatedAtEpochMs", snapshot.updatedAtEpochMs)
            put("turns", JSONArray().apply {
                snapshot.turns.forEach { turn ->
                    put(JSONObject().apply {
                        put("id", turn.id)
                        put("timestampEpochMs", turn.timestampEpochMs)
                        put("user", turn.user)
                        put("assistant", turn.assistant)
                    })
                }
            })
        }
        temp.writeText(json.toString(), Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            target.writeText(json.toString(), Charsets.UTF_8)
            temp.delete()
        }
    }

    private fun pruneOldDays() {
        root.listFiles()
            ?.filter { it.isFile && it.name.startsWith(DAY_PREFIX) && it.extension == "json" }
            ?.sortedByDescending { it.name }
            ?.drop(MAX_RETAINED_DAYS)
            ?.forEach { file -> runCatching { file.delete() } }
    }

    private fun dayFile(date: String) = File(root, "$DAY_PREFIX$date.json")

    private fun localDate(epochMs: Long): String = Instant.ofEpochMilli(epochMs)
        .atZone(zone)
        .toLocalDate()
        .format(DATE_FORMAT)

    private fun dateFromFile(file: File): String = file.name
        .removePrefix(DAY_PREFIX)
        .removeSuffix(".json")
        .takeIf { runCatching { LocalDate.parse(it, DATE_FORMAT) }.isSuccess }
        ?: "unknown"

    private fun clean(value: String, maxChars: Int): String? = value
        .replace(Regex("[\\u0000-\\u001f\\u007f]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(maxChars)
        .takeIf { it.length >= 2 }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val ROOT_DIR = "ariana_memory"
        const val DAY_PREFIX = "day_"
        const val MAX_TURNS_PER_DAY = 80
        const val MAX_RETAINED_DAYS = 45
        const val DEFAULT_CONTEXT_TURNS = 10
        const val MAX_CONTEXT_TURNS = 16
        const val CONTEXT_DAY_WINDOW = 3
        const val MAX_USER_CHARS = 900
        const val MAX_ASSISTANT_CHARS = 1800
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
