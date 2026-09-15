package de.snowworks.app.widget

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import de.snowworks.app.R
import de.snowworks.app.ui.X88HomeActivity
import de.snowworks.ariana.ArianaGate

class ArianaWakewordService : Service(), RecognitionListener {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var paused = false
    private var destroyed = false
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        current = this
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> {
                WakewordStateStore.setEnabled(this, false)
                shutdownWakeword()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                pauseNow()
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                resumeNow()
                return START_NOT_STICKY
            }
        }

        val gate = ArianaGate(this)
        if (!gate.isMasterEnabled || gate.isBlocked) {
            WakewordStateStore.setEnabled(this, false)
            shutdownWakeword()
            return START_NOT_STICKY
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            WakewordStateStore.setEnabled(this, false)
            publishStatus("WAKEWORD · MIKROFON FEHLT")
            shutdownWakeword()
            return START_NOT_STICKY
        }

        WakewordStateStore.setEnabled(this, true)
        paused = false
        ensureForeground("WAKEWORD · HÖRT ZU")
        startListeningSoon(150L)
        return START_NOT_STICKY
    }

    private fun createRecognizer(): SpeechRecognizer? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) return null
        return runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(this) }
            .getOrNull()
            ?.also { it.setRecognitionListener(this) }
    }

    private fun ensureRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }
        recognizer = createRecognizer()
        return recognizer
    }

    private fun startListeningSoon(delayMs: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ startListening() }, delayMs)
    }

    private fun startListening() {
        if (destroyed || paused || listening || !WakewordStateStore.isEnabled(this)) return
        val localRecognizer = ensureRecognizer() ?: run {
            publishStatus("WAKEWORD · ON-DEVICE STT FEHLT")
            WakewordStateStore.setEnabled(this, false)
            shutdownWakeword()
            return
        }
        val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        listening = true
        publishStatus("WAKEWORD · HÖRT ZU")
        runCatching { localRecognizer.startListening(request) }
            .onFailure {
                listening = false
                rebuildRecognizer(650L)
            }
    }

    private fun scheduleRestart(delayMs: Long = 350L) {
        if (destroyed || paused || !WakewordStateStore.isEnabled(this)) return
        startListeningSoon(delayMs)
    }

    private fun rebuildRecognizer(delayMs: Long) {
        val old = recognizer
        recognizer = null
        listening = false
        runCatching { old?.cancel() }
        runCatching { old?.destroy() }
        scheduleRestart(delayMs)
    }

    private fun pauseNow() {
        paused = true
        cancelRecognition()
        publishStatus("WAKEWORD · PAUSIERT")
    }

    private fun resumeNow() {
        if (destroyed || !WakewordStateStore.isEnabled(this)) return
        val gate = ArianaGate(this)
        if (!gate.isMasterEnabled || gate.isBlocked ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            WakewordStateStore.setEnabled(this, false)
            shutdownWakeword()
            return
        }
        paused = false
        ensureForeground("WAKEWORD · HÖRT ZU")
        startListeningSoon(200L)
    }

    private fun cancelRecognition() {
        listening = false
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.cancel() }
    }

    private fun publishStatus(status: String) {
        WakewordStateStore.setStatus(this, status)
        if (!destroyed && WakewordStateStore.isEnabled(this)) {
            ensureForeground(status)
        }
    }

    private fun ensureForeground(status: String) {
        ensureChannel()
        val notification = buildNotification(status)
        if (!foregroundStarted) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
            foregroundStarted = true
        } else {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(status: String): Notification {
        val openIntent = Intent(this, X88HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val open = PendingIntent.getActivity(
            this, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val speakIntent = Intent(this, X88HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(X88HomeActivity.EXTRA_START_CONVERSATION, true)
        }
        val speak = PendingIntent.getActivity(
            this, 2, speakIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 3,
            Intent(this, ArianaWakewordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_snowworks)
            .setContentTitle(getString(R.string.wakeword_notification_title))
            .setContentText(status)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)
            .addAction(R.drawable.ic_snowworks, getString(R.string.widget_speak), speak)
            .addAction(R.drawable.ic_snowworks, getString(R.string.notif_stop), stop)
            .build()
    }

    private fun ensureChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.wakeword_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    override fun onReadyForSpeech(params: Bundle?) {
        publishStatus("WAKEWORD · HÖRT ZU")
    }

    override fun onBeginningOfSpeech() {
        publishStatus("WAKEWORD · HÖRT")
    }

    override fun onEndOfSpeech() {
        publishStatus("WAKEWORD · PRÜFT")
    }

    override fun onError(error: Int) {
        listening = false
        if (destroyed || paused || !WakewordStateStore.isEnabled(this)) return
        when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                WakewordStateStore.setEnabled(this, false)
                shutdownWakeword()
            }
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_CLIENT -> rebuildRecognizer(650L)
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> scheduleRestart(350L)
            else -> scheduleRestart(900L)
        }
    }

    override fun onResults(results: Bundle?) {
        listening = false
        val candidates = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty()
        if (candidates.any(WakewordPolicy::matches)) {
            paused = true
            publishStatus("WAKEWORD · ERKANNT")
            val delivered = WakewordSignalBus.emit()
            if (!delivered) {
                publishStatus("WAKEWORD · ERKANNT · SPRECHEN TIPPEN")
                handler.postDelayed({
                    if (paused && WakewordStateStore.isEnabled(this) && !destroyed) {
                        paused = false
                        startListeningSoon(100L)
                    }
                }, 12_000L)
            }
        } else {
            scheduleRestart(300L)
        }
    }

    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun shutdownWakeword() {
        destroyed = true
        paused = true
        listening = false
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
        stopSelf()
    }

    override fun onDestroy() {
        if (current === this) current = null
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "de.snowworks.app.wakeword.START"
        const val ACTION_STOP = "de.snowworks.app.wakeword.STOP"
        const val ACTION_PAUSE = "de.snowworks.app.wakeword.PAUSE"
        const val ACTION_RESUME = "de.snowworks.app.wakeword.RESUME"
        private const val CHANNEL_ID = "ariana_presence_wakeword"
        private const val NOTIFICATION_ID = 88
        @Volatile private var current: ArianaWakewordService? = null

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ArianaWakewordService::class.java).setAction(ACTION_START),
            )
        }

        fun pause(context: Context) {
            if (!WakewordStateStore.isEnabled(context)) return
            current?.let {
                it.pauseNow()
                return
            }
            ContextCompat.startForegroundService(
                context,
                Intent(context, ArianaWakewordService::class.java).setAction(ACTION_PAUSE),
            )
        }

        fun resume(context: Context) {
            if (!WakewordStateStore.isEnabled(context)) return
            current?.let {
                it.resumeNow()
                return
            }
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ArianaWakewordService::class.java).setAction(ACTION_RESUME),
                )
            }
        }

        fun stop(context: Context) {
            current?.let {
                it.shutdownWakeword()
                WakewordStateStore.setEnabled(context, false)
                return
            }
            runCatching {
                context.startService(Intent(context, ArianaWakewordService::class.java).setAction(ACTION_STOP))
            }
        }
    }
}
