package de.snowworks.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.EditText
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
import de.snowworks.app.widget.PresenceWidgetStateStore
import de.snowworks.app.widget.ArianaWakewordService
import de.snowworks.app.widget.WakewordSignalBus
import de.snowworks.app.widget.WakewordStateStore
import de.snowworks.ariana.ArianaDeviceApi
import de.snowworks.ariana.ArianaResult
import de.snowworks.ariana.Feature
import de.snowworks.ariana.bridge.ActionApprovalStore
import de.snowworks.ariana.bridge.ActionPolicy
import de.snowworks.ariana.bridge.AiProviderManager
import de.snowworks.ariana.bridge.DialogueRouter
import de.snowworks.ariana.bridge.LocalAiProviderManager
import de.snowworks.ariana.bridge.LoopbackArianaProvider
import de.snowworks.ariana.bridge.LoopbackArianaProviderManager
import de.snowworks.ariana.bridge.LocalDeviceActionExecutor
import de.snowworks.ariana.files.TreePermissionStore
import de.snowworks.ariana.notify.NotificationStore
import de.snowworks.ariana.presence.PresenceSignalController
import de.snowworks.ariana.voice.ArianaVoiceController
import de.snowworks.ariana.voice.VoiceRecoveryPolicy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class X88HomeActivity : AppCompatActivity(), ArianaVoiceController.Listener {
    companion object {
        const val EXTRA_START_CONVERSATION = "de.snowworks.app.extra.START_CONVERSATION"
    }
    private lateinit var api: ArianaDeviceApi
    private lateinit var voice: ArianaVoiceController
    private lateinit var avatar: X88AvatarView
    private lateinit var masterButton: MaterialButton
    private lateinit var cameraButton: MaterialButton
    private lateinit var microphoneButton: MaterialButton
    private lateinit var screenButton: MaterialButton
    private lateinit var coreButton: MaterialButton
    private lateinit var conversationButton: MaterialButton
    private lateinit var wakewordButton: MaterialButton
    private lateinit var statusView: TextView
    private lateinit var transcriptView: TextView
    private lateinit var replyView: TextView
    private lateinit var chatHistoryView: TextView
    private lateinit var chatHistoryScroll: ScrollView
    private lateinit var dialogStateView: TextView
    private lateinit var modulesView: TextView
    private lateinit var textInput: EditText
    private var pendingPermissionFeature: Feature? = null
    @Volatile private var coreStatusLabel = "CORE · CHECKING"
    @Volatile private var historyLoaded = false
    @Volatile private var dialogStateLabel = "DIALOG · READY"
    @Volatile private var conversationActive = false

    private val dialogueExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ArianaX88HomeDialogue").apply { isDaemon = true }
    }
    private val healthExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "ArianaX88CoreHealth").apply { isDaemon = true }
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

    private val wakewordPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) enableWakewordNow()
            else {
                WakewordStateStore.setEnabled(this, false)
                refreshWakewordButton()
                showReply("Wakeword braucht Mikrofon-Berechtigung.", false)
            }
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
                resumeWakewordIfIdle()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = ArianaDeviceApi(this)
        voice = ArianaVoiceController(this, this)
        if (!runCatching { LoopbackArianaProviderManager.activateIfAvailable() }.getOrDefault(false) &&
            !runCatching { LocalAiProviderManager.activateConfigured(this) }.getOrDefault(false)
        ) {
            runCatching { AiProviderManager.activateConfigured(this) }
        }
        setContentView(buildUi())
        X88EventJournal.add("home_open")
        refreshStatus()
        startCoreHealthMonitor()
        loadLastConversation()
        handlePresenceIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePresenceIntent(intent)
    }

    private fun handlePresenceIntent(sourceIntent: Intent?) {
        if (sourceIntent?.getBooleanExtra(EXTRA_START_CONVERSATION, false) != true) return
        sourceIntent.removeExtra(EXTRA_START_CONVERSATION)
        if (::conversationButton.isInitialized) {
            conversationButton.post {
                if (!conversationActive && !isFinishing && !isDestroyed) startConversation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        WakewordSignalBus.listener = {
            runOnUiThread {
                if (!conversationActive && !isFinishing && !isDestroyed) startConversation()
            }
        }
        WakewordSignalBus.statusListener = { status ->
            runOnUiThread {
                if (::wakewordButton.isInitialized && WakewordStateStore.isEnabled(this)) {
                    wakewordButton.text = status
                }
            }
        }
        if (!runCatching { LoopbackArianaProviderManager.activateIfAvailable() }.getOrDefault(false) &&
            !runCatching { LocalAiProviderManager.activateConfigured(this) }.getOrDefault(false)
        ) {
            runCatching { AiProviderManager.activateConfigured(this) }
        }
        if (::statusView.isInitialized) refreshStatus()
        if (!historyLoaded) loadLastConversation()
        if (WakewordStateStore.isEnabled(this) && !conversationActive &&
            api.isMasterEnabled() && !api.isBlocked() &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        ) {
            ArianaWakewordService.start(this)
        }
    }

    override fun onPause() {
        WakewordSignalBus.listener = null
        WakewordSignalBus.statusListener = null
        super.onPause()
    }

    override fun onDestroy() {
        WakewordSignalBus.listener = null
        WakewordSignalBus.statusListener = null
        dialogueExecutor.shutdownNow()
        healthExecutor.shutdownNow()
        voice.shutdown()
        super.onDestroy()
    }

    private fun loadLastConversation() {
        dialogueExecutor.execute {
            val history = LoopbackArianaProvider().recentHistory(24)
            val assistantIndex = history.indexOfLast { it.role == "assistant" }
            val assistant = history.getOrNull(assistantIndex)
            val user = if (assistantIndex > 0) {
                history.subList(0, assistantIndex).lastOrNull { it.role == "user" }
            } else null

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                renderHistory(history)
                if (assistant != null && transcriptView.text == "Du: Noch nichts gesprochen.") {
                    user?.let { transcriptView.text = "Du: ${it.content}" }
                    replyView.text = "Ariana: ${assistant.content}"
                }
                historyLoaded = true
            }
        }
    }

    private fun renderHistory(history: List<LoopbackArianaProvider.HistoryMessage>) {
        if (!::chatHistoryView.isInitialized) return
        if (history.isEmpty()) {
            chatHistoryView.text = "Noch kein lokaler Dialog gespeichert."
        } else {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
            val dateTimeFormat = DateTimeFormatter.ofPattern("dd.MM. · HH:mm")
            val lines = mutableListOf<String>()
            var previousTimestamp: Double? = null
            history.forEach { item ->
                val timestamp = item.timestampSeconds
                val startsSession = previousTimestamp == null ||
                    (timestamp != null && previousTimestamp != null && timestamp - previousTimestamp!! > 30 * 60.0)
                if (startsSession) {
                    val heading = timestamp?.let { raw ->
                        val dateTime = Instant.ofEpochMilli((raw * 1000).toLong()).atZone(zone)
                        if (dateTime.toLocalDate() == today) {
                            "HEUTE · ${timeFormat.format(dateTime)}"
                        } else {
                            "VORHERIGE SITZUNG · ${dateTimeFormat.format(dateTime)}"
                        }
                    } ?: "SITZUNG"
                    if (lines.isNotEmpty()) lines += ""
                    lines += "── $heading ──"
                }
                val who = if (item.role == "user") "Du" else "Ariana"
                val stamp = timestamp?.let { raw ->
                    val dateTime = Instant.ofEpochMilli((raw * 1000).toLong()).atZone(zone)
                    " · ${timeFormat.format(dateTime)}"
                }.orEmpty()
                lines += "$who$stamp: ${item.content}"
                if (timestamp != null) previousTimestamp = timestamp
            }
            chatHistoryView.text = lines.joinToString("\n\n")
        }
        if (::chatHistoryScroll.isInitialized) {
            chatHistoryScroll.post { chatHistoryScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun startCoreHealthMonitor() {
        healthExecutor.scheduleWithFixedDelay({
            val provider = LoopbackArianaProvider()
            val health = provider.health()
            val history = if (health.online) runCatching { provider.recentHistory(24) }.getOrDefault(emptyList()) else emptyList()
            coreStatusLabel = if (health.online) {
                val gen = health.generation?.let { "GEN $it" } ?: "GEN ?"
                val branch = health.branch?.uppercase() ?: "UNKNOWN"
                "CORE ONLINE · $gen · $branch"
            } else {
                "CORE OFFLINE"
            }
            PresenceWidgetStateStore.publishCore(applicationContext, coreStatusLabel)
            runOnUiThread {
                if (!isFinishing && !isDestroyed && ::statusView.isInitialized) {
                    refreshStatus()
                    if (history.isNotEmpty()) renderHistory(history)
                }
            }
        }, 0L, 5L, TimeUnit.SECONDS)
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
        chatHistoryView = label("Verlauf wird geladen …", 13f, "#B8CAD6")
        chatHistoryScroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.parseColor("#080D14"))
            addView(chatHistoryView)
        }
        dialogStateView = label(dialogStateLabel, 11f, "#25D9FF")
        modulesView = label("MODULES · loading …", 11f, "#7896A6")
        root.addView(statusView)
        root.addView(dialogStateView)
        root.addView(chatHistoryScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(240)))
        root.addView(transcriptView)
        root.addView(replyView)
        root.addView(modulesView)
        root.addView(spacer(8))
        coreButton = button("CORE · CHECKING") { recoverCore() }
        root.addView(coreButton)
        root.addView(button("CHARACTER · PROFILE") {
            startActivity(Intent(this, X88CharacterActivity::class.java))
        })

        masterButton = button("MASTER") { toggleMaster() }
        cameraButton = button("CAMERA") { toggleFeature(Feature.CAMERA) }
        microphoneButton = button("MIC") { toggleFeature(Feature.MICROPHONE) }
        screenButton = button("SCREEN") { toggleFeature(Feature.SCREEN) }

        root.addView(masterButton)
        root.addView(button("TALK · Push-to-talk") { startPushToTalk() })
        conversationButton = button(conversationLabel()) { toggleConversation() }
        root.addView(conversationButton)
        wakewordButton = button(wakewordLabel()) { toggleWakeword() }
        root.addView(wakewordButton)
        textInput = EditText(this).apply {
            hint = "Nachricht an Ariana …"
            setTextColor(Color.parseColor("#EAF7FF"))
            setHintTextColor(Color.parseColor("#6F8799"))
            setBackgroundColor(Color.parseColor("#0B1018"))
            setPadding(dp(12))
            maxLines = 4
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendTypedMessage()
                    true
                } else false
            }
        }
        root.addView(textInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(button("SEND · Lokal") { sendTypedMessage() })
        root.addView(row(cameraButton, microphoneButton))
        root.addView(row(screenButton, button("GRANTS") {
            startActivity(Intent(this, DeviceGrantsActivity::class.java))
        }))
        root.addView(row(
            button("SETTINGS") { requestSettings() },
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
            disableWakeword("stop_all")
            stopConversation("stop_all")
            api.stopAll()
            X88EventJournal.add("stop_all", "button")
            avatar.mode = X88AvatarView.Mode.STOPPED
            showReply("Alles gestoppt. Gerätezugriff ist jetzt blockiert.", false)
            refreshStatus()
        }.apply {
            setBackgroundColor(Color.parseColor("#341018"))
            setTextColor(Color.parseColor("#FF8CA7"))
        })
        root.addView(label("Voice: Kamera an/aus · Mikrofon an/aus · Bildschirm teilen/stoppen · Gerätestatus · Gespräch beenden · Alles stoppen", 11f, "#788D9C"))

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#05070D"))
            addView(root)
        }
    }

    private fun recoverCore() {
        if (coreStatusLabel.startsWith("CORE ONLINE")) {
            showReply("Der lokale Ariana-Core ist bereits online.", false)
            return
        }
        val launch = packageManager.getLaunchIntentForPackage("com.termux")
        if (launch == null) {
            showReply("Termux wurde nicht gefunden.", true)
            return
        }
        X88EventJournal.add("core_recover", "open_termux")
        showReply("Ich öffne Termux. Der Guardian startet dort automatisch.", false)
        startActivity(launch)
    }

    private fun toggleMaster() {
        if (api.isMasterEnabled() && !api.isBlocked()) {
            disableWakeword("master_off")
            api.setMasterEnabled(false)
            X88EventJournal.add("master_off", "button")
            stopConversation("master_off")
            showReply("Gerätezugriff ausgeschaltet.", false)
            refreshStatus()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("ARIANA X-88 Gerätezugriff einschalten?")
            .setMessage("Der lokale Master-Schalter wird aktiviert. Android-Berechtigungen werden dadurch nicht automatisch erteilt.")
            .setNegativeButton("Abbrechen") { _, _ -> resumeWakewordIfIdle() }
            .setOnCancelListener { resumeWakewordIfIdle() }
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
            .setNegativeButton("Abbrechen") { _, _ -> resumeWakewordIfIdle() }
            .setOnCancelListener { resumeWakewordIfIdle() }
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
        resumeWakewordIfIdle()
    }


    private fun wakewordLabel(): String =
        if (WakewordStateStore.isEnabled(this)) WakewordStateStore.status(this) else "WAKEWORD · OFF"

    private fun refreshWakewordButton() {
        if (::wakewordButton.isInitialized) wakewordButton.text = wakewordLabel()
    }

    private fun voicePathBusy(): Boolean =
        conversationActive || voice.isListening() || voice.isSpeaking()

    private fun toggleWakeword() {
        if (WakewordStateStore.isEnabled(this)) {
            disableWakeword("button")
            showReply("Wakeword ausgeschaltet.", false)
            return
        }
        if (voicePathBusy()) {
            showReply("Beende zuerst den laufenden Sprachvorgang.", false)
            return
        }
        if (!api.isMasterEnabled() || api.isBlocked()) {
            showReply("Schalte zuerst den X-88 Gerätezugriff ein.", true)
            return
        }
        if (!voice.isOnDeviceRecognitionAvailable()) {
            showReply("Lokale On-Device-Spracherkennung ist nicht verfügbar.", true)
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            enableWakewordNow()
        } else {
            wakewordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun enableWakewordNow() {
        if (voicePathBusy()) {
            showReply("Wakeword startet nach dem laufenden Sprachvorgang noch nicht automatisch.", false)
            refreshWakewordButton()
            return
        }
        WakewordStateStore.setEnabled(this, true)
        ArianaWakewordService.start(this)
        refreshWakewordButton()
        X88EventJournal.add("wakeword", "on")
        showReply("Wakeword aktiv: Ariana, rede mit mir.", false)
    }

    private fun disableWakeword(source: String) {
        if (!WakewordStateStore.isEnabled(this)) return
        WakewordStateStore.setEnabled(this, false)
        ArianaWakewordService.stop(this)
        refreshWakewordButton()
        X88EventJournal.add("wakeword", "off:$source")
    }

    private fun resumeWakewordIfIdle() {
        if (!conversationActive && WakewordStateStore.isEnabled(this) &&
            api.isMasterEnabled() && !api.isBlocked()
        ) ArianaWakewordService.resume(this)
    }

    private fun conversationLabel(): String =
        if (conversationActive) "CONVERSATION · STOP" else "CONVERSATION · START"

    private fun toggleConversation() {
        if (conversationActive) stopConversation("button")
        else startConversation()
    }

    private fun startConversation() {
        if (!api.isMasterEnabled() || api.isBlocked()) {
            showReply("Schalte zuerst den X-88 Gerätezugriff ein.", true)
            return
        }
        if (!voice.isOnDeviceRecognitionAvailable()) {
            onError("Auf diesem Gerät ist keine Android On-Device-Spracherkennung verfügbar.")
            return
        }
        ArianaWakewordService.pause(this)
        conversationActive = true
        if (::conversationButton.isInitialized) conversationButton.text = conversationLabel()
        X88EventJournal.add("conversation", "start")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startConversationListening()
        } else {
            pushToTalkPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun stopConversation(source: String) {
        val wasActive = conversationActive
        conversationActive = false
        voice.interruptSpeech()
        voice.cancelListening()
        if (::conversationButton.isInitialized) conversationButton.text = conversationLabel()
        setDialogState("DIALOG · READY")
        if (!api.isBlocked()) avatar.mode = X88AvatarView.Mode.IDLE
        if (wasActive) X88EventJournal.add("conversation", "stop:$source")
        resumeWakewordIfIdle()
    }

    private fun startConversationListening() {
        if (!conversationActive || isFinishing || isDestroyed) return
        if (!api.isMasterEnabled() || api.isBlocked()) {
            stopConversation("master")
            return
        }
        if (!voice.isOnDeviceRecognitionAvailable() || voice.isListening()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopConversation("permission")
            return
        }
        setDialogState("DIALOG · HÖRT ZU")
        X88EventJournal.add("conversation_listen")
        voice.startListening()
    }

    private fun ensureConversationListeningAfterSpeech(attempt: Int = 0) {
        if (!conversationActive || isFinishing || isDestroyed) return
        if (voice.isSpeaking()) {
            if (attempt < 120) {
                dialogStateView.postDelayed({ ensureConversationListeningAfterSpeech(attempt + 1) }, 250L)
            } else {
                stopConversation("tts_rearm_timeout")
            }
            return
        }
        if (!voice.isListening()) startConversationListening()
    }

    private fun startPushToTalk() {
        if (!api.isMasterEnabled() || api.isBlocked()) {
            showReply("Schalte zuerst den X-88 Gerätezugriff ein.", true)
            return
        }
        if (!voice.isOnDeviceRecognitionAvailable()) {
            onError("Auf diesem Gerät ist keine Android On-Device-Spracherkennung verfügbar.")
            return
        }
        ArianaWakewordService.pause(this)
        if (voice.interruptSpeech()) {
            X88EventJournal.add("tts_interrupt", "talk")
            setDialogState("DIALOG · HÖRT ZU")
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voice.startListening()
        } else {
            pushToTalkPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onState(message: String) {
        runOnUiThread {
            statusView.text = "VOICE · $message"
            avatar.mode = when {
                message.contains("höre", true) || message.contains("sprich", true) -> X88AvatarView.Mode.LISTENING
                message.contains("verarbeite", true) -> X88AvatarView.Mode.THINKING
                else -> avatar.mode
            }
        }
    }

    override fun onTranscript(text: String) {
        runOnUiThread {
            transcriptView.text = "Du: $text"
            replyView.text = "Ariana: …"
            avatar.mode = X88AvatarView.Mode.THINKING
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
                disableWakeword("stop_all_voice")
                stopConversation("stop_all")
                api.stopAll()
                X88EventJournal.add("stop_all", "voice")
                avatar.mode = X88AvatarView.Mode.STOPPED
                showReply("Alles gestoppt.", false)
                refreshStatus()
            }
            X88VoiceIntentRouter.Intent.MASTER_ON -> runOnUiThread { toggleMaster() }
            X88VoiceIntentRouter.Intent.MASTER_OFF -> runOnUiThread {
                disableWakeword("master_off_voice")
                stopConversation("master_off")
                api.setMasterEnabled(false)
                X88EventJournal.add("master_off", "voice")
                showReply("Gerätezugriff ausgeschaltet.", false)
                refreshStatus()
            }
            X88VoiceIntentRouter.Intent.CONVERSATION_STOP -> runOnUiThread {
                stopConversation("voice")
                showReply("Gesprächsmodus beendet.", false)
            }
            X88VoiceIntentRouter.Intent.OPEN_SETTINGS -> runOnUiThread { requestSettings() }
            X88VoiceIntentRouter.Intent.DEVICE_STATUS -> runOnUiThread { speakDeviceStatus() }
            X88VoiceIntentRouter.Intent.NONE -> {
                runOnUiThread { setDialogState("DIALOG · DENKT") }
                requestDialogue(text, speak = true)
            }
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
            .setNegativeButton("Abbrechen") { _, _ ->
                ActionApprovalStore.deny(proposalId)
                resumeWakewordIfIdle()
            }
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
            .setOnCancelListener {
                ActionApprovalStore.deny(proposalId)
                resumeWakewordIfIdle()
            }
            .show()
    }

    private fun sendTypedMessage() {
        val text = textInput.text.toString().trim()
        if (text.isBlank()) return
        textInput.text.clear()
        transcriptView.text = "Du: $text"
        replyView.text = "Ariana: …"
        avatar.mode = X88AvatarView.Mode.THINKING
        setDialogState("DIALOG · DENKT")
        X88EventJournal.add("text_dialogue")
        requestDialogue(text, speak = false)
    }

    private fun requestDialogue(text: String, speak: Boolean) {
        if (DialogueRouter.providerId() == null) {
            runOnUiThread {
                if (conversationActive) stopConversation("provider_unavailable")
                setDialogState("DIALOG · OFFLINE")
                if (!api.isBlocked()) avatar.mode = X88AvatarView.Mode.ATTENTION
                showReply("Die lokale Sprache läuft. Für freie Antworten ist noch kein KI-Provider aktiv.", true)
            }
            return
        }
        dialogueExecutor.execute {
            val outcome = DialogueRouter.generate(text)
            val history = if (outcome.ok) LoopbackArianaProvider().recentHistory(24) else emptyList()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (outcome.ok && !outcome.reply.isNullOrBlank()) {
                    setDialogState("DIALOG · ANTWORT")
                    showReply(outcome.reply.trim(), speak)
                    renderHistory(history)
                    if (speak && conversationActive) {
                        dialogStateView.postDelayed({ ensureConversationListeningAfterSpeech() }, 1200L)
                    }
                    if (!speak) dialogStateView.postDelayed({ setDialogState("DIALOG · READY") }, 1200L)
                } else {
                    if (conversationActive) stopConversation("dialogue_error")
                    setDialogState("DIALOG · FEHLER")
                    if (!api.isBlocked()) avatar.mode = X88AvatarView.Mode.ATTENTION
                    showReply("Dialogfehler: ${outcome.error ?: "unbekannt"}", false)
                }
            }
        }
    }

    override fun onSpeechStarted() {
        runOnUiThread {
            if (!api.isBlocked()) avatar.mode = X88AvatarView.Mode.SPEAKING
            setDialogState("DIALOG · SPRICHT")
            X88EventJournal.add("tts_start")
        }
    }

    override fun onSpeechFinished() {
        runOnUiThread {
            if (!api.isBlocked()) avatar.mode = X88AvatarView.Mode.IDLE
            setDialogState("DIALOG · READY")
            X88EventJournal.add("tts_done")
            if (conversationActive) {
                dialogStateView.postDelayed({ startConversationListening() }, 900L)
            } else {
                dialogStateView.postDelayed({ resumeWakewordIfIdle() }, 450L)
            }
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            val recoverableConversationError =
                conversationActive && VoiceRecoveryPolicy.isRecoverableMessage(message)
            if (recoverableConversationError) {
                X88EventJournal.add("conversation_rearm", "idle")
                statusView.text = "VOICE · Warte auf dich …"
                setDialogState("DIALOG · HÖRT ZU")
                if (!api.isBlocked()) avatar.mode = X88AvatarView.Mode.LISTENING
                dialogStateView.postDelayed({ startConversationListening() }, 350L)
                return@runOnUiThread
            }
            if (conversationActive) stopConversation("voice_error")
            else resumeWakewordIfIdle()
            avatar.mode = X88AvatarView.Mode.ATTENTION
            statusView.text = "VOICE · Fehler"
            replyView.text = "Ariana: $message"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setDialogState(value: String) {
        dialogStateLabel = value
        PresenceWidgetStateStore.publishDialog(applicationContext, value)
        if (::dialogStateView.isInitialized) dialogStateView.text = value
    }

    private fun showReply(message: String, speak: Boolean) {
        replyView.text = "Ariana: $message"
        if (speak) voice.speak(message)
    }

    private fun refreshStatus() {
        if (!::statusView.isInitialized) return
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
        statusView.text = "$coreStatusLabel\n$state · ${connection.label} · CAM ${onOff(camera.sessionActive)} · MIC ${onOff(microphone.sessionActive)} · SCREEN ${onOff(screen.sessionActive)}"
        masterButton.text = if (masterOn) "MASTER · ON" else "MASTER · OFF"
        refreshWakewordButton()
        if (::coreButton.isInitialized) {
            coreButton.text = if (coreStatusLabel.startsWith("CORE ONLINE")) "CORE · ONLINE" else "CORE · RECOVER"
        }
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
