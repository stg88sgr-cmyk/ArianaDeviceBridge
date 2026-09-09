package de.snowworks.app.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.setPadding
import com.google.android.material.button.MaterialButton
import de.snowworks.ariana.core.AuditLog
import de.snowworks.ariana.core.MemoryVault
import de.snowworks.ariana.core.NetworkGate

/**
 * Visible control surface for the local X-Ariana core.
 *
 * Nothing here silently enables external network access. The external switch
 * starts OFF, the allow-list starts empty, and enabling the switch still does
 * not permit a host until that host is explicitly allow-listed.
 */
class CoreControlActivity : AppCompatActivity() {

    private lateinit var memoryVault: MemoryVault
    private lateinit var networkGate: NetworkGate
    private lateinit var auditLog: AuditLog

    private var networkSwitch: SwitchCompat? = null
    private var memoryStatus: TextView? = null
    private var networkStatus: TextView? = null
    private var hostsStatus: TextView? = null
    private var auditStatus: TextView? = null
    private var suppressNetworkCallback = false

    private val exportMemoryLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                    writer.write(memoryVault.exportPlaintextJson())
                } ?: error("Datei konnte nicht geöffnet werden.")
            }.onSuccess {
                auditLog.append(
                    AuditLog.Event(
                        category = "memory",
                        action = "export",
                        decision = "EXPORTED",
                        reason = "Explicit user export",
                    ),
                )
                toast("Memory-Export gespeichert.")
                refreshCoreStatus()
            }.onFailure { error ->
                toast("Export fehlgeschlagen: ${error.message ?: "Unbekannter Fehler"}")
            }
        }

    private val exportAuditLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                    writer.write(auditLog.exportJsonLines())
                } ?: error("Datei konnte nicht geöffnet werden.")
            }.onSuccess {
                toast("Audit-Log gespeichert.")
            }.onFailure { error ->
                toast("Export fehlgeschlagen: ${error.message ?: "Unbekannter Fehler"}")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        memoryVault = MemoryVault(this)
        networkGate = NetworkGate(this)
        auditLog = AuditLog(this)
        setContentView(buildUi())
        refreshCoreStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshCoreStatus()
    }

    private fun buildUi(): ScrollView {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0F12"))
            setPadding(dp(20))
        }

        root.addView(text("X-ARIANA", 12f, Color.parseColor("#8A9AA6")))
        root.addView(text("Core Control", 28f, Color.parseColor("#E9EEF1")))
        root.addView(
            text(
                "Lokaler Speicher, Netzwerk-Gate und Prüfprotokoll. Externer Zugriff startet gesperrt.",
                14f,
                Color.parseColor("#8A9AA6"),
            ),
        )

        val statusCard = card(::dp)
        statusCard.addView(text("Status", 18f, Color.parseColor("#E9EEF1")))
        memoryStatus = text("Memory: …", 14f, Color.parseColor("#C5D4DC")).also(statusCard::addView)
        networkStatus = text("Externes Netzwerk: …", 14f, Color.parseColor("#C5D4DC")).also(statusCard::addView)
        hostsStatus = text("Freigegebene Hosts: …", 14f, Color.parseColor("#C5D4DC")).also(statusCard::addView)
        auditStatus = text("Audit: …", 14f, Color.parseColor("#C5D4DC")).also(statusCard::addView)
        root.addView(statusCard)

        val networkCard = card(::dp)
        networkCard.addView(text("Network Gate", 18f, Color.parseColor("#E9EEF1")))
        networkCard.addView(
            text(
                "Deny-by-default innerhalb der X-Ariana-App. Nur explizit freigegebene HTTPS-Hosts dürfen nach außen.",
                14f,
                Color.parseColor("#8A9AA6"),
            ),
        )

        val switchRow = row()
        switchRow.addView(
            text("Externen Zugriff erlauben", 16f, Color.parseColor("#E9EEF1")).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            },
        )
        networkSwitch = SwitchCompat(this).apply {
            isChecked = networkGate.isExternalAccessEnabled
            setOnCheckedChangeListener { _, enabled ->
                if (suppressNetworkCallback) return@setOnCheckedChangeListener
                if (enabled) confirmEnableExternalNetwork() else {
                    networkGate.setExternalAccessEnabled(false)
                    toast("Externer Zugriff gesperrt.")
                    refreshCoreStatus()
                }
            }
        }
        switchRow.addView(networkSwitch)
        networkCard.addView(switchRow)

        networkCard.addView(
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Host freigeben"
                setOnClickListener { showAllowHostDialog() }
            },
        )
        networkCard.addView(
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Freigegebenen Host entfernen"
                setOnClickListener { showRemoveHostDialog() }
            },
        )
        networkCard.addView(
            MaterialButton(this).apply {
                text = "Alle externen Hosts sperren"
                setBackgroundColor(Color.parseColor("#C45C4A"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    networkGate.setExternalAccessEnabled(false)
                    networkGate.clearExternalHosts()
                    toast("Externes Netzwerk und Allowlist gesperrt.")
                    refreshCoreStatus()
                }
            },
        )
        root.addView(networkCard)

        val memoryCard = card(::dp)
        memoryCard.addView(text("Memory Vault", 18f, Color.parseColor("#E9EEF1")))
        memoryCard.addView(
            text(
                "Memory liegt verschlüsselt im privaten App-Speicher. Ein Export ist bewusst und sichtbar.",
                14f,
                Color.parseColor("#8A9AA6"),
            ),
        )
        memoryCard.addView(
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Memory exportieren"
                setOnClickListener { exportMemoryLauncher.launch("x-ariana-memory.json") }
            },
        )
        root.addView(memoryCard)

        val auditCard = card(::dp)
        auditCard.addView(text("Audit Log", 18f, Color.parseColor("#E9EEF1")))
        auditCard.addView(
            text(
                "Zeigt Entscheidungen und Ziele, aber speichert keine Nachrichteninhalte im Netzwerkprotokoll.",
                14f,
                Color.parseColor("#8A9AA6"),
            ),
        )
        auditCard.addView(
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Letzte Audit-Einträge anzeigen"
                setOnClickListener { showAuditDialog() }
            },
        )
        auditCard.addView(
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Audit-Log exportieren"
                setOnClickListener { exportAuditLauncher.launch("x-ariana-audit.jsonl") }
            },
        )
        root.addView(auditCard)

        val boundaryCard = card(::dp)
        boundaryCard.addView(text("Sicherheitsgrenze", 18f, Color.parseColor("#E9EEF1")))
        boundaryCard.addView(
            text(
                "Wichtig: Diese Stufe ist noch keine Android-weite Firewall. Sie schützt nur Netzwerkaufrufe, die durch das X-Ariana NetworkGate geführt werden. Eine VPN-/OS-Firewall ist eine separate spätere Stufe.",
                14f,
                Color.parseColor("#E0B66A"),
            ),
        )
        root.addView(boundaryCard)

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F12"))
            addView(root)
        }
    }

    private fun confirmEnableExternalNetwork() {
        AlertDialog.Builder(this)
            .setTitle("Externen Zugriff aktivieren?")
            .setMessage(
                "Damit wird nur der Network-Gate-Master eingeschaltet. Ohne freigegebene Hosts bleibt externer Verkehr weiterhin blockiert.",
            )
            .setNegativeButton("Abbrechen") { _, _ -> setNetworkSwitchSilently(false) }
            .setPositiveButton("Aktivieren") { _, _ ->
                networkGate.setExternalAccessEnabled(true)
                toast("Network Gate aktiviert. Nur Allowlist-Hosts dürfen nach außen.")
                refreshCoreStatus()
            }
            .setOnCancelListener { setNetworkSwitchSilently(false) }
            .show()
    }

    private fun showAllowHostDialog() {
        val input = EditText(this).apply {
            hint = "api.example.com"
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("HTTPS-Host freigeben")
            .setMessage("Nur den Hostnamen eingeben, keine URL und keinen API-Schlüssel.")
            .setView(input)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Freigeben") { _, _ ->
                runCatching { networkGate.allowExternalHost(input.text.toString()) }
                    .onSuccess {
                        toast("Host freigegeben. Externer Master bleibt davon unabhängig.")
                        refreshCoreStatus()
                    }
                    .onFailure { error -> toast("Host nicht akzeptiert: ${error.message ?: "ungültig"}") }
            }
            .show()
    }

    private fun showRemoveHostDialog() {
        val hosts = networkGate.allowedExternalHosts().sorted()
        if (hosts.isEmpty()) {
            toast("Keine externen Hosts freigegeben.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Host entfernen")
            .setItems(hosts.toTypedArray()) { _, index ->
                networkGate.removeExternalHost(hosts[index])
                toast("${hosts[index]} entfernt.")
                refreshCoreStatus()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun showAuditDialog() {
        val events = auditLog.recent(30)
        val message = if (events.isEmpty()) {
            "Noch keine Audit-Einträge."
        } else {
            events.asReversed().joinToString("\n\n") { event ->
                buildString {
                    append(event.decision)
                    append(" · ")
                    append(event.category)
                    append(" · ")
                    append(event.action)
                    event.destination?.let { append("\nZiel: ").append(it) }
                    event.reason?.let { append("\nGrund: ").append(it) }
                }
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Audit · letzte ${events.size}")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun refreshCoreStatus() {
        if (!::memoryVault.isInitialized || !::networkGate.isInitialized || !::auditLog.isInitialized) return
        val memoryCount = runCatching { memoryVault.list().size }.getOrDefault(-1)
        val hosts = networkGate.allowedExternalHosts()
        val auditCount = runCatching { auditLog.recent(1_000).size }.getOrDefault(-1)

        memoryStatus?.text = if (memoryCount >= 0) "Memory: $memoryCount Einträge · verschlüsselt" else "Memory: Fehler beim Lesen"
        networkStatus?.text = "Externes Netzwerk: ${if (networkGate.isExternalAccessEnabled) "MASTER AN" else "GESPERRT"}"
        hostsStatus?.text = "Freigegebene Hosts: ${hosts.size}${if (hosts.isEmpty()) " · keine" else " · ${hosts.joinToString()}"}"
        auditStatus?.text = if (auditCount >= 0) "Audit: $auditCount Einträge im aktuellen Log" else "Audit: Fehler beim Lesen"
        setNetworkSwitchSilently(networkGate.isExternalAccessEnabled)
    }

    private fun setNetworkSwitchSilently(enabled: Boolean) {
        suppressNetworkCallback = true
        networkSwitch?.isChecked = enabled
        suppressNetworkCallback = false
    }

    private fun card(dp: (Int) -> Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#13191E"))
        setPadding(dp(16))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(12) }
    }

    private fun text(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        setPadding(0, 8, 0, 8)
    }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
