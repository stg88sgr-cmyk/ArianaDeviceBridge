package de.snowworks.app.ui

import android.Manifest
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
import de.snowworks.ariana.voice.ArianaVoiceController

class VoiceTestActivity : AppCompatActivity(), ArianaVoiceController.Listener {

    private lateinit var api: ArianaDeviceApi
    private lateinit var voice: ArianaVoiceController
    private lateinit var statusView: TextView
    private lateinit var transcriptView: TextView

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
        setContentView(buildUi())
    }

    override fun onDestroy() {
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
        root.addView(label("Sprache v1", 30f, Color.parseColor("#E9EEF1")))
        root.addView(
            label(
                "Lokaler Test: Push-to-talk → Android On-Device STT → Text → Android TTS.",
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
        transcriptView = label("Erkannter Text erscheint hier.", 18f, Color.parseColor("#E9EEF1"))

        root.addView(statusView)
        root.addView(transcriptView)

        root.addView(
            MaterialButton(this).apply {
                text = "Push-to-talk"
                setOnClickListener { startPushToTalk() }
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
                "Kein Dauer-Mikrofon. Die Aufnahme startet nur nach deinem Tipp auf Push-to-talk.",
                12f,
                Color.parseColor("#7F919D"),
            ),
        )

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F12"))
            addView(root)
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
            statusView.text = "Status: erkannt"
            transcriptView.text = text
        }
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
