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
import de.snowworks.ariana.bridge.ClaudeDialogueProvider
import de.snowworks.ariana.bridge.CloudProviderRegistry
import de.snowworks.ariana.bridge.DialogueRouter
import de.snowworks.ariana.bridge.HttpsDialogueProvider
import de.snowworks.ariana.bridge.SecureAiProviderStore
import java.util.concurrent.Executors

class AiProviderSettingsActivity : AppCompatActivity() {
    private lateinit var metaEndpoint: EditText
    private lateinit var metaModel: EditText
    private lateinit var metaKey: EditText
    private lateinit var metaStatus: TextView
    private lateinit var metaTest: MaterialButton

    private lateinit var claudeEndpoint: EditText
    private lateinit var claudeModel: EditText
    private lateinit var claudeKey: EditText
    private lateinit var claudeStatus: TextView
    private lateinit var claudeTest: MaterialButton

    private var currentMetaKey: String = ""
    private var currentClaudeKey: String = ""
    private val probeExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ArianaProviderProbe").apply { isDaemon = true }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        loadProfiles()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onDestroy() {
        probeExecutor.shutdownNow()
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

        root.addView(label("X-88 MULTI-KI", 12f, Color.parseColor("#8A9AA6")))
        root.addView(label("Provider-Konsole", 28f, Color.parseColor("#E9EEF1")))
        root.addView(label("Ariana bleibt Identität und Steuerung. Meta und Claude werden getrennt verschlüsselt gespeichert und vom Smart-AI-Router je nach Aufgabe ausgewählt.", 14f, Color.parseColor("#AAB8C2")))

        root.addView(sectionTitle("CLAUDE · CODE / ARCHITEKTUR"))
        claudeEndpoint = endpointInput(ClaudeDialogueProvider.ANTHROPIC_ENDPOINT)
        claudeModel = modelInput(CLAUDE_MODEL)
        claudeKey = keyInput()
        claudeStatus = label("", 13f, Color.parseColor("#C5D4DC"))
        root.addView(claudeEndpoint)
        root.addView(claudeModel)
        root.addView(claudeKey)
        root.addView(claudeStatus)
        root.addView(MaterialButton(this).apply {
            text = "Claude speichern"
            setOnClickListener { saveClaude() }
        })
        claudeTest = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Claude testen"
            setOnClickListener { testClaude() }
        }
        root.addView(claudeTest)
        root.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Claude entfernen"
            setOnClickListener {
                CloudProviderRegistry(this@AiProviderSettingsActivity).clear(CloudProviderRegistry.Slot.CLAUDE)
                currentClaudeKey = ""
                claudeKey.setText("")
                toast("Claude-Profil entfernt.")
                refreshStatus()
            }
        })

        root.addView(sectionTitle("META · GEGENCHECK / ZWEITE MEINUNG"))
        metaEndpoint = endpointInput(META_CHAT_COMPLETIONS_ENDPOINT)
        metaModel = modelInput(META_MODEL)
        metaKey = keyInput()
        metaStatus = label("", 13f, Color.parseColor("#C5D4DC"))
        root.addView(metaEndpoint)
        root.addView(metaModel)
        root.addView(metaKey)
        root.addView(metaStatus)
        root.addView(MaterialButton(this).apply {
            text = "Meta speichern"
            setOnClickListener { saveMeta() }
        })
        metaTest = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Meta testen"
            setOnClickListener { testMeta() }
        }
        root.addView(metaTest)
        root.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Meta entfernen"
            setOnClickListener {
                CloudProviderRegistry(this@AiProviderSettingsActivity).clear(CloudProviderRegistry.Slot.META)
                currentMetaKey = ""
                metaKey.setText("")
                toast("Meta-Profil entfernt.")
                refreshStatus()
            }
        })

        root.addView(label("Sicherheit: Beide Profile liegen getrennt verschlüsselt im Android Keystore. API-Schlüssel werden in der Oberfläche nie wieder angezeigt. CloudAiPolicy kann sensible Inhalte weiterhin lokal festhalten.", 12f, Color.parseColor("#7F919D")))

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F12"))
            addView(root)
        }
    }

    private fun endpointInput(defaultValue: String) = EditText(this).apply {
        hint = "HTTPS-Endpunkt"
        setText(defaultValue)
        setTextColor(Color.parseColor("#E9EEF1"))
        setHintTextColor(Color.parseColor("#687781"))
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        maxLines = 2
    }

    private fun modelInput(defaultValue: String) = EditText(this).apply {
        hint = "Modell-ID"
        setText(defaultValue)
        setTextColor(Color.parseColor("#E9EEF1"))
        setHintTextColor(Color.parseColor("#687781"))
        inputType = InputType.TYPE_CLASS_TEXT
        maxLines = 1
    }

    private fun keyInput() = EditText(this).apply {
        hint = "API-Key (leer = vorhandenen behalten)"
        setTextColor(Color.parseColor("#E9EEF1"))
        setHintTextColor(Color.parseColor("#687781"))
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        maxLines = 1
    }

    private fun loadProfiles() {
        val registry = CloudProviderRegistry(this).also { it.migrateActiveIfNeeded() }
        registry.load(CloudProviderRegistry.Slot.CLAUDE)?.let {
            claudeEndpoint.setText(it.endpoint)
            claudeModel.setText(it.model)
            currentClaudeKey = it.apiKey
        }
        registry.load(CloudProviderRegistry.Slot.META)?.let {
            metaEndpoint.setText(it.endpoint)
            metaModel.setText(it.model)
            currentMetaKey = it.apiKey
        }
        claudeKey.setText("")
        metaKey.setText("")
        refreshStatus()
    }

    private fun saveClaude() {
        val newKey = claudeKey.text?.toString()?.trim().orEmpty()
        val key = if (newKey.isNotEmpty()) newKey else currentClaudeKey
        val config = SecureAiProviderStore.Config(
            endpoint = claudeEndpoint.text?.toString()?.trim().orEmpty(),
            model = claudeModel.text?.toString()?.trim().orEmpty(),
            apiKey = key,
        )
        val saved = runCatching {
            CloudProviderRegistry(this).save(CloudProviderRegistry.Slot.CLAUDE, config)
            true
        }.getOrElse {
            toast("Claude-Konfiguration ungültig: ${it.message ?: "unbekannter Fehler"}")
            false
        }
        if (!saved) return
        currentClaudeKey = key
        claudeKey.setText("")
        toast("Claude-Profil gespeichert.")
        refreshStatus()
    }

    private fun saveMeta() {
        val newKey = metaKey.text?.toString()?.trim().orEmpty()
        val key = if (newKey.isNotEmpty()) newKey else currentMetaKey
        val config = SecureAiProviderStore.Config(
            endpoint = metaEndpoint.text?.toString()?.trim().orEmpty(),
            model = metaModel.text?.toString()?.trim().orEmpty(),
            apiKey = key,
        )
        val saved = runCatching {
            CloudProviderRegistry(this).save(CloudProviderRegistry.Slot.META, config)
            true
        }.getOrElse {
            toast("Meta-Konfiguration ungültig: ${it.message ?: "unbekannter Fehler"}")
            false
        }
        if (!saved) return
        currentMetaKey = key
        metaKey.setText("")
        toast("Meta-Profil gespeichert.")
        refreshStatus()
    }

    private fun testClaude() {
        if (!claudeTest.isEnabled) return
        claudeTest.isEnabled = false
        claudeTest.text = "Claude wird getestet …"
        probeExecutor.submit {
            val result = runCatching {
                val config = CloudProviderRegistry(applicationContext).load(CloudProviderRegistry.Slot.CLAUDE)
                    ?: throw DialogueRouter.ProviderException("PROVIDER_NOT_CONFIGURED")
                if (config.apiKey.isBlank()) throw DialogueRouter.ProviderException("PROVIDER_KEY_MISSING")
                ClaudeDialogueProvider(config).generate("Connectivity smoke test for Ariana X-88. Reply briefly with CLAUDE_OK.")
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                claudeTest.isEnabled = true
                claudeTest.text = "Claude testen"
                result.onSuccess { toast("Claude-Verbindung OK · ${it.take(80)}") }
                    .onFailure { toast("Claude-Test fehlgeschlagen: ${providerError(it)}") }
                refreshStatus()
            }
        }
    }

    private fun testMeta() {
        if (!metaTest.isEnabled) return
        metaTest.isEnabled = false
        metaTest.text = "Meta wird getestet …"
        probeExecutor.submit {
            val result = runCatching {
                val config = CloudProviderRegistry(applicationContext).load(CloudProviderRegistry.Slot.META)
                    ?: throw DialogueRouter.ProviderException("PROVIDER_NOT_CONFIGURED")
                if (config.apiKey.isBlank()) throw DialogueRouter.ProviderException("PROVIDER_KEY_MISSING")
                HttpsDialogueProvider(config).generate("Connectivity smoke test for Ariana X-88. Reply briefly with META_OK.")
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                metaTest.isEnabled = true
                metaTest.text = "Meta testen"
                result.onSuccess { toast("Meta-Verbindung OK · ${it.take(80)}") }
                    .onFailure { toast("Meta-Test fehlgeschlagen: ${providerError(it)}") }
                refreshStatus()
            }
        }
    }

    private fun providerError(error: Throwable): String =
        (error as? DialogueRouter.ProviderException)?.code ?: error.message ?: "PROVIDER_PROBE_FAILED"

    private fun refreshStatus() {
        val registry = CloudProviderRegistry(this).also { it.migrateActiveIfNeeded() }
        val claude = registry.load(CloudProviderRegistry.Slot.CLAUDE)
        val meta = registry.load(CloudProviderRegistry.Slot.META)
        claudeStatus.text = providerStatus("Claude", claude)
        metaStatus.text = providerStatus("Meta", meta)
    }

    private fun providerStatus(name: String, config: SecureAiProviderStore.Config?): String = buildString {
        append(name).append(": ")
        if (config == null) {
            append("nicht konfiguriert")
            return@buildString
        }
        append("bereit")
        append(" · Modell: ").append(config.model)
        append(" · Schlüssel: ").append(if (config.apiKey.isNotBlank()) "gespeichert" else "fehlt")
    }

    private fun sectionTitle(value: String) = label(value, 16f, Color.parseColor("#25D9FF"))

    private fun label(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        setPadding(0, 8, 0, 8)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val CLAUDE_MODEL = "claude-sonnet-5"
        const val META_CHAT_COMPLETIONS_ENDPOINT = "https://api.meta.ai/v1/chat/completions"
        const val META_MODEL = "muse-spark-1.3"
    }
}
