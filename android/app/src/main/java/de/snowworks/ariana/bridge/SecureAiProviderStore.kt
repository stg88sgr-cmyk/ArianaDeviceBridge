package de.snowworks.ariana.bridge

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the whole AI-provider configuration encrypted with Android Keystore AES/GCM. */
class SecureAiProviderStore(context: Context) {
    data class Config(
        val endpoint: String,
        val model: String,
        val apiKey: String,
    )

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): Config? {
        val encrypted = prefs.getString(KEY_CONFIG, null) ?: return null
        return runCatching {
            val json = JSONObject(decrypt(encrypted))
            Config(
                endpoint = json.getString("endpoint"),
                model = json.getString("model"),
                apiKey = json.optString("apiKey", ""),
            ).also(::validate)
        }.getOrNull()
    }

    fun save(config: Config) {
        validate(config)
        val payload = JSONObject()
            .put("endpoint", config.endpoint.trim())
            .put("model", config.model.trim())
            .put("apiKey", config.apiKey.trim())
            .toString()
        prefs.edit().putString(KEY_CONFIG, encrypt(payload)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_CONFIG).apply()
    }

    fun hasConfig(): Boolean = load() != null

    private fun validate(config: Config) {
        require(config.endpoint.length in 12..512) { "endpoint length" }
        require(config.model.length in 1..120) { "model length" }
        require(config.apiKey.length <= 512) { "api key length" }

        val uri = URI(config.endpoint.trim())
        require(uri.scheme.equals("https", ignoreCase = true)) { "https required" }
        val host = uri.host ?: throw IllegalArgumentException("host required")
        require(host.isNotBlank()) { "host required" }
        require(uri.userInfo == null) { "userinfo forbidden" }
        require(uri.fragment == null) { "fragment forbidden" }
        require(!host.equals("localhost", ignoreCase = true)) { "localhost forbidden" }
        require(!host.endsWith(".local", ignoreCase = true)) { "local host forbidden" }
        require(uri.rawQuery == null || uri.rawQuery.length <= 256) { "query too long" }
        require(!IP_LITERAL.matches(host)) { "ip literals forbidden" }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
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

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        require(payload.size > IV_SIZE)
        val iv = payload.copyOfRange(0, IV_SIZE)
        val ciphertext = payload.copyOfRange(IV_SIZE, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    companion object {
        private const val PREFS = "x88_ai_provider_security"
        private const val KEY_CONFIG = "config_v1"
        private const val KEY_ALIAS = "x88_ai_provider_key_v1"
        private const val IV_SIZE = 12
        private val IP_LITERAL = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}$|^[0-9a-fA-F:]+$")
    }
}
