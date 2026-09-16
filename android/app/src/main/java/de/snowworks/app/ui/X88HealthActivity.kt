package de.snowworks.app.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.google.android.material.button.MaterialButton
import de.snowworks.ariana.ArianaDeviceApi
import de.snowworks.ariana.Feature
import de.snowworks.ariana.apk.ApkBridgeRouteSelfTest
import de.snowworks.ariana.apk.ApkHealthCheck
import de.snowworks.ariana.apk.ApkInstallSourceController
import de.snowworks.ariana.bridge.AiHealthReporter
import de.snowworks.ariana.bridge.AiProviderRecoveryProbe
import de.snowworks.ariana.bridge.AiProviderRecoverySupervisor
import de.snowworks.ariana.bridge.AiRouteStateStore
import de.snowworks.ariana.bridge.AiRouterMetricsStore
import de.snowworks.ariana.bridge.AiRouterTrendStore
import de.snowworks.ariana.bridge.BridgeSelfTest
import de.snowworks.ariana.bridge.CloudProviderRegistry
import de.snowworks.ariana.files.TreePermissionStore
import de.snowworks.ariana.notify.NotificationStore
import de.snowworks.ariana.presence.PresenceSignalController
import de.snowworks.ariana.thermal.ThermalSafetyController
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Local X-88 health/release-gate panel. */
class X88HealthActivity : AppCompatActivity() {

    private lateinit var output: TextView
    private lateinit var runButton: MaterialButton
    private lateinit var claudeProbeButton: MaterialButton
    private lateinit var metaProbeButton: MaterialButton
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ArianaX88Health").apply { isDaemon = true }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        refreshSummary()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#05070D"))
            setPadding(dp(18))
        }

        root.addView(text("ARIANA X-88", 12f, "#7E93A6"))
        root.addView(text("HEALTH · RELEASE GATE", 26f, "#EAF7FF"))
        root.addView(text("Lokale Bridge, Multi-KI-Routing, Recovery-Supervisor, Gate, Session-Module, APK-Pipeline und echte Android-Thermalwerte.", 12f, "#8398A8"))

        runButton = MaterialButton(this).apply {
            text = "Bridge + APK Self-Test starten"
            isAllCaps = false
            setOnClickListener { runSelfTest() }
        }
        root.addView(runButton)

        claudeProbeButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Claude jetzt testen"
            isAllCaps = false
            setOnClickListener { runProviderProbe(CloudProviderRegistry.Slot.CLAUDE) }
        }
        metaProbeButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Meta jetzt testen"
            isAllCaps = false
            setOnClickListener { runProviderProbe(CloudProviderRegistry.Slot.META) }
        }
        root.addView(claudeProbeButton)
        root.addView(metaProbeButton)

        root.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "KI-Provider öffnen"
            isAllCaps = false
            setOnClickListener { startActivity(Intent(this@X88HealthActivity, AiProviderSettingsActivity::class.java)) }
        })
        root.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "APK Manager öffnen"
            isAllCaps = false
            setOnClickListener { startActivity(Intent(this@X88HealthActivity, ApkStatusActivity::class.java)) }
        })
        root.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Status aktualisieren"
            isAllCaps = false
            setOnClickListener { refreshSummary() }
        })

        output = text("Noch kein Test.", 13f, "#DCEAF2").apply { setTextIsSelectable(true) }
        root.addView(output)

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#05070D"))
            addView(root)
        }
    }

    private fun runSelfTest() {
        runButton.isEnabled = false
        output.text = "Bridge + APK Self-Test läuft …"
        executor.execute {
            val result = BridgeSelfTest.run(applicationContext)
            val apk = ApkHealthCheck.run(applicationContext)
            val apkRoutes = ApkBridgeRouteSelfTest.run(applicationContext)
            val report = buildString {
                appendLine(result.message)
                appendLine()
                result.checks.forEachIndexed { index, check ->
                    val marker = when { check.skipped -> "SKIP"; check.ok -> "PASS"; else -> "FAIL" }
                    appendLine("${index + 1}. [$marker] ${check.name}")
                    appendLine("   ${check.detail}")
                }
                appendLine()
                appendLine(if (apk.ok) "APK HEALTH · PASS" else "APK HEALTH · ATTENTION")
                apk.checks.forEachIndexed { index, check ->
                    val marker = when { check.skipped -> "SKIP"; check.ok -> "PASS"; else -> "FAIL" }
                    appendLine("A${index + 1}. [$marker] ${check.name}")
                    appendLine("   ${check.detail}")
                }
                appendLine()
                appendLine(if (apkRoutes.ok) "APK ROUTES · PASS" else "APK ROUTES · FAIL")
                apkRoutes.checks.forEachIndexed { index, check ->
                    appendLine("R${index + 1}. [${if (check.ok) "PASS" else "FAIL"}] ${check.name}")
                    appendLine("   ${check.detail}")
                }
                appendLine()
                append(runtimeSummary())
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                output.text = report
                runButton.isEnabled = true
            }
        }
    }

    private fun runProviderProbe(slot: CloudProviderRegistry.Slot) {
        val button = if (slot == CloudProviderRegistry.Slot.CLAUDE) claudeProbeButton else metaProbeButton
        if (!button.isEnabled) return
        button.isEnabled = false
        button.text = "${slot.name} wird getestet …"
        executor.execute {
            val result = AiProviderRecoveryProbe.run(applicationContext, slot)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                button.isEnabled = true
                button.text = if (slot == CloudProviderRegistry.Slot.CLAUDE) "Claude jetzt testen" else "Meta jetzt testen"
                val prefix = if (result.ok) "${result.engine} · TEST OK" else "${result.engine} · TEST FEHLER · ${result.error ?: "UNKNOWN"}"
                output.text = "$prefix\n\n${runtimeSummary()}"
            }
        }
    }

    private fun refreshSummary() { output.text = runtimeSummary() }

    private fun runtimeSummary(): String {
        val api = ArianaDeviceApi(this)
        val connection = api.getConnection()
        val thermal = ThermalSafetyController.currentSnapshot()
        val ai = AiHealthReporter.snapshot(this)
        val routeHistory = AiRouteStateStore.history(this).takeLast(6).asReversed()
        val metrics = AiRouterMetricsStore.snapshot(this)
        val trends = AiRouterTrendStore.snapshot(this)
        val supervisor = AiProviderRecoverySupervisor.status(this)
        val tree = TreePermissionStore(this).get()
        val notifications = NotificationStore.listRecent()
        val tracked = listOf(Feature.CAMERA, Feature.MICROPHONE, Feature.SCREEN, Feature.NOTIFY_READ, Feature.FILES, Feature.LOCATION, Feature.BLUETOOTH)

        return buildString {
            appendLine("RUNTIME SNAPSHOT")
            appendLine("Zeit: ${DateFormat.getDateTimeInstance().format(Date())}")
            appendLine("Master: ${if (api.isMasterEnabled()) "ON" else "OFF"}")
            appendLine("Stop-All blockiert: ${if (api.isBlocked()) "JA" else "NEIN"}")
            appendLine("Bridge: ${connection.label}")
            appendLine("Bridge Detail: ${connection.detail}")
            appendLine("APK-Installationsquelle: ${if (ApkInstallSourceController.isReady(this@X88HealthActivity)) "READY" else "FREIGABE NÖTIG"}")
            appendLine()
            appendLine("AI ROUTER HEALTH")
            appendLine("LOCAL: ${ai.activeLocalProviderId ?: "nicht aktiv"}")
            appendLine(providerLine(ai.claude))
            appendLine(providerLine(ai.meta))
            appendLine("Letzte Route: ${ai.route.engine} · ${ai.route.taskClass}${if (ai.route.fallbackUsed) " · FALLBACK" else ""}")
            ai.route.fallbackReason?.let { appendLine("Fallback-Ursache: $it") }
            appendLine("Route-Zeit: ${formatTime(ai.route.updatedAtMs)}")
            appendLine()
            appendLine("ROUTER METRICS · LIFETIME")
            appendLine("Gesamt: ${metrics.total} · LOCAL=${metrics.local} · CLAUDE=${metrics.claude} · META=${metrics.meta} · MULTI=${metrics.multi}")
            appendLine("Fallbacks: ${metrics.fallbacks} (${formatPercent(metrics.fallbackRatePercent)})")
            appendLine("Fehler: ${metrics.errors} (${formatPercent(metrics.errorRatePercent)})")
            appendLine()
            appendLine("ROUTER TRENDS")
            appendLine(trendLine("24h", trends.last24Hours))
            appendLine(trendLine("7d", trends.last7Days))
            appendLine()
            appendLine("ROUTER HISTORY")
            if (routeHistory.isEmpty()) {
                appendLine("noch keine Zustandswechsel")
            } else {
                routeHistory.forEach { entry ->
                    appendLine(buildString {
                        append(formatTime(entry.updatedAtMs)).append(" · ")
                        append(entry.engine).append(" · ").append(entry.taskClass)
                        if (entry.fallbackUsed) append(" · FALLBACK")
                        entry.fallbackReason?.let { append(" · reason=").append(it) }
                    })
                }
            }
            appendLine()
            appendLine("AI RECOVERY SUPERVISOR")
            appendLine("Status: ${if (supervisor.running) "RUNNING" else "STOPPED"} · tick=${supervisor.tickSeconds}s · min-gap=${supervisor.minProbeIntervalMs / 1000L}s")
            appendLine(supervisorLine(supervisor.claude))
            appendLine(supervisorLine(supervisor.meta))
            appendLine()
            appendLine("THERMAL SAFETY")
            appendLine("Android Thermal API: ${if (thermal.supported) "AKTIV" else "NICHT UNTERSTÜTZT"}")
            appendLine("Status: ${thermal.label} (${thermal.status})")
            appendLine("Lokale KI-Inferenz: ${if (thermal.localInferenceAllowed) "ERLAUBT" else "GEDROSSELT"}")
            appendLine("Capture-Hardware: ${if (thermal.captureAllowed) "ERLAUBT" else "GESTOPPT"}")
            appendLine("Master einschaltbar: ${if (thermal.masterEnableAllowed) "JA" else "NEIN"}")
            appendLine()
            appendLine("Presence: ${PresenceSignalController.currentState()}")
            appendLine("NotificationStore RAM-Einträge: ${notifications.size}")
            appendLine("SAF-Dateibaum: ${tree?.toString() ?: "nicht gewählt"}")
            appendLine()
            tracked.forEach { feature ->
                val status = api.getStatus(feature)
                appendLine("${feature.title}: permission=${status.permissionGranted} · active=${status.sessionActive} · ${status.permissionLabel}")
            }
        }
    }

    private fun trendLine(label: String, window: AiRouterTrendStore.Window): String = buildString {
        append(label).append(": total=").append(window.total)
        append(" · L=").append(window.local)
        append(" · C=").append(window.claude)
        append(" · M=").append(window.meta)
        append(" · X=").append(window.multi)
        append(" · fallback=").append(formatPercent(window.fallbackRatePercent))
        append(" · error=").append(formatPercent(window.errorRatePercent))
    }

    private fun providerLine(status: AiHealthReporter.ProviderStatus): String {
        if (!status.configured) return "${status.engine}: NICHT KONFIGURIERT"
        val state = when {
            status.circuitOpen -> "COOLDOWN ${maxOf(1L, status.cooldownRemainingMs / 1000L)}s"
            status.halfOpenProbeInFlight -> "RECOVERY PROBE"
            status.recoveryProbeReady -> "RECOVERY READY"
            status.consecutiveFailures > 0 -> "ATTENTION"
            else -> "HEALTHY"
        }
        return buildString {
            append(status.engine).append(": ").append(state)
            status.model?.let { append(" · ").append(it) }
            append(" · failures=").append(status.consecutiveFailures)
            status.lastError?.let { append(" · last=").append(it) }
        }
    }

    private fun supervisorLine(status: AiProviderRecoverySupervisor.SlotTelemetry): String = buildString {
        append(status.engine).append(": last=").append(formatTime(status.lastAttemptWallMs))
        append(" · success=").append(formatTime(status.lastSuccessWallMs))
        status.lastError?.let { append(" · error=").append(it) }
        append(" · next=").append(formatTime(status.nextEligibleWallMs))
    }

    private fun formatPercent(value: Double): String = String.format(Locale.US, "%.1f%%", value)

    private fun formatTime(value: Long): String = if (value > 0L) DateFormat.getDateTimeInstance().format(Date(value)) else "noch keine"

    private fun text(value: String, sizeSp: Float, colorHex: String): TextView = TextView(this).apply {
        text = value
        textSize = sizeSp
        setTextColor(Color.parseColor(colorHex))
        setPadding(0, dp(5), 0, dp(5))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
