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

/**
 * Persistent encrypted registry for multiple cloud AI providers.
 *
 * The legacy SecureAiProviderStore still represents the currently selected
 * provider in the UI. This registry keeps provider-specific profiles so Meta
 * and Claude can coexist and be addressed independently by the smart router.
 */
class CloudProviderRegistry(context: Context) {
    enum class Slot { META, CLAUDE }

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun remember(config: SecureAiProviderStore.Config) {
        val slot = classify(config) ?: return
        save(slot, config)
    }

    fun save(slot: Slot, config: SecureAiProviderStore.Config) {
        validate(slot, config)
        val payload = JSONObject()
            .put("endpoint", config.endpoint.trim())
            .put("model", config.model.trim())
            .put("apiKey", config.apiKey.trim())
            .toString()
        prefs.edit().putString(keyFor(slot), encrypt(payload)).apply()
    }

    fun load(slot: Slot): SecureAiProviderStore.Config? {
        val encrypted = prefs.getString(keyFor(slot), null) ?: return null
        return runCatching {
            val json = JSONObject(decrypt(encrypted))
            SecureAiProviderStore.Config(
                endpoint = json.getString("endpoint"),
                model = json.getString("model"),
                apiKey = json.optString("apiKey", ""),
            ).also { validate(slot, it) }
        }.getOrNull()
    }

    fun has(slot: Slot): Boolean = load(slot) != null

    fun clear(slot: Slot) {
        prefs.edit().remove(keyFor(slot)).apply()
    }

    fun migrateActiveIfNeeded() {
        val active = SecureAiProviderStore(app).load() ?: return
        val slot = classify(active) ?: return
        if (!has(slot)) save(slot, active)
    }

    private fun classify(config: SecureAiProviderStore.Config): Slot? {
        val host = runCatching { URI(config.endpoint.trim()).host.orEmpty().lowercase() }.getOrDefault("")
        return when {
            host == ClaudeDialogueProvider.ANTHROPIC_HOST -> Slot.CLAUDE
            host == "api.meta.ai" || host.endsWith(".meta.ai") -> Slot.META
            else -> null
        }
    }

    private fun validate(slot: Slot, config: SecureAiProviderStore.Config) {
        require(config.model.isNotBlank()) { "model required" }
        require(config.apiKey.length <= 512) { "api key length" }
        val uri = URI(config.endpoint.trim())
        require(uri.scheme.equals("https", true)) { "https required" }
        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("host required")
        require(uri.userInfo == null && uri.fragment == null) { "endpoint invalid" }
        when (slot) {
            Slot.CLAUDE -> {
                require(host == ClaudeDialogueProvider.ANTHROPIC_HOST) { "anthropic host required" }
                require(uri.path.trimEnd('/') == "/v1/messages") { "anthropic messages endpoint required" }
            }
            Slot.META -> {
                require(host == "api.meta.ai" || host.endsWith(".meta.ai")) { "meta host required" }
            }
        }
    }

    private fun keyFor(slot: Slot): String = when (slot) {
        Slot.META -> KEY_META
        Slot.CLAUDE -> KEY_CLAUDE
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
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

    private companion object {
        const val PREFS = "x88_cloud_provider_registry"
        const val KEY_META = "meta_v1"
        const val KEY_CLAUDE = "claude_v1"
        const val KEY_ALIAS = "x88_cloud_provider_registry_key_v1"
        const val IV_SIZE = 12
    }
}
