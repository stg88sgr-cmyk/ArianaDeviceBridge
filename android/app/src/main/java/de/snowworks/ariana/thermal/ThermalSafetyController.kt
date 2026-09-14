package de.snowworks.ariana.thermal

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import de.snowworks.ariana.ArianaDeviceApi
import de.snowworks.ariana.bridge.LocalAiProviderManager
import de.snowworks.ariana.session.ArianaCaptureService
import de.snowworks.ariana.session.SessionRegistry

/**
 * Process-wide thermal safety controller for ARIANA X-88.
 *
 * Android's PowerManager thermal status is the source of truth on API 29+.
 * The controller never invents a CPU percentage or temperature value.
 *
 * Policy:
 *  - NONE/LIGHT: normal operation
 *  - MODERATE: block/close on-device LLM inference
 *  - SEVERE: stop and block capture hardware until the device cools
 *  - CRITICAL/EMERGENCY/SHUTDOWN: true Stop-All, bridge off, master latched blocked
 *
 * The critical latch is not automatically cleared when the device cools. The user
 * must explicitly enable the master again after thermal status is below CRITICAL.
 */
object ThermalSafetyController {

    data class Snapshot(
        val supported: Boolean,
        val status: Int,
        val label: String,
        val localInferenceAllowed: Boolean,
        val captureAllowed: Boolean,
        val masterEnableAllowed: Boolean,
        val updatedAtMs: Long,
    )

    @Volatile
    private var snapshot = Snapshot(
        supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
        status = PowerManager.THERMAL_STATUS_NONE,
        label = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "UNBEKANNT" else "NICHT UNTERSTÜTZT",
        localInferenceAllowed = true,
        captureAllowed = true,
        masterEnableAllowed = true,
        updatedAtMs = 0L,
    )

    @Volatile private var started = false
    @Volatile private var criticalStopTriggered = false
    @Volatile private var appContext: Context? = null
    private var powerManager: PowerManager? = null
    private var listener: PowerManager.OnThermalStatusChangedListener? = null

    @Synchronized
    fun start(context: Context) {
        if (started) return

        val app = context.applicationContext
        appContext = app

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            snapshot = snapshot.copy(
                supported = false,
                label = "NICHT UNTERSTÜTZT",
                updatedAtMs = System.currentTimeMillis(),
            )
            started = true
            return
        }

        val pm = app.getSystemService(PowerManager::class.java)
        powerManager = pm

        val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
            handleStatus(status)
        }
        listener = thermalListener

        handleStatus(pm.currentThermalStatus)
        pm.addThermalStatusListener(app.mainExecutor, thermalListener)
        started = true
    }

    @Synchronized
    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = powerManager
            val currentListener = listener
            if (pm != null && currentListener != null) {
                runCatching { pm.removeThermalStatusListener(currentListener) }
            }
        }
        listener = null
        powerManager = null
        appContext = null
        started = false
    }

    fun currentSnapshot(): Snapshot = snapshot

    fun allowsLocalInference(): Boolean = snapshot.localInferenceAllowed

    fun allowsCapture(): Boolean = snapshot.captureAllowed

    fun allowsMasterEnable(): Boolean = snapshot.masterEnableAllowed

    private fun handleStatus(status: Int) {
        val app = appContext
        val normalized = status.coerceIn(
            PowerManager.THERMAL_STATUS_NONE,
            PowerManager.THERMAL_STATUS_SHUTDOWN,
        )

        val localAllowed = normalized < PowerManager.THERMAL_STATUS_MODERATE
        val captureAllowed = normalized < PowerManager.THERMAL_STATUS_SEVERE
        val masterAllowed = normalized < PowerManager.THERMAL_STATUS_CRITICAL

        snapshot = Snapshot(
            supported = true,
            status = normalized,
            label = labelFor(normalized),
            localInferenceAllowed = localAllowed,
            captureAllowed = captureAllowed,
            masterEnableAllowed = masterAllowed,
            updatedAtMs = System.currentTimeMillis(),
        )

        Log.i(TAG, "Thermal status=${labelFor(normalized)} ($normalized)")

        if (app == null) return

        if (normalized >= PowerManager.THERMAL_STATUS_MODERATE) {
            runCatching { LocalAiProviderManager.closeRuntime() }
                .onFailure { Log.e(TAG, "Failed to close local AI runtime", it) }
        }

        if (normalized >= PowerManager.THERMAL_STATUS_SEVERE) {
            // Hardware capture is the first runtime load shed. This does not latch
            // the master or stop the bridge yet; cooling below SEVERE allows a new
            // explicitly requested capture session.
            runCatching { ArianaCaptureService.stopAll(app) }
                .onFailure { Log.e(TAG, "Failed to stop capture service", it) }
            runCatching { SessionRegistry.clearCaptureFeatures() }
                .onFailure { Log.e(TAG, "Failed to clear capture registry", it) }
        }

        if (normalized >= PowerManager.THERMAL_STATUS_CRITICAL) {
            if (!criticalStopTriggered) {
                criticalStopTriggered = true
                Log.e(TAG, "CRITICAL thermal state: executing Stop-All latch")
                runCatching { ArianaDeviceApi(app).stopAll() }
                    .onFailure { Log.e(TAG, "Thermal Stop-All failed", it) }
            }
        } else {
            // Reset only the internal edge detector. ArianaGate's Stop-All block remains
            // latched until the user explicitly enables the master again.
            criticalStopTriggered = false
        }
    }

    private fun labelFor(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "NORMAL"
        PowerManager.THERMAL_STATUS_LIGHT -> "LEICHT"
        PowerManager.THERMAL_STATUS_MODERATE -> "MODERAT"
        PowerManager.THERMAL_STATUS_SEVERE -> "STARK"
        PowerManager.THERMAL_STATUS_CRITICAL -> "KRITISCH"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "NOTFALL"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "ABSCHALTUNG"
        else -> "UNBEKANNT"
    }

    private const val TAG = "X88ThermalSafety"
}
