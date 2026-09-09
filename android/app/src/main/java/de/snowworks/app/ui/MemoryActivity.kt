package de.snowworks.app.ui

import android.graphics.Color
import android.os.Bundle
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
import java.util.UUID

/** Visible, local-only memory management for the X-Ariana vault. */
class MemoryActivity : AppCompatActivity() {
    private lateinit var vault: MemoryVault
    private lateinit var audit: AuditLog
    private lateinit var status: TextView

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val json = runCatching {
                contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("Datei konnte nicht geöffnet werden.")
            }.getOrElse { error ->
                toast("Import konnte nicht gelesen werden: ${error.message ?: "Unbekannter Fehler"}")
                return@registerForActivityResult
            }

            AlertDialog.Builder(this)
                .setTitle("Memory wirklich ersetzen?")
                .setMessage("Der Import ersetzt den aktuellen lokalen X-Ariana-Memory-Vault. Lege vorher bei Bedarf einen Export an.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Importieren") { _, _ ->
                    runCatching { vault.importPlaintextJson(json) }
                        .onSuccess {
                            audit.append(AuditLog.Event("memory", "import", "IMPORTED", "Explicit user import"))
                            toast("Memory importiert.")
                            refresh()
                        }
                        .onFailure { error -> toast("Import abgelehnt: ${error.message ?: "ungültiges Format"}") }
                }
                .show()
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
        root.addView(text("Lokale Erinnerungen. Verschlüsselt im App-Speicher. Ein Import oder Löschen passiert nur sichtbar hier.", 14f, Color.parseColor("#8A9AA6")))

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
        root.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "X-Ariana-Memory importieren"
            setOnClickListener { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) }
        })

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F12"))
            addView(root)
        }
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
}
