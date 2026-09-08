package de.snowworks.ariana.gi

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidEncryptedGiExperienceStore(context: Context) : GiExperienceStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun get(key: String, route: String): GiExperience? {
        val storageKey = storageKey(key, route)
        val encrypted = prefs.getString(storageKey, null) ?: return null
        return runCatching { decode(decrypt(encrypted)) }
            .onFailure { prefs.edit().remove(storageKey).apply() }
            .getOrNull()
    }

    override fun save(experience: GiExperience) {
        require(experience.key.length <= 160)
        require(experience.route.length <= 80)
        prefs.edit().putString(storageKey(experience.key, experience.route), encrypt(encode(experience))).apply()
        trimIfNeeded()
    }

    override fun allFor(key: String): List<GiExperience> {
        val prefix = "$ENTRY_PREFIX${safe(key)}::"
        return prefs.all.mapNotNull { (storedKey, value) ->
            if (!storedKey.startsWith(prefix)) return@mapNotNull null
            val encrypted = value as? String ?: return@mapNotNull null
            runCatching { decode(decrypt(encrypted)) }
                .onFailure { prefs.edit().remove(storedKey).apply() }
                .getOrNull()
        }
    }

    override fun clear() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(ENTRY_PREFIX) }.forEach(editor::remove)
        editor.apply()
    }

    private fun trimIfNeeded() {
        val entries = prefs.all.filterKeys { it.startsWith(ENTRY_PREFIX) }
        if (entries.size <= MAX_EXPERIENCES) return
        val decoded = entries.map { (storedKey, value) ->
            val exp = (value as? String)?.let { runCatching { decode(decrypt(it)) }.getOrNull() }
            storedKey to exp
        }
        decoded.sortedBy { it.second?.lastUsedAt ?: Long.MIN_VALUE }
            .take(entries.size - MAX_EXPERIENCES)
            .forEach { prefs.edit().remove(it.first).apply() }
    }

    private fun storageKey(key: String, route: String) = "$ENTRY_PREFIX${safe(key)}::${safe(route)}"

    private fun safe(value: String): String = Base64.encodeToString(
        value.toByteArray(Charsets.UTF_8),
        Base64.NO_WRAP or Base64.URL_SAFE,
    )

    private fun encode(exp: GiExperience): String {
        val json = JSONObject()
            .put("schemaVersion", 1)
            .put("key", exp.key)
            .put("route", exp.route)
            .put("attempts", exp.attempts)
            .put("successes", exp.successes)
            .put("failures", exp.failures)
            .put("averageOutcome", exp.averageOutcome.toDouble())
            .put("lastOutcome", exp.lastOutcome.toDouble())
            .put("lastUsedAt", exp.lastUsedAt)
        if (exp.actionType != null) json.put("actionType", exp.actionType)
        return json.toString()
    }

    private fun decode(payload: String): GiExperience {
        val json = JSONObject(payload)
        require(json.optInt("schemaVersion", 0) == 1)
        return GiExperience(
            key = json.getString("key"),
            route = json.getString("route"),
            actionType = json.optString("actionType").takeIf { it.isNotBlank() },
            attempts = json.getInt("attempts"),
            successes = json.getInt("successes"),
            failures = json.getInt("failures"),
            averageOutcome = json.getDouble("averageOutcome").toFloat().coerceIn(0f, 1f),
            lastOutcome = json.getDouble("lastOutcome").toFloat().coerceIn(0f, 1f),
            lastUsedAt = json.getLong("lastUsedAt"),
        )
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val body = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        return "$iv.$body"
    }

    private fun decrypt(packed: String): String {
        val parts = packed.split('.', limit = 2)
        require(parts.size == 2)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFS = "ariana_gi_experience_encrypted"
        private const val KEY_ALIAS = "ariana_gi_memory_key_v1"
        private const val ENTRY_PREFIX = "gi_exp::"
        private const val MAX_EXPERIENCES = 2_000
    }
}

object GiRuntimeProvider {
    fun createRuntime(): GiRuntime = GiRuntime(GiBridgePolicyGate)

    fun createExperienceStore(context: Context): GiExperienceStore =
        SynchronizedGiExperienceStore(AndroidEncryptedGiExperienceStore(context))
}
