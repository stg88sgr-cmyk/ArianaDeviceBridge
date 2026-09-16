package de.snowworks.app.ui

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.google.android.material.button.MaterialButton
import de.snowworks.ariana.bridge.AiProviderManager
import de.snowworks.ariana.bridge.ClaudeDialogueProvider
import de.snowworks.ariana.bridge.SecureAiProviderStore
import java.util.concurrent.Executors

class AiProviderSettingsActivity : AppCompatActivity() {
    private lateinit var endpointInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var apiKeyInput: EditText
    private lateinit var statusView: TextView
    private lateinit var testButton: MaterialButton
    private var currentKey: String = ""
    private val probeExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "ArianaProviderProbe").apply { isDaemon = true } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        loadCurrent()
    }

    override fun onResume() { super.onResume(); refreshStatus() }
    override fun onDestroy() { probeExecutor.shutdownNow(); super.onDestroy() }

    private fun buildUi(): ScrollView {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0F12"))
            setPadding(dp(20))
        }
        root.addView(label("X-88 KI-PROVIDER", 12f, Color.parseColor("#8A9AA6")))
        root.addView(label("Cloud-KI-Provider", 28f, Color.parseColor("#E9EEF1")))
        root.addView(label("Ariana bleibt Identität und Steuerung. Die Bridge kann im Hintergrund einen OpenAI-kompatiblen Provider oder Claude über die native Anthropic Messages API nutzen.", 14f, Color.parseColor("#AAB8C2")))

        endpointInput = EditText(this).apply {
            hint = "HTTPS-Endpunkt"
            setTextColor(Color.parseColor("#E9EEF1")); setHintTextColor(Color.parseColor("#687781"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI; maxLines = 2
        }
        modelInput = EditText(this).apply {
            hint = "Modell-ID"; setTextColor(Color.parseColor("#E9EEF1")); setHintTextColor(Color.parseColor("#687781")); inputType = InputType.TYPE_CLASS_TEXT; maxLines = 1
        }
        apiKeyInput = EditText(this).apply {
            hint = "API-Key (leer = vorhandenen behalten)"; setTextColor(Color.parseColor("#E9EEF1")); setHintTextColor(Color.parseColor("#687781")); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; maxLines = 1
        }
        statusView = label("", 13f, Color.parseColor("#C5D4DC"))
        root.addView(endpointInput); root.addView(modelInput); root.addView(apiKeyInput); root.addView(statusView)

        root.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Claude Sonnet vorbereiten"; setOnClickListener { applyClaudePreset() }
        })
        root.addView(label("Claude-Preset: Anthropic Messages API. Der API-Key wird nicht vorbelegt und erst beim Speichern verschlüsselt abgelegt.", 12f, Color.parseColor("#8FA4AF")))

        root.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Meta Muse Spark 1.3 vorbereiten"; setOnClickListener { applyMetaPreset() }
        })
        root.addView(label("Meta-Preset: API-Key wird nicht vorbelegt und erst beim Speichern verschlüsselt abgelegt.", 12f, Color.parseColor("#8FA4AF")))
        root.addView(MaterialButton(this).apply { text = "Speichern und aktivieren"; setOnClickListener { saveAndActivate() } })
        testButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Gespeicherte KI-Verbindung testen"; setOnClickListener { testConfiguredProvider() }
        }
        root.addView(testButton)
        root.addView(label("Der Test nutzt nur die verschlüsselt gespeicherte Konfiguration. Er speichert die Testantwort nicht.", 12f, Color.parseColor("#8FA4AF")))
        root.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Letzten Provider-Stand wiederherstellen"; setOnClickListener { restorePrevious() }
        })
        root.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Provider entfernen"
            setOnClickListener {
                AiProviderManager.clear(this@AiProviderSettingsActivity)
                currentKey = ""; endpointInput.setText(""); modelInput.setText(""); apiKeyInput.setText("")
                toast("KI-Provider entfernt. Der vorherige Stand bleibt als Recovery-Snapshot erhalten.")
                refreshStatus()
            }
        })
        root.addView(label("Sicherheit: HTTPS ist Pflicht. Localhost, IP-Adressen, .local-Ziele und private Netzwerkadressen werden blockiert. Claude wird zusätzlich fest auf api.anthropic.com/v1/messages begrenzt. Konfiguration und Recovery-Stand werden mit Android Keystore AES/GCM verschlüsselt gespeichert.", 12f, Color.parseColor("#7F919D")))
        return ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0B0F12")); addView(root) }
    }

    private fun applyClaudePreset() {
        endpointInput.setText(ClaudeDialogueProvider.ANTHROPIC_ENDPOINT)
        modelInput.setText(CLAUDE_MODEL)
        toast("Claude vorbereitet. Jetzt Anthropic API-Key eintragen und speichern.")
    }

    private fun applyMetaPreset() {
        endpointInput.setText(META_CHAT_COMPLETIONS_ENDPOINT)
        modelInput.setText(META_MODEL)
        toast("Meta Muse Spark 1.3 vorbereitet. Jetzt nur noch den Meta Model API-Key eintragen und speichern.")
    }

    private fun loadCurrent() {
        val config = SecureAiProviderStore(this).load()
        if (config != null) { endpointInput.setText(config.endpoint); modelInput.setText(config.model); currentKey = config.apiKey }
        else { endpointInput.setText(""); modelInput.setText(""); currentKey = "" }
        apiKeyInput.setText(""); refreshStatus()
    }

    private fun saveAndActivate() {
        val endpoint = endpointInput.text?.toString()?.trim().orEmpty()
        val model = modelInput.text?.toString()?.trim().orEmpty()
        val newKey = apiKeyInput.text?.toString()?.trim().orEmpty()
        val key = if (newKey.isNotEmpty()) newKey else currentKey
        val activated = runCatching { AiProviderManager.configure(this, endpoint, model, key) }.getOrElse {
            toast("Provider-Konfiguration ungültig: ${it.message ?: "unbekannter Fehler"}"); false
        }
        if (!activated) { toast("Provider konnte nicht registriert werden."); refreshStatus(); return }
        currentKey = key; apiKeyInput.setText("")
        toast("KI-Provider registriert. Netzwerk wird erst beim Dialog oder Verbindungstest genutzt.")
        refreshStatus()
    }

    private fun testConfiguredProvider() {
        if (!::testButton.isInitialized || !testButton.isEnabled) return
        testButton.isEnabled = false; testButton.text = "KI-Verbindung wird getestet …"
        probeExecutor.submit {
            val result = AiProviderManager.testConfigured(applicationContext)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                testButton.isEnabled = true; testButton.text = "Gespeicherte KI-Verbindung testen"
                if (result.ok) toast("KI-Verbindung OK · ${result.model ?: "Modell"} · ${result.replyPreview?.takeIf { it.isNotBlank() } ?: "Antwort erhalten"}")
                else toast("KI-Test fehlgeschlagen: ${result.error ?: "PROVIDER_PROBE_FAILED"}")
                refreshStatus()
            }
        }
    }

    private fun restorePrevious() {
        val restored = runCatching { AiProviderManager.restorePrevious(this) }.getOrDefault(false)
        if (!restored) { toast("Kein nutzbarer Recovery-Snapshot vorhanden."); refreshStatus(); return }
        loadCurrent(); toast("Letzten Provider-Stand wiederhergestellt.")
    }

    private fun refreshStatus() {
        val status = AiProviderManager.status(this)
        statusView.text = buildString {
            append("Status: ").append(if (status.active) "aktiv" else if (status.configured) "konfiguriert" else "nicht konfiguriert")
            status.endpointHost?.let { append(" · Host: $it") }
            status.model?.let { append(" · Modell: $it") }
            append(" · Schlüssel: ").append(if (status.apiKeyPresent) "gespeichert" else "nicht gesetzt")
            append(" · Recovery: ").append(if (status.recoveryAvailable) "vorhanden" else "leer")
        }
    }

    private fun label(value: String, size: Float, color: Int) = TextView(this).apply { text = value; textSize = size; setTextColor(color); setPadding(0, 8, 0, 8) }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }

    private companion object {
        const val CLAUDE_MODEL = "claude-sonnet-5"
        const val META_CHAT_COMPLETIONS_ENDPOINT = "https://api.meta.ai/v1/chat/completions"
        const val META_MODEL = "muse-spark-1.3"
    }
}
