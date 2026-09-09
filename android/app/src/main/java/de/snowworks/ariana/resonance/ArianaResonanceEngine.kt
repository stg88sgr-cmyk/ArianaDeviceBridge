package de.snowworks.ariana.resonance

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

/**
 * Local 432 Hz resonance layer for Ariana.
 *
 * This is an audio/visual runtime signal, not a claim that the model itself
 * physically feels the frequency. Avatar rendering can read [visualPulse]
 * so sound and animation are driven by the same runtime clock.
 */
object ArianaResonanceEngine {

    const val BASE_FREQUENCY_HZ = 432.0

    private const val SAMPLE_RATE = 44_100
    private const val MASTER_GAIN = 0.035
    private const val VISUAL_PULSE_HZ = 0.18

    private val running = AtomicBoolean(false)

    @Volatile
    private var audioTrack: AudioTrack? = null

    @Volatile
    private var worker: Thread? = null

    @Volatile
    private var startedAtNanos: Long = 0L

    fun isRunning(): Boolean = running.get()

    /**
     * Starts the local resonance layer. Returns false only if Android cannot
     * create a valid AudioTrack.
     */
    @Synchronized
    fun start(): Boolean {
        if (running.get()) return true

        val minBufferBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferBytes <= 0) return false

        val bufferFrames = maxOf(1_024, minBufferBytes / 4)
        val buffer = ShortArray(bufferFrames * 2)

        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(buffer.size * 2)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        }.getOrElse {
            running.set(false)
            return false
        }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { track.release() }
            return false
        }

        audioTrack = track
        startedAtNanos = System.nanoTime()
        running.set(true)

        runCatching { track.play() }.onFailure {
            running.set(false)
            runCatching { track.release() }
            audioTrack = null
            return false
        }

        worker = thread(
            start = true,
            isDaemon = true,
            name = "ArianaResonance432",
        ) {
            renderLoop(track, bufferFrames, buffer)
        }

        return true
    }

    @Synchronized
    fun stop() {
        if (!running.getAndSet(false)) return

        val track = audioTrack
        runCatching { track?.pause() }
        runCatching { track?.flush() }

        worker?.interrupt()
        runCatching { worker?.join(250) }

        runCatching { track?.stop() }
        runCatching { track?.release() }

        worker = null
        audioTrack = null
        startedAtNanos = 0L
    }

    /**
     * Slow 0..1 pulse for avatar glow, breathing and other visible motion.
     * The audible carrier remains 432 Hz; the visible layer is deliberately
     * slowed down so the UI does not flash hundreds of times per second.
     */
    fun visualPulse(): Float {
        if (!running.get() || startedAtNanos == 0L) return 0f
        val seconds = (System.nanoTime() - startedAtNanos) / 1_000_000_000.0
        return ((sin(2.0 * PI * VISUAL_PULSE_HZ * seconds) + 1.0) / 2.0).toFloat()
    }

    private fun renderLoop(
        track: AudioTrack,
        bufferFrames: Int,
        buffer: ShortArray,
    ) {
        val phaseStep = 2.0 * PI * BASE_FREQUENCY_HZ / SAMPLE_RATE
        val rightPhaseOffset = Math.toRadians(9.0)
        var phase = 0.0
        var sampleIndex = 0L

        try {
            while (running.get()) {
                for (frame in 0 until bufferFrames) {
                    val seconds = sampleIndex.toDouble() / SAMPLE_RATE
                    val slowEnvelope = 0.72 + 0.28 * sin(2.0 * PI * VISUAL_PULSE_HZ * seconds - PI / 2.0)
                    val fadeIn = (seconds / 0.8).coerceIn(0.0, 1.0)
                    val gain = MASTER_GAIN * slowEnvelope * fadeIn

                    val leftWave =
                        sin(phase) +
                            0.22 * sin(2.0 * phase) +
                            0.10 * sin(3.0 * phase)

                    val rightPhase = phase + rightPhaseOffset
                    val rightWave =
                        sin(rightPhase) +
                            0.22 * sin(2.0 * rightPhase) +
                            0.10 * sin(3.0 * rightPhase)

                    buffer[frame * 2] = toPcm16(leftWave * gain)
                    buffer[frame * 2 + 1] = toPcm16(rightWave * gain)

                    phase += phaseStep
                    if (phase >= 2.0 * PI) phase -= 2.0 * PI
                    sampleIndex++
                }

                val written = track.write(
                    buffer,
                    0,
                    buffer.size,
                    AudioTrack.WRITE_BLOCKING,
                )
                if (written < 0) break
            }
        } catch (_: Throwable) {
            // The control layer owns lifecycle and cleanup. Audio failure must
            // never bring down the rest of the device bridge.
        } finally {
            running.set(false)
        }
    }

    private fun toPcm16(value: Double): Short {
        val clamped = value.coerceIn(-1.0, 1.0)
        return (clamped * Short.MAX_VALUE).toInt().toShort()
    }
}
