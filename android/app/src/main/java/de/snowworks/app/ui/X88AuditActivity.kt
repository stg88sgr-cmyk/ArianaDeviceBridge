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
import de.snowworks.ariana.bridge.ActionApprovalStore
import de.snowworks.ariana.notify.NotificationStore
import de.snowworks.ariana.presence.PresenceSignalController
import de.snowworks.ariana.session.SessionRegistry
import java.text.DateFormat
import java.util.Date

/**
 * Read-only runtime audit view.
 *
 * Shows control metadata only. It does not render notification text,
 * transcripts, dialogue prompts, or file contents.
 */
class X88AuditActivity : AppCompatActivity() {

    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::output.isInitialized) refresh()
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#05070D"))
            setPadding(dp(18))
        }

        root.addView(text("ARIANA X-88", 12f, "#7E93A6"))
        root.addView(text("AUDIT · LOCAL RUNTIME", 26f, "#EAF7FF"))
        root.addView(
            text(
                "Nur lokale Steuerungsmetadaten. Keine Gesprächsinhalte.",
                12f,
                "#8398A8",
            ),
        )

        root.addView(
            MaterialButton(this).apply {
                text = "Aktualisieren"
                isAllCaps = false
                setOnClickListener { refresh() }
            },
        )

        output = text("", 12f, "#DCEAF2").apply {
            setTextIsSelectable(true)
        }
        root.addView(output)

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#05070D"))
            addView(root)
        }
    }

    private fun refresh() {
        val api = ArianaDeviceApi(this)
        val active = SessionRegistry.snapshot().sortedBy { it.id }
        val pending = ActionApprovalStore.listPending()
        val journal = X88EventJournal.snapshot()
        val notificationCount = NotificationStore.listRecent().size

        output.text = buildString {
            appendLine("RUNTIME")
            appendLine("Zeit: ${DateFormat.getDateTimeInstance().format(Date())}")
            appendLine("Master: ${if (api.isMasterEnabled()) "ON" else "OFF"}")
            appendLine("Stop-All blockiert: ${if (api.isBlocked()) "JA" else "NEIN"}")
            appendLine("Bridge: ${api.getConnection().label}")
            appendLine("Presence: ${PresenceSignalController.currentState()}")
            appendLine("NotificationStore count: $notificationCount")
            appendLine()

            appendLine("ACTIVE SESSIONS")
            if (active.isEmpty()) {
                appendLine("• keine")
            } else {
                active.forEach { feature ->
                    appendLine("• ${feature.id} · ${feature.title}")
                }
            }
            appendLine()

            appendLine("PENDING APPROVALS")
            appendLine("pending=${ActionApprovalStore.pendingCount()} · approved=${ActionApprovalStore.approvedCount()}")
            if (pending.isEmpty()) {
                appendLine("• keine")
            } else {
                pending.forEach { proposal ->
                    val ttl = ((proposal.expiresAtMs - System.currentTimeMillis()) / 1000L)
                        .coerceAtLeast(0L)
                    appendLine("• ${proposal.action} · ${ttl}s · ${proposal.reason}")
                }
            }
            appendLine()

            appendLine("X-88 EVENT JOURNAL")
            if (journal.isEmpty()) {
                appendLine("• noch leer")
            } else {
                journal.take(60).forEach { entry ->
                    val time = DateFormat.getTimeInstance().format(Date(entry.timestampMs))
                    val detail = if (entry.detail.isBlank()) "" else " · ${entry.detail}"
                    appendLine("• $time · ${entry.kind}$detail")
                }
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
