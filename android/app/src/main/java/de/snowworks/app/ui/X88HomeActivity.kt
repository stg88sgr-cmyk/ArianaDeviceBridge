package de.snowworks.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import com.google.android.material.button.MaterialButton
import de.snowworks.app.BuildConfig
import de.snowworks.app.widget.X88WidgetProvider
import de.snowworks.ariana.ArianaDeviceApi
import de.snowworks.ariana.ArianaResult
import de.snowworks.ariana.Feature
import de.snowworks.ariana.bridge.ActionApprovalStore
import de.snowworks.ariana.bridge.ActionPolicy
import de.snowworks.ariana.bridge.AiProviderManager
import de.snowworks.ariana.bridge.DialogueRouter
import de.snowworks.ariana.bridge.LocalAiProviderManager
import de.snowworks.ariana.bridge.LocalDeviceActionExecutor
import de.snowworks.ariana.files.TreePermissionStore
import de.snowworks.ariana.notify.NotificationStore
import de.snowworks.ariana.presence.PresenceSignalController
import de.snowworks.ariana.voice.ArianaVoiceController
import java.util.concurrent.Executors

class X88HomeActivity : AppCompatActivity(), ArianaVoiceController.Listener {
    private lateinit var api: ArianaDeviceApi
    private lateinit var voice: ArianaVoiceController
    private lateinit var avatar: X88AvatarView
    private lateinit var masterButton: MaterialButton
    private lateinit var cameraButton: MaterialButton
    private lateinit var microphoneButton: MaterialButton
    private lateinit var screenButton: MaterialButton
    private lateinit var statusView: TextView
    private lateinit var transcriptView: TextView
    private lateinit var replyView: TextView
    private lateinit var modulesView: TextView
    private var pendingPermissionFeature: Feature? = null

    private val dialogueExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ArianaX88HomeDialogue").apply { isDaemon = true }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val feature = pendingPermissionFeature ?: return@registerForActivityResult
            pendingPermissionFeature = null
            val granted = api.requiredRuntimePermissions(feature).all { permission ->
                result[permission] == true ||
                    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            }
            if (granted) startFeatureNow(feature)
            else showReply("${feature.title} wurde nicht freigegeben.", true)
        }

    private val pushToTalkPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) voice.startListening()
            else onError("Mikrofon-Berechtigung wurde nicht erteilt.")
        }

    private val screenProjectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                data.putExtra("resultCode", result.resultCode)
                val started = api.start(this, Feature.SCREEN, data)
                if (started is ArianaResult.Ok) X88EventJournal.add("feature_start", Feature.SCREEN.id)
                handleResult(started, "Bildschirmübertragung gestartet.")
            } else {
                showReply("Bildschirmübertragung wurde nicht gestartet.", false)
                refreshStatus()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = ArianaDeviceApi(this)

        // Build the visible UI before voice/TTS initialization. Some Android voice
        // services can fail immediately during construction and report an error callback.
        // The UI therefore exists before any such callback can be rendered.
        setContentView(buildUi())
        X88EventJournal.add("home_open")

        voice = ArianaVoiceController(this, this)
        if (!runCatching { LocalAiProviderManager.activateConfigured(this) }.getOrDefault(false)) {
            runCatching { AiProviderManager.activateConfigured(this) }
        }
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::statusView.isInitialized && ::api.isInitialized) refreshStatus()
    }

    override fun onDestroy() {
        dialogueExecutor.shutdownNow()
        if (::voice.isInitialized) voice.shutdown()
        super.onDestroy()
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#05070D"))
            setPadding(dp(18))
        }
        root.addView(label("ARIANA X-88", 12f, "#7E93A6"))
        root.addView(label("LOCAL DEVICE CORE", 28f, "#EAF7FF"))
        root.addView(label("Build ${BuildConfig.VERSION_NAME} · ${BuildConfig.APPLICATION_ID}", 11f, "#6F8799"))

        avatar = X88AvatarView(this)
        root.addView(avatar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(310)))

        statusView = label("Status wird gelesen …", 13f, "#25D9FF")
        transcriptView = label("Du: Noch nichts gesprochen.", 15f, "#EAF7FF")
        replyView = label("Ariana: X-88 bereit.", 15f, "#E8D9F2")
        modulesView = label("MODULES · loading …", 11f, "#7896A6")
        root.addView(statusView)
        root.addView(transcriptView)
        root.addView(replyView)
        root.addView(modulesView)
        root.addView(spacer(8))

        masterButton = button("MASTER") { toggleMaster() }
        cameraButton = button("CAMERA") { toggleFeature(Feature.CAMERA) }
        microphoneButton = button("MIC") { toggleFeature(Feature.MICROPHONE) }
        screenButton = button("SCREEN") { toggleFeature(Feature.SCREEN) }

        root.addView(masterButton)
        root.addView(button("TALK · Push-to-talk") { startPushToTalk() })
        root.addView(row(cameraButton, microphoneButton))
        root.addView(row(screenButton, button("GRANTS") {
            startActivity(Intent(this, DeviceGrantsActivity::class.java))
        }))
        root.addView(row(
            button("AI SETTINGS") {
                X88EventJournal.add("open_ai_provider_settings")
                startActivity(Intent(this, AiProviderSettingsActivity::class.java))
            },
            button("HEALTH") {
                X88EventJournal.add("open_health")
                startActivity(Intent(this, X88HealthActivity::class.java))
            },
        ))
        root.addView(button("AUDIT") {
            X88EventJournal.add("open_audit")
            startActivity(Intent(this, X88AuditActivity::class.java))
        })
        root.addView(button("STOP ALL") {
            api.stopAll()
            X88EventJournal.add("stop_all", "button")
            avatar.mode = X88AvatarView.Mode.STOPPED
            showReply("Alles gestoppt. Gerätezugriff ist jetzt blockiert.", false)
            refreshStatus()
        }.apply {
            setBackgroundColor(Color.parseColor("#341018"))
            setTextColor(Color.parseColor("#FF8CA7"))
        })
        root.addView(label("Voice: Kamera an/aus · Mikrofon an/aus · Bildschirm teilen/stoppen · Gerätestatus · Alles stoppen", 11f, "#788D9C"))

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#05070D"))
            addView(root)
        }
    }

    private fun toggleMaster() {
        if (api.isMasterEnabled() && !api.isBlocked()) {
            api.setMasterEnabled(false)
            X88EventJournal.add("master_off", "button")
            showReply("Gerätezugriff ausgeschaltet.", false)
            refreshStatus()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("ARIANA X-88 Gerätezugriff einschalten?")
            .setMessage("Der lokale Master-Schalter wird aktiviert. Android-Berechtigungen werden dadurch nicht automatisch erteilt.")
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Einschalten") { _, _ ->
                api.setMasterEnabled(true)
                X88EventJournal.add("master_on", "confirmed")
                avatar.mode = X88AvatarView.Mode.IDLE
                showReply("Lokaler Gerätezugriff aktiviert.", true)
                refreshStatus()
            }
            .show()
    }

    private fun toggleFeature(feature: Feature) {
        if (api.getStatus(feature).sessionActive) {
            api.stop(feature)
            X88EventJournal.add("feature_stop", feature.id)
            showReply("${feature.title} gestoppt.", false)
            refreshStatus()
        } else {
            requestStartFeature(feature)
        }
    }

    private fun requestStartFeature(feature: Feature) {
        if (!api.isMasterEnabled() || api.isBlocked()) {
            showReply("Schalte zuerst den X-88 Gerätezugriff ein.", true)
            return
        }
        AlertDialog.Builder(this)
            .setTitle("${feature.title} starten?")
            .setMessage(feature.purpose)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Starten") { _, _ ->
                if (feature == Feature.SCREEN) screenProjectionLauncher.launch(api.createScreenCaptureIntent())
                else ensurePermissionsThenStart(feature)
            }
            .show()
    }

    private fun ensurePermissionsThenStart(feature: Feature) {
        val missing = api.requiredRuntimePermissions(feature).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startFeatureNow(feature)
        else {
            pendingPermissionFeature = feature
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startFeatureNow(feature: Feature) {
        val result = api.start(this, feature)
        if (result is ArianaResult.Ok) X88EventJournal.add("feature_start", feature.id)
        handleResult(result, "${feature.title} gestartet.")
    }

    private fun stopFeature(feature: Feature) {
        val result = api.stop(feature)
        if (result is ArianaResult.Ok) X88EventJournal.add("feature_stop", feature.id)
        handleResult(result, "${feature.title} gestoppt.")
    }

    private fun handleResult(result: ArianaResult<Unit>, success: String) {
        when (result) {
            is ArianaResult.Ok -> showReply(success, false)
            is ArianaResult.Err -> showReply(result.error.message, true)
        }
        refreshStatus()
    }

    private fun startPushToTalk() {
        if (!api.isMasterEnabled() || api.isBlocked()) {
            showReply("Schalte zuerst den X-88 Gerätezugriff ein.", true)
            return
        }
        if (!::voice.isInitialized || !voice.isOnDeviceRecognitionAvailable()) {
            onError("Auf diesem Gerät ist keine Android On-Device-Spracherkennung verfügbar.")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voice.startListening()
        } else {
            pushToTalkPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onState(message: String) {
        runOnUiThread {
            if (::statusView.isInitialized) statusView.text = "VOICE · $message"
            if (::avatar.isInitialized) {
                avatar.mode = when {
                    message.contains("höre", true) || message.contains("sprich", true) -> X88AvatarView.Mode.LISTENING
                    message.contains("verarbeite", true) -> X88AvatarView.Mode.THINKING
                    else -> avatar.mode
                }
            }
        }
    }

    override fun onTranscript(text: String) {
        runOnUiThread {
            if (::transcriptView.isInitialized) transcriptView.text = "Du: $text"
            if (::replyView.isInitialized) replyView.text = "Ariana: …"
            if (::avatar.isInitialized) avatar.mode = X88AvatarView.Mode.THINKING
        }
        val intent = X88VoiceIntentRouter.classify(text)
        X88EventJournal.add("voice_intent", intent.name.lowercase())
        when (intent) {
            X88VoiceIntentRouter.Intent.CAMERA_ON -> runOnUiThread { requestStartFeature(Feature.CAMERA) }
            X88VoiceIntentRouter.Intent.CAMERA_OFF -> runOnUiThread { stopFeature(Feature.CAMERA) }
            X88VoiceIntentRouter.Intent.MICROPHONE_ON -> runOnUiThread { requestStartFeature(Feature.MICROPHONE) }
            X88VoiceIntentRouter.Intent.MICROPHONE_OFF -> runOnUiThread { stopFeature(Feature.MICROPHONE) }
            X88VoiceIntentRouter.Intent.SCREEN_ON -> runOnUiThread { requestStartFeature(Feature.SCREEN) }
            X88VoiceIntentRouter.Intent.SCREEN_OFF -> runOnUiThread { stopFeature(Feature.SCREEN) }
            X88VoiceIntentRouter.Intent.STOP_ALL -> runOnUiThread {
                api.stopAll()
                X88EventJournal.add("stop_all", "voice")
                if (::avatar.isInitialized) avatar.mode = X88AvatarView.Mode.STOPPED
                showReply("Alles gestoppt.", false)
                refreshStatus()
            }
            X88VoiceIntentRouter.Intent.MASTER_ON -> runOnUiThread { toggleMaster() }
            X88VoiceIntentRouter.Intent.MASTER_OFF -> runOnUiThread {
                api.setMasterEnabled(false)
                X88EventJournal.add("master_off", "voice")
                showReply("Gerätezugriff ausgeschaltet.", false)
                refreshStatus()
            }
            X88VoiceIntentRouter.Intent.OPEN_SETTINGS -> runOnUiThread { requestSettings() }
            X88VoiceIntentRouter.Intent.DEVICE_STATUS -> runOnUiThread { speakDeviceStatus() }
            X88VoiceIntentRouter.Intent.NONE -> requestDialogue(text)
        }
    }

    private fun speakDeviceStatus() {
        val active = listOf(Feature.CAMERA, Feature.MICROPHONE, Feature.SCREEN)
            .filter { api.getStatus(it).sessionActive }
        val reply = when {
            api.isBlocked() -> "STOP ALL ist aktiv. Der lokale Gerätezugriff ist blockiert."
            !api.isMasterEnabled() -> "Der lokale Gerätezugriff ist ausgeschaltet."
            active.isEmpty() -> "X-88 ist aktiv. ${api.getConnection().label}. Keine Medien-Sitzung läuft."
            else -> "X-88 ist aktiv. Aktiv: ${active.joinToString { it.title }}."
        }
        showReply(reply, true)
        refreshStatus()
    }

    private fun requestSettings() {
        val evaluation = ActionPolicy.evaluate(applicationContext, LocalDeviceActionExecutor.ACTION_OPEN_SETTINGS)
        val proposalId = evaluation.pendingProposalId
        if (evaluation.decision != ActionPolicy.Decision.CONFIRM || proposalId.isNullOrBlank()) {
            showReply("Android-Einstellungen sind aktuell nicht freigegeben.", true)
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Android-Einstellungen öffnen?")
            .setMessage("X-88 schlägt open_settings vor. Erst dein Klick führt die Aktion aus.")
            .setNegativeButton("Abbrechen") { _, _ -> ActionApprovalStore.deny(proposalId) }
            .setPositiveButton("Öffnen") { _, _ ->
                val grant = ActionApprovalStore.approve(proposalId)
                if (grant == null) {
                    showReply("Einmalfreigabe ungültig oder abgelaufen.", true)
                    return@setPositiveButton
                }
                val result = LocalDeviceActionExecutor.execute(applicationContext, grant.id, LocalDeviceActionExecutor.ACTION_OPEN_SETTINGS)
                if (result.ok) {
                    X88EventJournal.add("settings_open", "confirmed")
                    showReply("Android-Einstellungen geöffnet.", false)
                } else showReply("Einstellungen konnten nicht geöffnet werden.", true)
            }
            .setOnCancelListener { ActionApprovalStore.deny(proposalId) }
            .show()
    }

    private fun requestDialogue(text: String) {
        if (DialogueRouter.providerId() == null) {
            runOnUiThread { showReply("Die lokale Sprache läuft. Für freie Antworten ist noch kein KI-Provider aktiv.", true) }
            return
        }
        dialogueExecutor.execute {
            val outcome = DialogueRouter.generate(text)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (outcome.ok && !outcome.reply.isNullOrBlank()) showReply(outcome.reply.trim(), true)
                else showReply("Dialogfehler: ${outcome.error ?: "unbekannt"}", false)
            }
        }
    }

    override fun onSpeechStarted() {
        runOnUiThread {
            if (::api.isInitialized && ::avatar.isInitialized && !api.isBlocked()) {
                avatar.mode = X88AvatarView.Mode.SPEAKING
            }
            X88EventJournal.add("tts_start")
        }
    }

    override fun onSpeechFinished() {
        runOnUiThread {
            if (::api.isInitialized && ::avatar.isInitialized && !api.isBlocked()) {
                avatar.mode = X88AvatarView.Mode.IDLE
            }
            X88EventJournal.add("tts_done")
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            if (::avatar.isInitialized) avatar.mode = X88AvatarView.Mode.ATTENTION
            if (::statusView.isInitialized) statusView.text = "VOICE · Fehler"
            if (::replyView.isInitialized) replyView.text = "Ariana: $message"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showReply(message: String, speak: Boolean) {
        if (::replyView.isInitialized) replyView.text = "Ariana: $message"
        if (speak && ::voice.isInitialized) voice.speak(message)
    }

    private fun refreshStatus() {
        if (!::statusView.isInitialized || !::api.isInitialized) return
        val connection = api.getConnection()
        val camera = api.getStatus(Feature.CAMERA)
        val microphone = api.getStatus(Feature.MICROPHONE)
        val screen = api.getStatus(Feature.SCREEN)
        val masterOn = api.isMasterEnabled() && !api.isBlocked()
        val state = when {
            api.isBlocked() -> "STOP ALL"
            masterOn -> "MASTER ON"
            else -> "MASTER OFF"
        }
        statusView.text = "$state · ${connection.label} · CAM ${onOff(camera.sessionActive)} · MIC ${onOff(microphone.sessionActive)} · SCREEN ${onOff(screen.sessionActive)}"
        masterButton.text = if (masterOn) "MASTER · ON" else "MASTER · OFF"
        cameraButton.text = if (camera.sessionActive) "CAMERA · ON" else "CAMERA · OFF"
        microphoneButton.text = if (microphone.sessionActive) "MIC · ON" else "MIC · OFF"
        screenButton.text = if (screen.sessionActive) "SCREEN · ON" else "SCREEN · OFF"

        val notify = api.getStatus(Feature.NOTIFY_READ)
        val files = api.getStatus(Feature.FILES)
        val location = api.getStatus(Feature.LOCATION)
        val bluetooth = api.getStatus(Feature.BLUETOOTH)
        val treeSelected = TreePermissionStore(this).get() != null
        modulesView.text = "MODULES · NOTIFY ${ok(notify.permissionGranted)} (${NotificationStore.listRecent().size}) · FILES ${ok(files.permissionGranted || treeSelected)} · LOC ${ok(location.permissionGranted)} · BT ${ok(bluetooth.permissionGranted)} · PRESENCE ${PresenceSignalController.currentState()}"
        if (api.isBlocked()) avatar.mode = X88AvatarView.Mode.STOPPED
        else if (avatar.mode == X88AvatarView.Mode.STOPPED) avatar.mode = X88AvatarView.Mode.IDLE
        X88WidgetProvider.updateAll(applicationContext)
    }

    private fun row(left: MaterialButton, right: MaterialButton): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
        addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
    }

    private fun button(text: String, click: () -> Unit) = MaterialButton(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { click() }
    }

    private fun label(text: String, size: Float, color: String) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(Color.parseColor(color))
        setPadding(0, dp(5), 0, dp(5))
    }

    private fun spacer(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun onOff(value: Boolean) = if (value) "ON" else "OFF"
    private fun ok(value: Boolean) = if (value) "OK" else "—"
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
