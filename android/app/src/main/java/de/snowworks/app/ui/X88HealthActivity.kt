package de.snowworks.app.ui

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
import de.snowworks.ariana.bridge.BridgeSelfTest
import de.snowworks.ariana.files.TreePermissionStore
import de.snowworks.ariana.notify.NotificationStore
import de.snowworks.ariana.presence.PresenceSignalController
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
        root.addView(text("Lokale Bridge, Gate, Session-Module und Berechtigungsoberfläche.", 12f, "#8398A8"))

        runButton = MaterialButton(this).apply {
            text = "Bridge Self-Test starten"
            isAllCaps = false
            setOnClickListener { runSelfTest() }
        }
        root.addView(runButton)

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
        output.text = "Bridge Self-Test läuft …"

        executor.execute {
            val result = BridgeSelfTest.run(applicationContext)
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

    private fun text(value: String, sizeSp: Float, colorHex: String): TextView =
        TextView(this).apply {
            text = value
            textSize = sizeSp
            setTextColor(Color.parseColor(colorHex))
            setPadding(0, dp(5), 0, dp(5))
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
