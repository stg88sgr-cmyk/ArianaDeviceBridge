package de.snowworks.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import com.google.android.material.button.MaterialButton
import de.snowworks.ariana.ArianaDeviceApi
import de.snowworks.ariana.bridge.AiProviderManager
import de.snowworks.ariana.bridge.DialogueRouter
import de.snowworks.ariana.voice.ArianaVoiceController
import java.util.concurrent.Executors

class VoiceTestActivity : AppCompatActivity(), ArianaVoiceController.Listener {

    private lateinit var api: ArianaDeviceApi
    private lateinit var voice: ArianaVoiceController
    private lateinit var statusView: TextView
    private lateinit var providerView: TextView
    private lateinit var transcriptView: TextView
    private lateinit var replyView: TextView
    private val dialogueExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ArianaVoiceDialogue").apply { isDaemon = true }
    }

    private val microphonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                voice.startListening()
            } else {
                onError("Mikrofon-Berechtigung wurde nicht erteilt.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = ArianaDeviceApi(this)
        voice = ArianaVoiceController(this, this)
        runCatching { AiProviderManager.activateConfigured(this) }
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        if (::providerView.isInitialized) refreshProviderStatus()
    }

    override fun onDestroy() {
        dialogueExecutor.shutdownNow()
        voice.shutdown()
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
        root.addView(label("Sprache v2", 30f, Color.parseColor("#E9EEF1")))
        root.addView(
            label(
                "Push-to-talk → Android On-Device STT → Ariana Dialog → Android TTS.",
                14f,
                Color.parseColor("#AAB8C2"),
            ),
        )

        statusView = label(
            if (voice.isOnDeviceRecognitionAvailable()) {
                "Status: lokale Spracherkennung bereit"
            } else {
                "Status: keine lokale Spracherkennung verfügbar"
            },
            14f,
            Color.parseColor("#C5D4DC"),
        )
        providerView = label("", 13f, Color.parseColor("#8FB8C9"))
        transcriptView = label("Du: Noch nichts gesprochen.", 18f, Color.parseColor("#E9EEF1"))
        replyView = label("Ariana: Antwort erscheint hier.", 18f, Color.parseColor("#D8C7F0"))

        root.addView(statusView)
        root.addView(providerView)
        root.addView(transcriptView)
        root.addView(replyView)

        root.addView(
            MaterialButton(this).apply {
                text = "Push-to-talk"
                setOnClickListener { startPushToTalk() }
            },
        )

        root.addView(
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "KI-Provider konfigurieren"
                setOnClickListener {
                    startActivity(Intent(this@VoiceTestActivity, AiProviderSettingsActivity::class.java))
                }
            },
        )

        root.addView(
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Ariana-Stimme testen"
                setOnClickListener {
                    voice.speak("Ariana X-88 Sprachsystem ist bereit.")
                }
            },
        )

        root.addView(
            label(
                "Kein Dauer-Mikrofon. Sprache wird lokal erkannt. Nur der erkannte Text wird bei einer Dialoganfrage an den von dir konfigurierten HTTPS-KI-Provider gesendet.",
                12f,
                Color.parseColor("#7F919D"),
            ),
        )

        refreshProviderStatus()
        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F12"))
            addView(root)
        }
    }

    private fun refreshProviderStatus() {
        val provider = AiProviderManager.status(this)
        providerView.text = buildString {
            append("Dialog: ")
            append(if (provider.active) "bereit" else "kein KI-Provider aktiv")
            provider.endpointHost?.let { append(" · $it") }
            provider.model?.let { append(" · $it") }
        }
    }

    private fun startPushToTalk() {
        if (!api.isMasterEnabled()) {
            toast("Ariana-Gerätezugriff zuerst einschalten.")
            return
        }
        if (api.isBlocked()) {
            toast("Ariana ist nach Alles stoppen blockiert. Gerätezugriff erneut einschalten.")
            return
        }
        if (!voice.isOnDeviceRecognitionAvailable()) {
            onError("Auf diesem Gerät ist keine Android On-Device-Spracherkennung verfügbar.")
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            voice.startListening()
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onState(message: String) {
        runOnUiThread { statusView.text = "Status: $message" }
    }

    override fun onTranscript(text: String) {
        runOnUiThread {
            statusView.text = "Status: erkannt · Ariana denkt …"
            transcriptView.text = "Du: $text"
            replyView.text = "Ariana: …"
        }
        requestDialogue(text)
    }

    private fun requestDialogue(text: String) {
        if (DialogueRouter.providerId() == null) {
            val message = "Kein KI-Provider aktiv. Öffne KI-Provider konfigurieren, damit Ariana auf den erkannten Text antworten kann."
            runOnUiThread {
                statusView.text = "Status: Dialog-Provider fehlt"
                replyView.text = "Ariana: $message"
                refreshProviderStatus()
                voice.speak("Die lokale Sprache funktioniert. Für meine eigene Antwort muss noch ein KI-Provider konfiguriert werden.")
            }
            return
        }

        dialogueExecutor.execute {
            val outcome = DialogueRouter.generate(text)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (outcome.ok && !outcome.reply.isNullOrBlank()) {
                    val reply = outcome.reply.trim()
                    statusView.text = "Status: Antwort bereit"
                    replyView.text = "Ariana: $reply"
                    refreshProviderStatus()
                    voice.speak(reply)
                } else {
                    val message = dialogueErrorMessage(outcome.error)
                    statusView.text = "Status: $message"
                    replyView.text = "Ariana: $message"
                    toast(message)
                }
            }
        }
    }

    private fun dialogueErrorMessage(code: String?): String = when (code) {
        "PROVIDER_UNAVAILABLE" -> "Kein KI-Provider aktiv."
        "PROVIDER_AUTH_FAILED" -> "KI-Provider: Anmeldung fehlgeschlagen."
        "PROVIDER_TIMEOUT", "PROVIDER_REMOTE_TIMEOUT" -> "KI-Provider antwortet nicht rechtzeitig."
        "PROVIDER_RATE_LIMITED" -> "KI-Provider hat gerade ein Nutzungslimit erreicht."
        "PROVIDER_DNS_FAILED", "PROVIDER_NETWORK_FAILED" -> "KI-Provider ist über das Netzwerk nicht erreichbar."
        "PROVIDER_TLS_FAILED" -> "Sichere Verbindung zum KI-Provider fehlgeschlagen."
        "PROVIDER_RESPONSE_INVALID", "PROVIDER_EMPTY_REPLY", "EMPTY_REPLY" -> "KI-Provider hat keine verwertbare Antwort geliefert."
        "PROVIDER_REQUEST_REJECTED" -> "KI-Provider hat die Anfrage abgelehnt."
        else -> "Dialog fehlgeschlagen${code?.let { " ($it)" } ?: "."}"
    }

    override fun onError(message: String) {
        runOnUiThread {
            statusView.text = "Status: $message"
            toast(message)
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
