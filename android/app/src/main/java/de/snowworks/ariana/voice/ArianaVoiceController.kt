package de.snowworks.ariana.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class ArianaVoiceController(
    context: Context,
    private val listener: Listener,
) : RecognitionListener, TextToSpeech.OnInitListener {

    interface Listener {
        fun onState(message: String)
        fun onTranscript(text: String)
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val recognizer: SpeechRecognizer? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        } else {
            null
        }

    private val tts = TextToSpeech(appContext, this)
    private var ttsReady = false

    init {
        recognizer?.setRecognitionListener(this)
    }

    fun isOnDeviceRecognitionAvailable(): Boolean = recognizer != null

    fun startListening() {
        val localRecognizer = recognizer
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
        localRecognizer.startListening(intent)
    }

    fun speak(text: String) {
        if (!ttsReady) {
            listener.onError("Die lokale Sprachausgabe ist noch nicht bereit.")
            return
        }
        val spoken = text.trim().take(2400)
        if (spoken.isEmpty()) return
        tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "ariana-x88-voice-v2")
    }

    fun shutdown() {
        recognizer?.cancel()
        recognizer?.destroy()
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
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        listener.onState("Sprich jetzt.")
    }

    override fun onBeginningOfSpeech() {
        listener.onState("Ich höre dich …")
    }

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        listener.onState("Verarbeite lokal …")
    }

    override fun onError(error: Int) {
        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audiofehler bei der Spracherkennung."
            SpeechRecognizer.ERROR_CLIENT -> "Spracherkennung wurde abgebrochen."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon-Berechtigung fehlt."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Lokale Spracherkennung meldet einen Dienstfehler."
            SpeechRecognizer.ERROR_NO_MATCH -> "Ich habe nichts eindeutig verstanden."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Spracherkennung ist gerade beschäftigt."
            SpeechRecognizer.ERROR_SERVER -> "Lokaler Spracherkennungsdienst meldet einen Fehler."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Keine Sprache erkannt."
            else -> "Spracherkennung fehlgeschlagen (Code $error)."
        }
        listener.onError(message)
    }

    override fun onResults(results: Bundle?) {
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()

        if (text.isBlank()) {
            listener.onError("Kein Text erkannt.")
            return
        }

        listener.onTranscript(text)
    }

    override fun onPartialResults(partialResults: Bundle?) = Unit

    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
