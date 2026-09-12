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
import de.snowworks.ariana.bridge.SecureAiProviderStore

class AiProviderSettingsActivity : AppCompatActivity() {
    private lateinit var endpointInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var apiKeyInput: EditText
    private lateinit var statusView: TextView
    private var currentKey: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        loadCurrent()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun buildUi(): ScrollView {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0F12"))
            setPadding(dp(20))
        }

        root.addView(label("X-88 KI-PROVIDER", 12f, Color.parseColor("#8A9AA6")))
        root.addView(label("HTTPS-Provider", 28f, Color.parseColor("#E9EEF1")))
        root.addView(
            label(
                "OpenAI-kompatibler Chat-Completions-Endpunkt. Verbindung entsteht erst bei einer Dialoganfrage.",
                14f,
                Color.parseColor("#AAB8C2"),
            ),
        )

        endpointInput = EditText(this).apply {
            hint = "https://anbieter.example/v1/chat/completions"
            setTextColor(Color.parseColor("#E9EEF1"))
            setHintTextColor(Color.parseColor("#687781"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            maxLines = 2
        }
        modelInput = EditText(this).apply {
            hint = "Modell-ID"
            setTextColor(Color.parseColor("#E9EEF1"))
            setHintTextColor(Color.parseColor("#687781"))
            inputType = InputType.TYPE_CLASS_TEXT
            maxLines = 1
        }
        apiKeyInput = EditText(this).apply {
            hint = "API-Key (leer = vorhandenen behalten)"
            setTextColor(Color.parseColor("#E9EEF1"))
            setHintTextColor(Color.parseColor("#687781"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            maxLines = 1
        }
        statusView = label("", 13f, Color.parseColor("#C5D4DC"))

        root.addView(endpointInput)
        root.addView(modelInput)
        root.addView(apiKeyInput)
        root.addView(statusView)

        root.addView(
            MaterialButton(this).apply {
                text = "Speichern und aktivieren"
                setOnClickListener { saveAndActivate() }
            },
        )
        root.addView(
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Provider entfernen"
                setOnClickListener {
                    AiProviderManager.clear(this@AiProviderSettingsActivity)
                    currentKey = ""
                    endpointInput.setText("")
                    modelInput.setText("")
                    apiKeyInput.setText("")
                    toast("KI-Provider entfernt.")
                    refreshStatus()
                }
            },
        )
        root.addView(
            label(
                "Sicherheit: HTTPS ist Pflicht. Localhost, IP-Adressen, .local-Ziele und private Netzwerkadressen werden blockiert. " +
                    "Die komplette Konfiguration wird mit Android Keystore AES/GCM verschlüsselt gespeichert.",
                12f,
                Color.parseColor("#7F919D"),
            ),
        )

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F12"))
            addView(root)
        }
    }

    private fun loadCurrent() {
        val config = SecureAiProviderStore(this).load()
        if (config != null) {
            endpointInput.setText(config.endpoint)
            modelInput.setText(config.model)
            currentKey = config.apiKey
        }
        apiKeyInput.setText("")
        refreshStatus()
    }

    private fun saveAndActivate() {
        val endpoint = endpointInput.text?.toString()?.trim().orEmpty()
        val model = modelInput.text?.toString()?.trim().orEmpty()
        val newKey = apiKeyInput.text?.toString()?.trim().orEmpty()
        val key = if (newKey.isNotEmpty()) newKey else currentKey

        val activated = runCatching {
            AiProviderManager.configure(this, endpoint, model, key)
        }.getOrElse {
            toast("Provider-Konfiguration ungültig: ${it.message ?: "unbekannter Fehler"}")
            false
        }
        if (!activated) {
            toast("Provider konnte nicht registriert werden.")
            refreshStatus()
            return
        }
        currentKey = key
        apiKeyInput.setText("")
        toast("KI-Provider registriert. Netzwerk wird erst beim Dialog genutzt.")
        refreshStatus()
    }

    private fun refreshStatus() {
        val status = AiProviderManager.status(this)
        statusView.text = buildString {
            append("Status: ")
            append(if (status.active) "aktiv" else if (status.configured) "konfiguriert" else "nicht konfiguriert")
            status.endpointHost?.let { append(" · Host: $it") }
            status.model?.let { append(" · Modell: $it") }
            append(" · Schlüssel: ")
            append(if (status.apiKeyPresent) "gespeichert" else "nicht gesetzt")
        }
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
