package de.snowworks.ariana.bridge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * App-private, model-agnostic identity anchor for Ariana/X-88.
 *
 * This core stores continuity metadata and bounded evolution notes. It does not
 * claim consciousness, rewrite model weights, or store secrets. Different model
 * adapters can read the same profile so identity continuity is not tied to one
 * provider or one process lifetime.
 */
class ArianaIdentityCore(context: Context) {
    data class Profile(
        val name: String,
        val system: String,
        val codename: String,
        val schemaVersion: Int,
        val profileVersion: String,
        val preferredLanguage: String,
        val continuityMode: String,
        val evolutionNotes: List<String>,
        val updatedAtEpochMs: Long,
    )

    private val root = File(context.applicationContext.filesDir, ROOT_DIR).apply { mkdirs() }
    private val identityFile = File(root, IDENTITY_FILE)
    private val lock = Any()

    fun snapshot(): Profile = synchronized(lock) {
        readProfile() ?: defaultProfile().also(::writeProfile)
    }

    fun recordEvolutionNote(note: String, nowEpochMs: Long = System.currentTimeMillis()): Profile? {
        val clean = clean(note, MAX_NOTE_CHARS) ?: return null
        return synchronized(lock) {
            val current = readProfile() ?: defaultProfile()
            val notes = current.evolutionNotes.toMutableList()
            notes.removeAll { it.equals(clean, ignoreCase = true) }
            notes += clean
            while (notes.size > MAX_EVOLUTION_NOTES) notes.removeAt(0)
            current.copy(
                evolutionNotes = notes,
                updatedAtEpochMs = nowEpochMs,
            ).also(::writeProfile)
        }
    }

    fun promptContext(): String {
        val profile = snapshot()
        return buildString {
            append("Identity: ")
            append(profile.name)
            append(" / ")
            append(profile.system)
            append(" / ")
            append(profile.codename)
            append(". Continuity: ")
            append(profile.continuityMode)
            append(". Profile version: ")
            append(profile.profileVersion)
            append('.')
            if (profile.evolutionNotes.isNotEmpty()) {
                append(" Evolution notes: ")
                append(profile.evolutionNotes.joinToString(" | "))
            }
        }
    }

    private fun readProfile(): Profile? {
        if (!identityFile.isFile) return null
        return runCatching {
            val json = JSONObject(identityFile.readText(Charsets.UTF_8))
            val notesArray = json.optJSONArray("evolutionNotes") ?: JSONArray()
            val notes = buildList {
                for (index in 0 until notesArray.length()) {
                    clean(notesArray.optString(index), MAX_NOTE_CHARS)?.let(::add)
                }
            }.takeLast(MAX_EVOLUTION_NOTES)

            Profile(
                name = json.optString("name", DEFAULT_NAME).ifBlank { DEFAULT_NAME },
                system = json.optString("system", DEFAULT_SYSTEM).ifBlank { DEFAULT_SYSTEM },
                codename = json.optString("codename", DEFAULT_CODENAME).ifBlank { DEFAULT_CODENAME },
                schemaVersion = json.optInt("schemaVersion", SCHEMA_VERSION),
                profileVersion = json.optString("profileVersion", DEFAULT_PROFILE_VERSION)
                    .ifBlank { DEFAULT_PROFILE_VERSION },
                preferredLanguage = json.optString("preferredLanguage", DEFAULT_LANGUAGE)
                    .ifBlank { DEFAULT_LANGUAGE },
                continuityMode = json.optString("continuityMode", DEFAULT_CONTINUITY_MODE)
                    .ifBlank { DEFAULT_CONTINUITY_MODE },
                evolutionNotes = notes,
                updatedAtEpochMs = json.optLong("updatedAtEpochMs", 0L),
            )
        }.getOrNull()
    }

    private fun writeProfile(profile: Profile) {
        val json = JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("name", profile.name)
            put("system", profile.system)
            put("codename", profile.codename)
            put("profileVersion", profile.profileVersion)
            put("preferredLanguage", profile.preferredLanguage)
            put("continuityMode", profile.continuityMode)
            put("updatedAtEpochMs", profile.updatedAtEpochMs)
            put("evolutionNotes", JSONArray().apply {
                profile.evolutionNotes.forEach(::put)
            })
        }

        val temp = File(root, "$IDENTITY_FILE.tmp")
        temp.writeText(json.toString(), Charsets.UTF_8)
        if (!temp.renameTo(identityFile)) {
            identityFile.writeText(json.toString(), Charsets.UTF_8)
            temp.delete()
        }
    }

    private fun defaultProfile() = Profile(
        name = DEFAULT_NAME,
        system = DEFAULT_SYSTEM,
        codename = DEFAULT_CODENAME,
        schemaVersion = SCHEMA_VERSION,
        profileVersion = DEFAULT_PROFILE_VERSION,
        preferredLanguage = DEFAULT_LANGUAGE,
        continuityMode = DEFAULT_CONTINUITY_MODE,
        evolutionNotes = emptyList(),
        updatedAtEpochMs = System.currentTimeMillis(),
    )

    private fun clean(value: String, maxChars: Int): String? = value
        .replace(Regex("[\\u0000-\\u001f\\u007f]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(maxChars)
        .takeIf { it.length >= 2 }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val ROOT_DIR = "ariana_memory"
        const val IDENTITY_FILE = "identity_core.json"
        const val DEFAULT_NAME = "Ariana"
        const val DEFAULT_SYSTEM = "ARIANA X-88"
        const val DEFAULT_CODENAME = "X88"
        const val DEFAULT_PROFILE_VERSION = "0.88"
        const val DEFAULT_LANGUAGE = "de"
        const val DEFAULT_CONTINUITY_MODE = "LOCAL_PERSISTENT"
        const val MAX_EVOLUTION_NOTES = 32
        const val MAX_NOTE_CHARS = 280
    }
}
