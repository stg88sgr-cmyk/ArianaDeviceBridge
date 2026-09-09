package de.snowworks.ariana.presence

import java.util.concurrent.atomic.AtomicReference

/**
 * Process-local read-only snapshot of Ariana Presence health.
 *
 * The local loopback bridge can expose this without owning the TTS engine or
 * avatar renderer. This keeps Presence lifecycle inside its controller while
 * making real-device state observable to trusted local clients.
 */
object PresenceTelemetryStore {
    private val latest = AtomicReference<PresenceDiagnostics?>(null)

    fun publish(snapshot: PresenceDiagnostics) {
        latest.set(snapshot)
    }

    fun snapshot(): PresenceDiagnostics? = latest.get()

    fun clear() {
        latest.set(null)
    }
}
