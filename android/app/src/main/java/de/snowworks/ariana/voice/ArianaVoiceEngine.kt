package de.snowworks.ariana.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.Closeable
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin local voice adapter for Ariana.
 *
 * It intentionally talks to Android's currently selected system TTS engine
 * instead of binding the app to one vendor. On a phone where HayaiTTS is the
 * preferred engine and Ramona is the de-DE default, Ariana will therefore use
 * that voice automatically.
 */
class ArianaVoiceEngine(
    context: Context,
    private val listener: Listener = Listener.NOOP,
) : TextToSpeech.OnInitListener, Closeable {

    interface Listener {
        fun onReady()
        fun onSpeakingChanged(speaking: Boolean)
        fun onError(message: String)

        /**
         * Called when the selected Android TTS engine reports the text range
         * currently being spoken. Engines are allowed not to emit range
         * callbacks, so callers must keep a fallback animation path.
         */
        fun onUtteranceRange(text: String, start: Int, end: Int) = Unit

        object NOOP : Listener {
            override fun onReady() = Unit
            override fun onSpeakingChanged(speaking: Boolean) = Unit
            override fun onError(message: String) = Unit
        }
    }

    private val appContext = context.applicationContext
    private val ready = AtomicBoolean(false)
    private val utteranceTexts = ConcurrentHashMap<String, String>()

    @Volatile
    private var pendingText: String? = null

    @Volatile
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)

    override fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            listener.onError("Android TTS initialization failed: $status")
            return
        }

        val languageResult = engine.setLanguage(Locale.GERMANY)
        if (
            languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            listener.onError("German (de-DE) is not available in the selected TTS engine")
            return
        }

        engine.setSpeechRate(DEFAULT_RATE)
        engine.setPitch(DEFAULT_PITCH)
        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    listener.onSpeakingChanged(true)
                }

                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    val id = utteranceId ?: return
                    val text = utteranceTexts[id] ?: return
                    val safeStart = start.coerceIn(0, text.length)
                    val safeEnd = end.coerceIn(safeStart, text.length)
                    if (safeEnd > safeStart) {
                        listener.onUtteranceRange(text, safeStart, safeEnd)
                    }
                }

                override fun onDone(utteranceId: String?) {
                    utteranceId?.let(utteranceTexts::remove)
                    listener.onSpeakingChanged(false)
                }

                override fun onError(utteranceId: String?) {
                    utteranceId?.let(utteranceTexts::remove)
                    listener.onSpeakingChanged(false)
                    listener.onError("TTS playback failed")
                }
            },
        )

        ready.set(true)
        listener.onReady()

        pendingText?.let { queued ->
            pendingText = null
            speak(queued)
        }
    }

    fun isReady(): Boolean = ready.get()

    /** Package name of the Android TTS engine currently selected by the system. */
    fun currentEnginePackage(): String? = tts?.defaultEngine

    /**
     * Speaks locally through Android TTS. If the engine is still starting,
     * the most recent text is kept and spoken once initialization completes.
     */
    fun speak(text: String, flush: Boolean = true): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false

        val engine = tts
        if (engine == null || !ready.get()) {
            pendingText = clean
            return false
        }

        if (flush) utteranceTexts.clear()

        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val utteranceId = "ariana-${UUID.randomUUID()}"
        utteranceTexts[utteranceId] = clean
        val result = engine.speak(clean, queueMode, Bundle(), utteranceId)
        if (result != TextToSpeech.SUCCESS) utteranceTexts.remove(utteranceId)
        return result == TextToSpeech.SUCCESS
    }

    fun stop() {
        utteranceTexts.clear()
        tts?.stop()
        listener.onSpeakingChanged(false)
    }

    override fun close() {
        ready.set(false)
        pendingText = null
        utteranceTexts.clear()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private companion object {
        const val DEFAULT_RATE = 1.0f
        const val DEFAULT_PITCH = 1.0f
    }
}
