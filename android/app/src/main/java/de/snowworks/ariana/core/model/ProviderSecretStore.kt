package de.snowworks.ariana.core.model

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores provider bearer tokens encrypted with an Android Keystore AES key.
 * Secrets never belong in the repository or provider metadata JSON.
 */
class ProviderSecretStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun put(providerId: String, secret: String) {
        validateId(providerId)
        require(secret.length <= MAX_SECRET_CHARS) { "Secret is too long." }
        if (secret.isBlank()) {
            remove(providerId)
            return
        }
        prefs.edit().putString(keyName(providerId), encrypt(secret)).apply()
    }

    fun get(providerId: String): String? {
        validateId(providerId)
        val encrypted = prefs.getString(keyName(providerId), null) ?: return null
        return runCatching { decrypt(encrypted) }.getOrNull()
    }

    fun has(providerId: String): Boolean = get(providerId) != null

    fun remove(providerId: String) {
        validateId(providerId)
        prefs.edit().remove(keyName(providerId)).apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
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
        require(payload.size > IV_SIZE) { "Invalid encrypted secret." }
        val iv = payload.copyOfRange(0, IV_SIZE)
        val ciphertext = payload.copyOfRange(IV_SIZE, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun validateId(providerId: String) {
        require(providerId.matches(ID_REGEX)) { "Invalid provider id." }
    }

    private fun keyName(providerId: String) = "secret_$providerId"

    companion object {
        private const val PREFS = "x_ariana_provider_secrets"
        private const val KEY_ALIAS = "x_ariana_provider_secrets_key_v1"
        private const val IV_SIZE = 12
        private const val MAX_SECRET_CHARS = 8 * 1024
        private val ID_REGEX = Regex("[A-Za-z0-9._:-]{1,64}")
    }
}
