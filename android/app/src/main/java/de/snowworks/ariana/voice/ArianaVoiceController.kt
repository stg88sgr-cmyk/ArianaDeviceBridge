package de.snowworks.ariana.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class ArianaVoiceController(
    context: Context,
    private val listener: Listener,
) : RecognitionListener, TextToSpeech.OnInitListener {

    interface Listener {
        fun onState(message: String)
        fun onTranscript(text: String)
        fun onError(message: String)
        fun onSpeechStarted() = Unit
        fun onSpeechFinished() = Unit
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = createRecognizer()
    private val tts = TextToSpeech(appContext, this)

    private var ttsReady = false
    @Volatile private var speechActive = false
    @Volatile private var listeningActive = false
    @Volatile private var suppressCancelError = false
    @Volatile private var recognitionReady = false
    @Volatile private var retryPending = false
    @Volatile private var recognitionAttempt = 0

    init {
        recognizer?.setRecognitionListener(this)
    }

    private fun createRecognizer(): SpeechRecognizer? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        ) {
            runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext) }.getOrNull()
        } else {
            null
        }

    private fun ensureRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }
        return createRecognizer()?.also {
            it.setRecognitionListener(this)
            recognizer = it
        }
    }

    fun isOnDeviceRecognitionAvailable(): Boolean = recognizer != null ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext))

    fun isListening(): Boolean = listeningActive
    fun isSpeaking(): Boolean = speechActive || tts.isSpeaking

    fun startListening() = startListeningInternal(allowRetry = true)

    private fun startListeningInternal(allowRetry: Boolean) {
        if (listeningActive) return
        val localRecognizer = ensureRecognizer()
        if (localRecognizer == null) {
            listener.onError("Lokale Spracherkennung ist auf diesem Gerät nicht verfügbar.")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        listener.onState("Ich höre zu …")
        recognitionReady = false
        retryPending = allowRetry
        val attempt = ++recognitionAttempt
        listeningActive = true

        runCatching { localRecognizer.startListening(intent) }
            .onSuccess {
                mainHandler.postDelayed({
                    if (attempt == recognitionAttempt && listeningActive && !recognitionReady && retryPending) {
                        retryListeningOnce()
                    }
                }, 1800L)
            }
            .onFailure {
                listeningActive = false
                if (allowRetry) retryListeningOnce()
                else listener.onError("Spracherkennung konnte nicht gestartet werden.")
            }
    }

    private fun retryListeningOnce() {
        if (!retryPending) return

        retryPending = false
        recognitionReady = false
        listeningActive = false
        recognitionAttempt++
        suppressCancelError = true

        val oldRecognizer = recognizer
        recognizer = null
        runCatching { oldRecognizer?.cancel() }
        runCatching { oldRecognizer?.destroy() }

        listener.onState("Spracherkennung wird neu aktiviert …")
        mainHandler.postDelayed({
            val freshRecognizer = createRecognizer()
            recognizer = freshRecognizer
            freshRecognizer?.setRecognitionListener(this)
            if (freshRecognizer == null) {
                suppressCancelError = false
                listener.onError("Lokale Spracherkennung ist auf diesem Gerät nicht verfügbar.")
            } else {
                startListeningInternal(allowRetry = false)
            }
        }, 450L)
    }

    fun interruptSpeech(): Boolean {
        val wasSpeaking = speechActive || tts.isSpeaking
        if (!wasSpeaking) return false
        speechActive = false
        tts.stop()
        listener.onSpeechFinished()
        return true
    }

    fun cancelListening(): Boolean {
        retryPending = false
        recognitionReady = false
        recognitionAttempt++
        val wasListening = listeningActive
        listeningActive = false
        suppressCancelError = true

        val oldRecognizer = recognizer
        recognizer = null
        runCatching { oldRecognizer?.cancel() }
        runCatching { oldRecognizer?.destroy() }
        return wasListening || oldRecognizer != null
    }

    fun speak(text: String) {
        if (!ttsReady) {
            listener.onError("Die lokale Sprachausgabe ist noch nicht bereit.")
            return
        }
        val spoken = text.trim().take(2400)
        if (spoken.isEmpty()) return
        tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "ariana-x88-voice-v3")
    }

    fun shutdown() {
        retryPending = false
        recognitionAttempt++
        mainHandler.removeCallbacksAndMessages(null)
        listeningActive = false
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        tts.stop()
        tts.shutdown()
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            listener.onError("Android-TTS konnte nicht initialisiert werden.")
            return
        }
        val result = tts.setLanguage(Locale.GERMANY)
        ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED
        if (!ttsReady) {
            listener.onError("Für Android-TTS fehlt eine deutsche Stimme.")
            return
        }

        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    speechActive = true
                    listener.onSpeechStarted()
                }

                override fun onDone(utteranceId: String?) {
                    if (!speechActive) return
                    speechActive = false
                    listener.onSpeechFinished()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    val wasActive = speechActive
                    speechActive = false
                    if (wasActive) listener.onSpeechFinished()
                    listener.onError("Android-TTS konnte die Ausgabe nicht abschließen.")
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    if (!speechActive) return
                    speechActive = false
                    listener.onSpeechFinished()
                }
            },
        )
    }

    override fun onReadyForSpeech(params: Bundle?) {
        recognitionReady = true
        retryPending = false
        suppressCancelError = false
        listener.onState("Sprich jetzt.")
    }

    override fun onBeginningOfSpeech() {
        recognitionReady = true
        retryPending = false
        suppressCancelError = false
        listener.onState("Ich höre dich …")
    }

    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        listener.onState("Verarbeite lokal …")
    }

    override fun onError(error: Int) {
        listeningActive = false
        recognitionReady = false

        if (VoiceRecoveryPolicy.shouldRebuildRecognizer(error) && retryPending) {
            retryListeningOnce()
            return
        }

        if (suppressCancelError && error == SpeechRecognizer.ERROR_CLIENT) {
            suppressCancelError = false
            return
        }
        suppressCancelError = false

        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audiofehler bei der Spracherkennung."
            SpeechRecognizer.ERROR_CLIENT -> "Ich habe nichts eindeutig verstanden. Spracherkennung wurde neu synchronisiert."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon-Berechtigung fehlt."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Lokale Spracherkennung meldet einen Dienstfehler."
            SpeechRecognizer.ERROR_NO_MATCH -> "Ich habe nichts eindeutig verstanden."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Ich habe nichts eindeutig verstanden. Spracherkennung ist noch beschäftigt."
            SpeechRecognizer.ERROR_SERVER -> "Lokaler Spracherkennungsdienst meldet einen Fehler."
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Lokaler Spracherkennungsdienst wurde getrennt."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Keine Sprache erkannt."
            else -> "Spracherkennung fehlgeschlagen (Code $error)."
        }
        listener.onError(message)
    }

    override fun onResults(results: Bundle?) {
        listeningActive = false
        recognitionReady = false
        retryPending = false
        recognitionAttempt++

        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()

        if (text.isBlank()) {
            listener.onError("Ich habe nichts eindeutig verstanden.")
            return
        }
        listener.onTranscript(text)
    }

    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
