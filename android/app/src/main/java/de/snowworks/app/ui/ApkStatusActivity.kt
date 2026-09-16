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
import de.snowworks.ariana.apk.ApkBridgeStatus
import de.snowworks.ariana.apk.ApkHealthCheck
import de.snowworks.ariana.apk.ApkInstallSourceController
import org.json.JSONObject
import java.util.concurrent.Executors

class ApkStatusActivity : AppCompatActivity() {
    private lateinit var statusView: TextView
    private lateinit var healthView: TextView
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ArianaApkStatus").apply { isDaemon = true }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::statusView.isInitialized) refresh()
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
        root.addView(text("APK MANAGER", 26f, "#EAF7FF"))
        root.addView(text("Download · Signatur · Trust · PackageInstaller", 12f, "#8398A8"))

        statusView = text("Status wird gelesen …", 13f, "#DCEAF2").apply { setTextIsSelectable(true) }
        healthView = text("Health noch nicht geprüft.", 13f, "#DCEAF2").apply { setTextIsSelectable(true) }

        root.addView(MaterialButton(this).apply {
            text = "APK-Status aktualisieren"
            isAllCaps = false
            setOnClickListener { refresh() }
        })
        root.addView(MaterialButton(this).apply {
            text = "APK Health Self-Test"
            isAllCaps = false
            setOnClickListener { runHealth() }
        })
        root.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Installationsquelle freigeben"
            isAllCaps = false
            setOnClickListener {
                val intent = ApkInstallSourceController.createSettingsIntent(this@ApkStatusActivity)
                if (intent != null) startActivity(intent) else refresh()
            }
        })
        root.addView(statusView)
        root.addView(healthView)

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#05070D"))
            addView(root)
        }
    }

    private fun refresh() {
        executor.execute {
            val status = ApkBridgeStatus.status(applicationContext)
            val inspection = ApkBridgeStatus.inspectLatest(applicationContext)
            val report = buildString {
                appendLine("APK STATUS")
                appendLine("Installationsquelle: ${if (status.optBoolean("canRequestPackageInstalls")) "READY" else "FREIGABE NÖTIG"}")
                appendLine()
                appendLine("Letzter Installationslauf:")
                appendLine(pretty(status.optJSONObject("lastInstall")))
                appendLine()
                appendLine("Neueste APK:")
                appendLine(pretty(status.optJSONObject("latestDownload")))
                appendLine()
                appendLine("Prüfung:")
                appendLine(inspection.toString(2))
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) statusView.text = report
            }
        }
    }

    private fun runHealth() {
        healthView.text = "APK Health läuft …"
        executor.execute {
            val result = ApkHealthCheck.run(applicationContext)
            val report = buildString {
                appendLine(if (result.ok) "APK HEALTH · PASS" else "APK HEALTH · ATTENTION")
                result.checks.forEachIndexed { index, check ->
                    val marker = when {
                        check.skipped -> "SKIP"
                        check.ok -> "PASS"
                        else -> "FAIL"
                    }
                    appendLine("${index + 1}. [$marker] ${check.name}")
                    appendLine("   ${check.detail}")
                }
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) healthView.text = report
            }
        }
    }

    private fun pretty(value: JSONObject?): String = value?.toString(2) ?: "nicht vorhanden"

    private fun text(value: String, sizeSp: Float, colorHex: String): TextView = TextView(this).apply {
        text = value
        textSize = sizeSp
        setTextColor(Color.parseColor(colorHex))
        setPadding(0, dp(5), 0, dp(5))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
