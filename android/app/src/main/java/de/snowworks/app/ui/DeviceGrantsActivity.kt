package de.snowworks.app.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.setPadding
import com.google.android.material.button.MaterialButton
import de.snowworks.ariana.ArianaDeviceApi
import de.snowworks.ariana.ArianaResult
import de.snowworks.ariana.Feature
import de.snowworks.ariana.bridge.LocalBridgeServer
import de.snowworks.ariana.camera.CameraFrameStore
import de.snowworks.ariana.files.TreePermissionStore
import de.snowworks.ariana.presence.ArianaPresenceController
import de.snowworks.ariana.session.ArianaCaptureService
import de.snowworks.ariana.voice.ArianaVoiceEngine

class DeviceGrantsActivity : AppCompatActivity() {

    private lateinit var api: ArianaDeviceApi
    private lateinit var presence: ArianaPresenceController
    private val avatarDriver = PreviewAvatarDriver()
    private var pendingFeature: Feature? = null
    private val statusViews = mutableMapOf<Feature, TextView>()
    private val switches = mutableMapOf<Feature, SwitchCompat>()
    private var masterSwitch: SwitchCompat? = null
    private var voiceStatus: TextView? = null
    private var lipSyncStatus: TextView? = null
    private var presenceDiagnosticsStatus: TextView? = null

    private val presenceUiHandler = Handler(Looper.getMainLooper())
    private val presenceDiagnosticsTicker = object : Runnable {
        override fun run() {
            if (!::presence.isInitialized || isFinishing || isDestroyed) return
            val d = presence.diagnostics()
            presenceDiagnosticsStatus?.text = buildString {
                append("Diagnose: ${d.compactLabel()}")
                append(" · spoken=${d.utterancesStarted}")
                append(" · ranges=${d.rangeCallbacksObserved}")
            }
            if (d.speaking) {
                lipSyncStatus?.text = when (d.lipSyncMode) {
                    de.snowworks.ariana.presence.PresenceDiagnostics.LipSyncMode.TEXT_TIMING ->
                        "Lip-Sync: Text-Timing aktiv"
                    de.snowworks.ariana.presence.PresenceDiagnostics.LipSyncMode.FALLBACK_PULSE ->
                        "Lip-Sync: Fallback-Puls aktiv"
                    de.snowworks.ariana.presence.PresenceDiagnostics.LipSyncMode.IDLE ->
                        "Lip-Sync: bereit"
                }
            }
            presenceUiHandler.postDelayed(this, PRESENCE_DIAGNOSTICS_REFRESH_MS)
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refresh()
        }

    private val screenLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val feature = pendingFeature
            pendingFeature = null
            if (feature != Feature.SCREEN) return@registerForActivityResult
            if (result.resultCode != RESULT_OK || result.data == null) {
                toast("Systemdialog abgebrochen. Bildschirmübertragung bleibt aus.")
                refresh()
                return@registerForActivityResult
            }
            ArianaCaptureService.startWithProjection(this, result.resultCode, result.data!!)
            refresh()
        }

    private val documentTreeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) {
                toast("Kein Ordner gewählt.")
            } else {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, flags)
                TreePermissionStore(this).save(uri)
                toast("Ordner dauerhaft für diese App freigegeben. Inhalte werden nicht übertragen.")
                api.start(this, Feature.FILES)
            }
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = ArianaDeviceApi(this)
        presence = ArianaPresenceController(
            context = this,
            avatar = avatarDriver,
            voiceListener = object : ArianaVoiceEngine.Listener {
                override fun onReady() {
                    runOnUiThread {
                        val engine = presence.currentVoiceEnginePackage() ?: "unbekannt"
                        voiceStatus?.text = "Stimme: bereit · de-DE · $engine"
                    }
                }

                override fun onSpeakingChanged(speaking: Boolean) {
                    runOnUiThread {
                        val engine = presence.currentVoiceEnginePackage() ?: "unbekannt"
                        voiceStatus?.text = if (speaking) {
                            "Stimme: spricht · $engine"
                        } else {
                            "Stimme: bereit · de-DE · $engine"
                        }
                        if (!speaking) lipSyncStatus?.text = "Lip-Sync: bereit"
                    }
                }

                override fun onUtteranceRange(text: String, start: Int, end: Int) {
                    val fragment = text.substring(start, end)
                        .replace('\n', ' ')
                        .take(24)
                    runOnUiThread {
                        lipSyncStatus?.text = "Lip-Sync: Text-Timing aktiv · $fragment"
                    }
                }

                override fun onError(message: String) {
                    runOnUiThread {
                        voiceStatus?.text = "Stimme: Fehler · $message"
                    }
                }
            },
        )
        LocalBridgeServer.start(this)
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        refresh()
        presenceUiHandler.removeCallbacks(presenceDiagnosticsTicker)
        presenceUiHandler.post(presenceDiagnosticsTicker)
    }

    override fun onPause() {
        presenceUiHandler.removeCallbacks(presenceDiagnosticsTicker)
        super.onPause()
    }

    override fun onDestroy() {
        presenceUiHandler.removeCallbacksAndMessages(null)
        presence.close()
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

        root.addView(text("SNOWWORKS", 12f, Color.parseColor("#8A9AA6")))
        root.addView(text("Gerätefreigaben", 28f, Color.parseColor("#E9EEF1")).apply {
            setPadding(0, dp(4), 0, dp(8))
        })
        root.addView(
            text(
                api.getConnection().detail,
                14f,
                Color.parseColor("#8A9AA6"),
            ),
        )

        val masterRow = row()
        masterRow.addView(
            text("Ariana-Gerätezugriff", 16f, Color.parseColor("#E9EEF1")).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            },
        )
        masterSwitch = SwitchCompat(this).apply {
            isChecked = api.isMasterEnabled()
            setOnCheckedChangeListener { _, on ->
                api.setMasterEnabled(on)
                toast(
                    if (on) "Zugriff ein. Keine Sitzung automatisch gestartet."
                    else "Zugriff aus. Sitzungen beendet.",
                )
                refresh()
            }
        }
        masterRow.addView(masterSwitch)
        root.addView(masterRow)

        root.addView(
            MaterialButton(this).apply {
                text = "Alles stoppen"
                setBackgroundColor(Color.parseColor("#C45C4A"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    api.stopAll()
                    presence.stopSpeaking()
                    toast("Alles gestoppt. Neue Aktionen sind gesperrt.")
                    refresh()
                }
            },
        )

        root.addView(presenceCard(::dp))

        Feature.entries.forEach { feature ->
            root.addView(featureCard(feature, ::dp))
        }

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F12"))
            addView(root)
        }
    }

    private fun presenceCard(dp: (Int) -> Int): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#171325"))
            setPadding(dp(16))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = dp(12)
            layoutParams = lp
        }

        card.addView(text("Ariana Presence", 18f, Color.parseColor("#F1E9FF")))
        card.addView(
            text(
                "Lokaler Sprachtest + renderer-neutraler Lip-Sync Motion Probe.",
                14f,
                Color.parseColor("#B9A8D6"),
            ),
        )

        voiceStatus = text(
            if (presence.isVoiceReady()) "Stimme: bereit · de-DE" else "Stimme: startet …",
            13f,
            Color.parseColor("#D7CCEA"),
        )
        card.addView(voiceStatus)

        lipSyncStatus = text(
            "Lip-Sync: bereit",
            13f,
            Color.parseColor("#B9A8D6"),
        )
        card.addView(lipSyncStatus)

        presenceDiagnosticsStatus = text(
            "Diagnose: startet …",
            12f,
            Color.parseColor("#9E8EBB"),
        )
        card.addView(presenceDiagnosticsStatus)

        val preview = ArianaMotionPreviewView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(240),
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(10)
            }
        }
        card.addView(preview)
        avatarDriver.attach(preview)
        presence.showAvatar()

        val input = EditText(this).apply {
            setText("Hallo. Hier ist Ariana. Meine lokale Stimme und mein Bewegungszustand sind verbunden.")
            setTextColor(Color.parseColor("#F3EFF8"))
            setHintTextColor(Color.parseColor("#80758F"))
            setBackgroundColor(Color.parseColor("#0F0C18"))
            setPadding(dp(12))
            minLines = 2
            maxLines = 5
        }
        card.addView(input)

        card.addView(
            MaterialButton(this).apply {
                text = "Presence-Selbsttest"
                setOnClickListener {
                    input.setText(ArianaPresenceController.SELF_TEST_PHRASE)
                    val accepted = presence.sayPresenceSelfTest()
                    if (!accepted && !presence.isVoiceReady()) {
                        voiceStatus?.text = "Stimme: startet … Selbsttest ist vorgemerkt"
                    }
                }
            },
        )

        val actions = row()
        actions.addView(
            MaterialButton(this).apply {
                text = "Sprechen"
                setOnClickListener {
                    val accepted = presence.say(input.text?.toString().orEmpty())
                    if (!accepted && !presence.isVoiceReady()) {
                        voiceStatus?.text = "Stimme: startet … Text ist vorgemerkt"
                    }
                }
            },
        )
        actions.addView(
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Stop"
                setOnClickListener { presence.stopSpeaking() }
            },
        )
        card.addView(actions)

        return card
    }

    private fun featureCard(feature: Feature, dp: (Int) -> Int): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#13191E"))
            setPadding(dp(16))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = dp(12)
            layoutParams = lp
        }
        card.addView(text(feature.title, 18f, Color.parseColor("#E9EEF1")))
        card.addView(text(feature.purpose, 14f, Color.parseColor("#8A9AA6")))
        val status = text("", 13f, Color.parseColor("#C5D4DC"))
        statusViews[feature] = status
        card.addView(status)

        val actions = row()
        actions.addView(
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Freigeben"
                setOnClickListener { requestGrant(feature) }
            },
        )
        val sw = SwitchCompat(this).apply {
            setOnCheckedChangeListener { view, on ->
                if (!view.isPressed && !view.isHovered) return@setOnCheckedChangeListener
                if (on) enableFeature(feature) else {
                    api.stop(feature)
                    refresh()
                }
            }
        }
        switches[feature] = sw
        actions.addView(sw)
        card.addView(actions)

        if (feature == Feature.CAMERA) {
            card.addView(
                MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = "Live-Testbild anzeigen"
                    setOnClickListener { showCameraTestImage() }
                },
            )
        }
        return card
    }

    private fun requestGrant(feature: Feature) {
        AlertDialog.Builder(this)
            .setTitle(feature.title)
            .setMessage(feature.purpose + "\n\nDie Berechtigung wird erst jetzt angefragt.")
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Weiter") { _, _ ->
                when (feature) {
                    Feature.NOTIFY_READ -> startActivity(api.openNotificationListenerSettings())
                    Feature.FILES -> documentTreeLauncher.launch(null)
                    Feature.SCREEN -> {
                        pendingFeature = Feature.SCREEN
                        screenLauncher.launch(api.createScreenCaptureIntent())
                    }
                    else -> {
                        val perms = api.requiredRuntimePermissions(feature)
                        if (perms.isEmpty()) {
                            toast("Keine Runtime-Berechtigung nötig.")
                        } else {
                            permissionLauncher.launch(perms)
                        }
                    }
                }
            }
            .show()
    }

    private fun enableFeature(feature: Feature) {
        AlertDialog.Builder(this)
            .setTitle("${feature.title} aktivieren")
            .setMessage(feature.purpose)
            .setNegativeButton("Abbrechen") { _, _ -> refresh() }
            .setPositiveButton("Aktivieren") { _, _ ->
                when (val gate = api.canUse(feature)) {
                    is ArianaResult.Err -> {
                        if (gate.error.code == de.snowworks.ariana.ArianaError.Code.DENIED) {
                            requestGrant(feature)
                        } else {
                            toast(gate.error.message)
                        }
                        refresh()
                        return@setPositiveButton
                    }
                    is ArianaResult.Ok -> Unit
                }
                if (feature == Feature.SCREEN) {
                    pendingFeature = Feature.SCREEN
                    screenLauncher.launch(api.createScreenCaptureIntent())
                    return@setPositiveButton
                }
                if (feature == Feature.FILES) {
                    documentTreeLauncher.launch(null)
                    return@setPositiveButton
                }
                if (feature == Feature.NOTIFY_READ && !api.getStatus(feature).permissionGranted) {
                    startActivity(api.openNotificationListenerSettings())
                    refresh()
                    return@setPositiveButton
                }
                when (val r = api.start(this, feature)) {
                    is ArianaResult.Err -> toast(r.error.message)
                    is ArianaResult.Ok -> Unit
                }
                refresh()
            }
            .show()
    }

    private fun refresh() {
        masterSwitch?.isChecked = api.isMasterEnabled()
        Feature.entries.forEach { feature ->
            val status = api.getStatus(feature)
            val cameraFrames = feature == Feature.CAMERA && status.sessionActive && CameraFrameStore.hasFrame()
            statusViews[feature]?.text = buildString {
                append("Status: ${status.permissionLabel}")
                if (status.sessionActive) append(" · Sitzung aktiv")
                if (cameraFrames) append(" · Bilddaten aktiv")
            }
            switches[feature]?.isChecked = status.enabled || status.sessionActive
        }
    }

    private fun showCameraTestImage() {
        val firstFrame = CameraFrameStore.latest()
        if (firstFrame == null) {
            toast("Noch kein Kamerabild da. Kamera einschalten und kurz warten.")
            return
        }

        val image = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(24, 16, 24, 16)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Kamera-Livebild · ${firstFrame.width}×${firstFrame.height}")
            .setView(image)
            .setPositiveButton("OK", null)
            .create()
        val handler = Handler(Looper.getMainLooper())
        var lastCapturedAt = -1L

        val updater = object : Runnable {
            override fun run() {
                if (!dialog.isShowing) return
                val frame = CameraFrameStore.latest()
                if (frame != null && frame.capturedAt != lastCapturedAt) {
                    val bitmap = BitmapFactory.decodeByteArray(frame.jpeg, 0, frame.jpeg.size)
                    if (bitmap != null) {
                        image.setImageBitmap(bitmap)
                        dialog.setTitle("Kamera-Livebild · ${frame.width}×${frame.height}")
                        lastCapturedAt = frame.capturedAt
                    }
                }
                handler.postDelayed(this, TEST_VIEW_REFRESH_MS)
            }
        }

        dialog.setOnShowListener { handler.post(updater) }
        dialog.setOnDismissListener { handler.removeCallbacks(updater) }
        dialog.show()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun text(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        setPadding(0, 8, 0, 8)
    }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    companion object {
        private const val TEST_VIEW_REFRESH_MS = 150L
        private const val PRESENCE_DIAGNOSTICS_REFRESH_MS = 250L
    }
}
