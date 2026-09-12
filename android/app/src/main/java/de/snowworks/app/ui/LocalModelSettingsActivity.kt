package de.snowworks.app.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.google.android.material.button.MaterialButton
import de.snowworks.ariana.bridge.LocalAiProviderManager
import de.snowworks.ariana.bridge.LocalMemoryStore
import java.util.concurrent.Executors

class LocalModelSettingsActivity : AppCompatActivity() {
    private lateinit var statusView: TextView
    private lateinit var memoryView: TextView
    private lateinit var actionButton: MaterialButton
    private val ioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ArianaLocalModelImport").apply { isDaemon = true }
    }

    private val modelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importModel(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::statusView.isInitialized) refreshStatus()
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun buildUi(): ScrollView {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0F12"))
            setPadding(dp(20))
        }

        root.addView(label("ARIANA X-88", 12f, Color.parseColor("#8A9AA6")))
        root.addView(label("Ariana Local v2", 30f, Color.parseColor("#E9EEF1")))
        root.addView(
            label(
                "Lokales Sprachmodell auf dem Telefon. Nach dem Import braucht der Dialog keinen API-Key und kein Guthaben.",
                14f,
                Color.parseColor("#AAB8C2"),
            ),
        )

        statusView = label("Status wird geladen …", 14f, Color.parseColor("#C5D4DC"))
        root.addView(statusView)

        memoryView = label("Lokales Gedächtnis wird geladen …", 13f, Color.parseColor("#B9A7D8"))
        root.addView(memoryView)

        root.addView(
            MaterialButton(this).apply {
                text = "Modell auswählen (.task)"
                setOnClickListener { modelPicker.launch(arrayOf("*/*")) }
            },
        )

        actionButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Lokales Modell aktivieren"
            setOnClickListener {
                val current = LocalAiProviderManager.status(this@LocalModelSettingsActivity)
                val next = LocalAiProviderManager.setEnabled(this@LocalModelSettingsActivity, !current.enabled)
                toast(if (next.enabled) "Ariana Local aktiviert." else "Ariana Local deaktiviert.")
                refreshStatus()
            }
        }
        root.addView(actionButton)

        root.addView(
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Lokales Gedächtnis löschen"
                setOnClickListener { confirmMemoryClear() }
            },
        )

        root.addView(
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Empfohlenes Gemma-Modell öffnen"
                setOnClickListener {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://huggingface.co/litert-community/Gemma3-1B-IT/tree/main"),
                    )
                    startActivity(intent)
                }
            },
        )

        root.addView(
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Lokales Modell entfernen"
                setOnClickListener {
                    LocalAiProviderManager.clear(this@LocalModelSettingsActivity)
                    toast("Lokales Modell entfernt.")
                    refreshStatus()
                }
            },
        )

        root.addView(
            label(
                "Memory v2 ordnet ausdrücklich gespeicherte Fakten lokal in Person, Vorlieben, Projekte, Geräte und Entscheidungen. Bestehende Memory-v1-Fakten werden übernommen. Der komplette Gesprächsverlauf wird nicht dauerhaft gespeichert.",
                12f,
                Color.parseColor("#8E7FA5"),
            ),
        )

        root.addView(
            label(
                "Empfehlung für den S23: Gemma 3 1B IT als Q4-MediaPipe-.task. Die Modelldatei ist groß und wird einmalig in den privaten App-Speicher kopiert. Die erste Antwort kann beim Laden deutlich länger dauern.",
                12f,
                Color.parseColor("#7F919D"),
            ),
        )

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F12"))
            addView(root)
        }
    }

    private fun confirmMemoryClear() {
        val memory = LocalMemoryStore(this).snapshot()
        if (memory.itemCount == 0) {
            toast("Das lokale Gedächtnis ist bereits leer.")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Lokales Gedächtnis löschen?")
            .setMessage("Name und alle strukturierten Memory-v2-Fakten werden dauerhaft entfernt. Das Sprachmodell bleibt installiert.")
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Löschen") { _, _ ->
                LocalMemoryStore(this).clear()
                LocalAiProviderManager.closeRuntime()
                LocalAiProviderManager.activateConfigured(this)
                toast("Lokales Gedächtnis gelöscht.")
                refreshStatus()
            }
            .show()
    }

    private fun importModel(uri: Uri) {
        statusView.text = "Status: Modell wird in den privaten Ariana-Speicher kopiert …"
        ioExecutor.execute {
            val result = runCatching { LocalAiProviderManager.importAndActivate(this, uri) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess {
                    toast("Lokales Modell importiert und aktiviert.")
                    refreshStatus()
                }.onFailure {
                    statusView.text = "Status: Import fehlgeschlagen"
                    toast("Modellimport fehlgeschlagen: ${it.message ?: "unbekannter Fehler"}")
                }
            }
        }
    }

    private fun refreshStatus() {
        val status = LocalAiProviderManager.status(this)
        statusView.text = buildString {
            append("Status: ")
            append(if (status.active) "lokaler Dialog aktiv" else if (status.enabled) "lokales Modell aktiviert" else if (status.installed) "Modell installiert, aber aus" else "noch kein lokales Modell")
            if (status.installed) {
                append("\nGröße: ")
                append(String.format("%.0f MB", status.sizeBytes / 1024.0 / 1024.0))
            }
            status.displayName?.let { append("\nDatei: $it") }
        }

        val memory = LocalMemoryStore(this).snapshot()
        memoryView.text = buildString {
            append("Memory v2: ")
            append(memory.itemCount)
            append(if (memory.itemCount == 1) " lokaler Fakt" else " lokale Fakten")
            if (!memory.userName.isNullOrBlank()) append(" · Name gespeichert")
            append("\nPerson: ${memory.person.size}")
            append(" · Vorlieben: ${memory.preferences.size}")
            append(" · Projekte: ${memory.projects.size}")
            append(" · Geräte: ${memory.devices.size}")
            append(" · Entscheidungen: ${memory.decisions.size}")
        }

        actionButton.isEnabled = status.installed
        actionButton.text = if (status.enabled) "Lokales Modell deaktivieren" else "Lokales Modell aktivieren"
    }

    private fun label(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        setPadding(0, 8, 0, 8)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
