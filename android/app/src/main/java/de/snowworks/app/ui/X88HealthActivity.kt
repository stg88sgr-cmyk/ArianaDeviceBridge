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
import de.snowworks.ariana.bridge.BridgeSelfTest
import de.snowworks.ariana.files.TreePermissionStore
import de.snowworks.ariana.notify.NotificationStore
import de.snowworks.ariana.presence.PresenceSignalController
import de.snowworks.ariana.thermal.ThermalSafetyController
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/** Local X-88 health/release-gate panel. */
class X88HealthActivity : AppCompatActivity() {

    private lateinit var output: TextView
    private lateinit var runButton: MaterialButton
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
        root.addView(text("Lokale Bridge, Multi-KI-Routing, Gate, Session-Module, APK-Pipeline und echte Android-Thermalwerte.", 12f, "#8398A8"))

        runButton = MaterialButton(this).apply {
            text = "Bridge + APK Self-Test starten"
            isAllCaps = false
            setOnClickListener { runSelfTest() }
        }
        root.addView(runButton)

        root.addView(
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "KI-Provider öffnen"
                isAllCaps = false
                setOnClickListener { startActivity(Intent(this@X88HealthActivity, AiProviderSettingsActivity::class.java)) }
            },
        )

        root.addView(
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "APK Manager öffnen"
                isAllCaps = false
                setOnClickListener { startActivity(Intent(this@X88HealthActivity, ApkStatusActivity::class.java)) }
            },
        )

        root.addView(
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Status aktualisieren"
                isAllCaps = false
                setOnClickListener { refreshSummary() }
            },
        )

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
                    val marker = when {
                        check.skipped -> "SKIP"
                        check.ok -> "PASS"
                        else -> "FAIL"
                    }
                    appendLine("${index + 1}. [$marker] ${check.name}")
                    appendLine("   ${check.detail}")
                }
                appendLine()
                appendLine(if (apk.ok) "APK HEALTH · PASS" else "APK HEALTH · ATTENTION")
                apk.checks.forEachIndexed { index, check ->
                    val marker = when {
                        check.skipped -> "SKIP"
                        check.ok -> "PASS"
                        else -> "FAIL"
                    }
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

    private fun refreshSummary() {
        output.text = runtimeSummary()
    }

    private fun runtimeSummary(): String {
        val api = ArianaDeviceApi(this)
        val connection = api.getConnection()
        val thermal = ThermalSafetyController.currentSnapshot()
        val ai = AiHealthReporter.snapshot(this)
        val tree = TreePermissionStore(this).get()
        val notifications = NotificationStore.listRecent()
        val tracked = listOf(
            Feature.CAMERA,
            Feature.MICROPHONE,
            Feature.SCREEN,
            Feature.NOTIFY_READ,
            Feature.FILES,
            Feature.LOCATION,
            Feature.BLUETOOTH,
        )

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
            appendLine("Route-Zeit: ${if (ai.route.updatedAtMs > 0L) DateFormat.getDateTimeInstance().format(Date(ai.route.updatedAtMs)) else "noch keine"}")
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

    private fun providerLine(status: AiHealthReporter.ProviderStatus): String {
        if (!status.configured) return "${status.engine}: NICHT KONFIGURIERT"
        val state = when {
            status.circuitOpen -> "CIRCUIT OPEN"
            status.halfOpenProbeInFlight -> "HALF-OPEN PROBE"
            status.consecutiveFailures > 0 -> "ATTENTION"
            else -> "READY"
        }
        return buildString {
            append(status.engine).append(": ").append(state)
            status.model?.let { append(" · ").append(it) }
            append(" · failures=").append(status.consecutiveFailures)
            status.lastError?.let { append(" · last=").append(it) }
        }
    }

    private fun text(value: String, sizeSp: Float, colorHex: String): TextView =
        TextView(this).apply {
            text = value
            textSize = sizeSp
            setTextColor(Color.parseColor(colorHex))
            setPadding(0, dp(5), 0, dp(5))
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
