package de.snowworks.ariana.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted, local-first memory store for the X-Ariana core.
 *
 * The vault is stored only in app-private storage. Android Keystore holds the
 * encryption key; the key itself is never written into the vault file.
 * Plaintext export/import is explicit so the user can keep a portable backup.
 */
class MemoryVault(context: Context) {
    data class Record(
        val id: String,
        val kind: String,
        val text: String,
        val tags: List<String> = emptyList(),
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = createdAt,
    )

    private val app = context.applicationContext
    private val directory = File(app.filesDir, DIRECTORY).apply { mkdirs() }
    private val vaultFile = File(directory, VAULT_FILE)

    @Synchronized
    fun list(): List<Record> = readRecords()

    @Synchronized
    fun get(id: String): Record? = readRecords().firstOrNull { it.id == id }

    @Synchronized
    fun upsert(record: Record) {
        validate(record)
        val records = readRecords().toMutableList()
        val index = records.indexOfFirst { it.id == record.id }
        val normalized = if (index >= 0) {
            record.copy(createdAt = records[index].createdAt, updatedAt = System.currentTimeMillis())
        } else {
            record.copy(updatedAt = System.currentTimeMillis())
        }
        if (index >= 0) records[index] = normalized else records += normalized
        require(records.size <= MAX_RECORDS) { "Memory vault record limit reached." }
        writeRecords(records)
    }

    @Synchronized
    fun delete(id: String): Boolean {
        val records = readRecords().toMutableList()
        val removed = records.removeAll { it.id == id }
        if (removed) writeRecords(records)
        return removed
    }

    @Synchronized
    fun clear() {
        writeRecords(emptyList())
    }

    /** Explicit portable plaintext export. Caller decides where to save it. */
    @Synchronized
    fun exportPlaintextJson(): String = encode(readRecords()).toString(2)

    /** Explicit import. Existing records are replaced only after full validation. */
    @Synchronized
    fun importPlaintextJson(json: String) {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_PLAINTEXT_BYTES) { "Memory import is too large." }
        val parsed = decode(JSONObject(json))
        require(parsed.size <= MAX_RECORDS) { "Memory import has too many records." }
        parsed.forEach(::validate)
        writeRecords(parsed)
    }

    private fun readRecords(): List<Record> {
        if (!vaultFile.exists()) return emptyList()
        val encrypted = vaultFile.readBytes()
        require(encrypted.size <= MAX_ENCRYPTED_BYTES) { "Memory vault is unexpectedly large." }
        if (encrypted.isEmpty()) return emptyList()
        val plaintext = decrypt(encrypted)
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Memory vault plaintext is unexpectedly large." }
        return decode(JSONObject(String(plaintext, Charsets.UTF_8)))
    }

    private fun writeRecords(records: List<Record>) {
        val plaintext = encode(records).toString().toByteArray(Charsets.UTF_8)
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Memory vault is too large." }
        val encrypted = encrypt(plaintext)
        val temp = File(directory, "$VAULT_FILE.tmp")
        temp.writeBytes(encrypted)
        if (vaultFile.exists() && !vaultFile.delete()) {
            temp.delete()
            error("Could not replace existing memory vault.")
        }
        if (!temp.renameTo(vaultFile)) {
            temp.delete()
            error("Could not commit memory vault.")
        }
    }

    private fun encode(records: List<Record>): JSONObject = JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("records", JSONArray(records.map { record ->
            JSONObject()
                .put("id", record.id)
                .put("kind", record.kind)
                .put("text", record.text)
                .put("tags", JSONArray(record.tags))
                .put("createdAt", record.createdAt)
                .put("updatedAt", record.updatedAt)
        }))

    private fun decode(root: JSONObject): List<Record> {
        require(root.optInt("schemaVersion", -1) == SCHEMA_VERSION) { "Unsupported memory schema." }
        val array = root.optJSONArray("records") ?: JSONArray()
        val out = ArrayList<Record>(array.length())
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val tagsJson = item.optJSONArray("tags") ?: JSONArray()
            val tags = ArrayList<String>(tagsJson.length())
            for (j in 0 until tagsJson.length()) tags += tagsJson.getString(j)
            out += Record(
                id = item.getString("id"),
                kind = item.getString("kind"),
                text = item.getString("text"),
                tags = tags,
                createdAt = item.getLong("createdAt"),
                updatedAt = item.getLong("updatedAt"),
            )
        }
        return out
    }

    private fun validate(record: Record) {
        require(record.id.matches(ID_REGEX)) { "Invalid memory id." }
        require(record.kind.length in 1..MAX_KIND_CHARS) { "Invalid memory kind." }
        require(record.text.length <= MAX_TEXT_CHARS) { "Memory text is too long." }
        require(record.tags.size <= MAX_TAGS) { "Too many memory tags." }
        require(record.tags.all { it.length in 1..MAX_TAG_CHARS }) { "Invalid memory tag." }
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

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plaintext)
    }

    private fun decrypt(payload: ByteArray): ByteArray {
        require(payload.size > IV_SIZE) { "Invalid memory vault payload." }
        val iv = payload.copyOfRange(0, IV_SIZE)
        val ciphertext = payload.copyOfRange(IV_SIZE, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    companion object {
        private const val DIRECTORY = "x_ariana"
        private const val VAULT_FILE = "memory.vault"
        private const val KEY_ALIAS = "x_ariana_memory_key_v1"
        private const val SCHEMA_VERSION = 1
        private const val IV_SIZE = 12
        private const val MAX_RECORDS = 10_000
        private const val MAX_TEXT_CHARS = 64 * 1024
        private const val MAX_KIND_CHARS = 64
        private const val MAX_TAGS = 32
        private const val MAX_TAG_CHARS = 64
        private const val MAX_PLAINTEXT_BYTES = 8 * 1024 * 1024
        private const val MAX_ENCRYPTED_BYTES = MAX_PLAINTEXT_BYTES + 1024
        private val ID_REGEX = Regex("[A-Za-z0-9._:-]{1,128}")
    }
}
