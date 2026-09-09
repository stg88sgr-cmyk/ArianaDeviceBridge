package de.snowworks.ariana.core

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-encrypted portable backup for the X-Ariana memory JSON.
 *
 * Unlike MemoryVault's Android-Keystore encryption, this format is deliberately
 * portable across uninstall/reinstall and devices. The password is never stored.
 */
object PortableMemoryBackup {
    fun encrypt(memoryJson: String, password: CharArray): String {
        require(password.size >= MIN_PASSWORD_CHARS) { "Passwort muss mindestens 10 Zeichen haben." }
        require(memoryJson.toByteArray(Charsets.UTF_8).size <= MAX_PLAINTEXT_BYTES) { "Memory-Backup ist zu groß." }

        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val keyBytes = deriveKey(password, salt, PBKDF2_ITERATIONS)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            val ciphertext = cipher.doFinal(memoryJson.toByteArray(Charsets.UTF_8))

            JSONObject()
                .put("format", FORMAT)
                .put("version", VERSION)
                .put("kdf", "PBKDF2WithHmacSHA256")
                .put("iterations", PBKDF2_ITERATIONS)
                .put("salt", b64(salt))
                .put("cipher", "AES-256-GCM")
                .put("iv", b64(iv))
                .put("ciphertext", b64(ciphertext))
                .toString()
        } finally {
            keyBytes.fill(0)
        }
    }

    fun decrypt(backupJson: String, password: CharArray): String {
        require(password.isNotEmpty()) { "Passwort fehlt." }
        require(backupJson.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "Backup-Datei ist zu groß." }

        val root = JSONObject(backupJson)
        require(root.optString("format") == FORMAT) { "Unbekanntes Backup-Format." }
        require(root.optInt("version", -1) == VERSION) { "Nicht unterstützte Backup-Version." }
        require(root.optString("kdf") == "PBKDF2WithHmacSHA256") { "Nicht unterstütztes KDF." }
        require(root.optString("cipher") == "AES-256-GCM") { "Nicht unterstützte Verschlüsselung." }

        val iterations = root.getInt("iterations")
        require(iterations in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS) { "Ungültige KDF-Iterationen." }
        val salt = decodeB64(root.getString("salt"), SALT_BYTES, "Salt")
        val iv = decodeB64(root.getString("iv"), IV_BYTES, "IV")
        val ciphertext = Base64.decode(root.getString("ciphertext"), Base64.NO_WRAP)
        require(ciphertext.size in 17..MAX_CIPHERTEXT_BYTES) { "Ungültige Ciphertext-Größe." }

        val keyBytes = deriveKey(password, salt, iterations)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            val plaintext = cipher.doFinal(ciphertext)
            require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Entschlüsselter Memory ist zu groß." }
            String(plaintext, Charsets.UTF_8)
        } catch (error: Exception) {
            throw IllegalArgumentException("Backup konnte nicht entschlüsselt werden. Passwort oder Datei stimmt nicht.", error)
        } finally {
            keyBytes.fill(0)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun decodeB64(value: String, expectedBytes: Int, label: String): ByteArray {
        val decoded = Base64.decode(value, Base64.NO_WRAP)
        require(decoded.size == expectedBytes) { "$label hat eine ungültige Länge." }
        return decoded
    }

    private fun b64(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)

    private const val FORMAT = "x-ariana-memory-backup"
    private const val VERSION = 1
    private const val MIN_PASSWORD_CHARS = 10
    private const val PBKDF2_ITERATIONS = 310_000
    private const val MIN_ACCEPTED_ITERATIONS = 100_000
    private const val MAX_ACCEPTED_ITERATIONS = 2_000_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val MAX_PLAINTEXT_BYTES = 8 * 1024 * 1024
    private const val MAX_CIPHERTEXT_BYTES = MAX_PLAINTEXT_BYTES + 32
    private const val MAX_BACKUP_BYTES = 12 * 1024 * 1024
}
