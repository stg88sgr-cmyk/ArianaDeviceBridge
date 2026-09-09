package de.snowworks.app.ui

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.google.android.material.button.MaterialButton
import de.snowworks.ariana.core.AuditLog
import de.snowworks.ariana.core.MemoryVault
import de.snowworks.ariana.core.PortableMemoryBackup
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

/** Visible, local-only memory management for the X-Ariana vault. */
class MemoryActivity : AppCompatActivity() {
    private lateinit var vault: MemoryVault
    private lateinit var audit: AuditLog
    private lateinit var status: TextView
    private var pendingEncryptedExport: String? = null

    private val encryptedExportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val backup = pendingEncryptedExport
            pendingEncryptedExport = null
            if (uri == null || backup == null) return@registerForActivityResult
            runCatching {
                contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                    writer.write(backup)
                } ?: error("Datei konnte nicht geöffnet werden.")
            }.onSuccess {
                audit.append(AuditLog.Event("memory", "encrypted_export", "EXPORTED", "Password-encrypted portable backup"))
                toast("Verschlüsseltes Memory-Backup gespeichert.")
            }.onFailure { error ->
                toast("Backup konnte nicht gespeichert werden: ${error.message ?: "Unbekannter Fehler"}")
            }
        }

    private val encryptedImportLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val backup = runCatching {
                contentResolver.openInputStream(uri)?.let { stream ->
                    String(readLimited(stream, MAX_ENCRYPTED_IMPORT_BYTES, "Backup-Datei ist größer als 12 MiB."), Charsets.UTF_8)
                } ?: error("Datei konnte nicht geöffnet werden.")
            }.getOrElse { error ->
                toast("Backup konnte nicht gelesen werden: ${error.message ?: "Unbekannter Fehler"}")
                return@registerForActivityResult
            }
            showEncryptedImportPasswordDialog(backup)
        }

    private val plaintextImportLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val json = runCatching {
                contentResolver.openInputStream(uri)?.let { stream ->
                    String(readLimited(stream, MAX_PLAINTEXT_IMPORT_BYTES, "Importdatei ist größer als 8 MiB."), Charsets.UTF_8)
                } ?: error("Datei konnte nicht geöffnet werden.")
            }.getOrElse { error ->
                toast("Import konnte nicht gelesen werden: ${error.message ?: "Unbekannter Fehler"}")
                return@registerForActivityResult
            }
            confirmReplaceMemory(json, "Klartext-Import")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vault = MemoryVault(this)
        audit = AuditLog(this)
        setContentView(buildUi())
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun buildUi(): ScrollView {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0F12"))
            setPadding(dp(20))
        }

        root.addView(text("X-ARIANA", 12f, Color.parseColor("#8A9AA6")))
        root.addView(text("Memory Vault", 28f, Color.parseColor("#E9EEF1")))
        root.addView(text("Lokale Erinnerungen. Im laufenden System per Android Keystore verschlüsselt. Portable Backups bekommen zusätzlich dein eigenes Passwort.", 14f, Color.parseColor("#8A9AA6")))

        status = text("", 15f, Color.parseColor("#C5D4DC"))
        root.addView(status)

        root.addView(MaterialButton(this).apply {
            text = "Erinnerung hinzufügen"
            setOnClickListener { showAddDialog() }
        })
        root.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Erinnerungen ansehen"
            setOnClickListener { showRecords() }
        })
        root.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Erinnerung gezielt löschen"
            setOnClickListener { showDeleteDialog() }
        })

        root.addView(text("Portable Sicherung", 18f, Color.parseColor("#E9EEF1")))
        root.addView(text("Dieses Backup ist für Neuinstallation oder Gerätewechsel gedacht. Das Passwort wird weder gespeichert noch ins Audit geschrieben.", 14f, Color.parseColor("#8A9AA6")))
        root.addView(MaterialButton(this).apply {
            text = "Verschlüsseltes Backup erstellen"
            setOnClickListener { showEncryptedExportPasswordDialog() }
        })
        root.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Verschlüsseltes Backup wiederherstellen"
            setOnClickListener { encryptedImportLauncher.launch(arrayOf("application/json", "text/plain")) }
        })

        root.addView(text("Technischer Klartext-Import", 18f, Color.parseColor("#E9EEF1")))
        root.addView(text("Nur für bewusst erzeugte X-Ariana-JSON-Dateien. Diese Datei ist außerhalb der App nicht verschlüsselt.", 14f, Color.parseColor("#E0B66A")))
        root.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Klartext-Memory importieren"
            setOnClickListener { plaintextImportLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) }
        })

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F12"))
            addView(root)
        }
    }

    private fun showEncryptedExportPasswordDialog() {
        val density = resources.displayMetrics.density
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt())
        }
        val password = passwordInput("Passwort · mindestens 10 Zeichen")
        val confirmation = passwordInput("Passwort wiederholen")
        form.addView(password)
        form.addView(confirmation)

        AlertDialog.Builder(this)
            .setTitle("Portable Sicherung verschlüsseln")
            .setMessage("Ohne dieses Passwort kann das Backup nicht wiederhergestellt werden. Das Passwort wird nicht gespeichert.")
            .setView(form)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Verschlüsseln") { _, _ ->
                val first = password.text.toString().toCharArray()
                val second = confirmation.text.toString().toCharArray()
                try {
                    if (!first.contentEquals(second)) {
                        toast("Die Passwörter stimmen nicht überein.")
                        return@setPositiveButton
                    }
                    val memoryJson = vault.exportPlaintextJson()
                    pendingEncryptedExport = PortableMemoryBackup.encrypt(memoryJson, first)
                    encryptedExportLauncher.launch("x-ariana-memory-backup.xamb.json")
                } catch (error: Exception) {
                    pendingEncryptedExport = null
                    toast("Backup nicht erstellt: ${error.message ?: "Unbekannter Fehler"}")
                } finally {
                    first.fill('\u0000')
                    second.fill('\u0000')
                    password.text?.clear()
                    confirmation.text?.clear()
                }
            }
            .show()
    }

    private fun showEncryptedImportPasswordDialog(backup: String) {
        val password = passwordInput("Backup-Passwort")
        AlertDialog.Builder(this)
            .setTitle("Verschlüsseltes Backup öffnen")
            .setMessage("Das Passwort wird nur für diesen Entschlüsselungsvorgang verwendet.")
            .setView(password)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Entschlüsseln") { _, _ ->
                val chars = password.text.toString().toCharArray()
                try {
                    val plaintext = PortableMemoryBackup.decrypt(backup, chars)
                    confirmReplaceMemory(plaintext, "Verschlüsseltes Backup")
                } catch (error: Exception) {
                    toast(error.message ?: "Backup konnte nicht entschlüsselt werden.")
                } finally {
                    chars.fill('\u0000')
                    password.text?.clear()
                }
            }
            .show()
    }

    private fun confirmReplaceMemory(json: String, source: String) {
        AlertDialog.Builder(this)
            .setTitle("Memory wirklich ersetzen?")
            .setMessage("$source ersetzt den aktuellen lokalen X-Ariana-Memory-Vault. Erstelle vorher bei Bedarf ein verschlüsseltes Backup.")
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Ersetzen") { _, _ ->
                runCatching { vault.importPlaintextJson(json) }
                    .onSuccess {
                        audit.append(AuditLog.Event("memory", "import", "IMPORTED", source))
                        toast("Memory wiederhergestellt.")
                        refresh()
                    }
                    .onFailure { error -> toast("Import abgelehnt: ${error.message ?: "ungültiges Format"}") }
            }
            .show()
    }

    private fun showAddDialog() {
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad)
        }
        val kind = EditText(this).apply {
            hint = "Art, z. B. projekt / regel / person"
            setSingleLine(true)
        }
        val body = EditText(this).apply {
            hint = "Erinnerung"
            minLines = 4
            maxLines = 10
        }
        val tags = EditText(this).apply {
            hint = "Tags, durch Komma getrennt"
            setSingleLine(true)
        }
        form.addView(kind)
        form.addView(body)
        form.addView(tags)

        AlertDialog.Builder(this)
            .setTitle("Lokale Erinnerung hinzufügen")
            .setView(form)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Speichern") { _, _ ->
                val tagList = tags.text.toString().split(',').map(String::trim).filter(String::isNotEmpty).distinct()
                runCatching {
                    vault.upsert(
                        MemoryVault.Record(
                            id = "mem:${UUID.randomUUID()}",
                            kind = kind.text.toString().trim(),
                            text = body.text.toString().trim(),
                            tags = tagList,
                        ),
                    )
                    audit.append(AuditLog.Event("memory", "add", "SAVED", "Explicit local memory edit"))
                }.onSuccess {
                    toast("Lokal gespeichert.")
                    refresh()
                }.onFailure { error -> toast("Nicht gespeichert: ${error.message ?: "ungültige Eingabe"}") }
            }
            .show()
    }

    private fun showRecords() {
        val records = runCatching { vault.list() }.getOrElse {
            toast("Memory konnte nicht gelesen werden.")
            return
        }
        if (records.isEmpty()) {
            toast("Memory Vault ist leer.")
            return
        }
        val message = records.sortedByDescending { it.updatedAt }.take(100).joinToString("\n\n") { record ->
            buildString {
                append("[").append(record.kind).append("] ").append(record.id)
                append("\n").append(record.text.take(500))
                if (record.text.length > 500) append(" …")
                if (record.tags.isNotEmpty()) append("\nTags: ").append(record.tags.joinToString())
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Memory · ${records.size} Einträge")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showDeleteDialog() {
        val records = runCatching { vault.list() }.getOrDefault(emptyList()).sortedByDescending { it.updatedAt }
        if (records.isEmpty()) {
            toast("Memory Vault ist leer.")
            return
        }
        val labels = records.map { "[${it.kind}] ${it.text.take(80)}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Erinnerung löschen")
            .setItems(labels) { _, index -> confirmDelete(records[index]) }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun confirmDelete(record: MemoryVault.Record) {
        AlertDialog.Builder(this)
            .setTitle("Wirklich löschen?")
            .setMessage(record.text.take(500))
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Löschen") { _, _ ->
                if (vault.delete(record.id)) {
                    audit.append(AuditLog.Event("memory", "delete", "DELETED", "Explicit local memory edit"))
                    toast("Erinnerung gelöscht.")
                    refresh()
                }
            }
            .show()
    }

    private fun readLimited(stream: InputStream, limit: Int, tooLargeMessage: String): ByteArray = stream.use { input ->
        val output = ByteArrayOutputStream(minOf(limit, INITIAL_BUFFER_BYTES))
        val buffer = ByteArray(READ_BUFFER_BYTES)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { tooLargeMessage }
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    }

    private fun passwordInput(hintText: String) = EditText(this).apply {
        hint = hintText
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setSingleLine(true)
    }

    private fun refresh() {
        if (!::vault.isInitialized || !::status.isInitialized) return
        val count = runCatching { vault.list().size }.getOrDefault(-1)
        status.text = if (count >= 0) "$count lokale Erinnerungen · verschlüsselt" else "Memory-Status nicht lesbar"
    }

    private fun text(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        setPadding(0, 8, 0, 8)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val MAX_PLAINTEXT_IMPORT_BYTES = 8 * 1024 * 1024
        private const val MAX_ENCRYPTED_IMPORT_BYTES = 12 * 1024 * 1024
        private const val INITIAL_BUFFER_BYTES = 16 * 1024
        private const val READ_BUFFER_BYTES = 8 * 1024
    }
}
