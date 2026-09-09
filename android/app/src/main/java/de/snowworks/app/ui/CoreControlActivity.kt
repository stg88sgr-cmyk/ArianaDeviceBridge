package de.snowworks.app.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
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
import de.snowworks.ariana.core.model.ModelProviderStore
import de.snowworks.ariana.core.model.ProviderSecretStore

class CoreControlActivity : AppCompatActivity() {

    private lateinit var memoryVault: MemoryVault
    private lateinit var networkGate: NetworkGate
    private lateinit var auditLog: AuditLog
    private lateinit var providerStore: ModelProviderStore
    private lateinit var providerSecrets: ProviderSecretStore

    private var networkSwitch: SwitchCompat? = null
    private var memoryStatus: TextView? = null
    private var networkStatus: TextView? = null
    private var hostsStatus: TextView? = null
    private var auditStatus: TextView? = null
    private var modelStatus: TextView? = null
    private var suppressNetworkCallback = false

    private val exportMemoryLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                    writer.write(memoryVault.exportPlaintextJson())
                } ?: error("Datei konnte nicht geöffnet werden.")
            }.onSuccess {
                auditLog.append(AuditLog.Event("memory", "export", "EXPORTED", "Explicit user export"))
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
        providerStore = ModelProviderStore(this)
        providerSecrets = ProviderSecretStore(this)
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
        root.addView(text("Lokaler Speicher, Netzwerk-Gate, Modell-Router und Prüfprotokoll. Externer Zugriff startet gesperrt.", 14f, Color.parseColor("#8A9AA6")))

        root.addView(MaterialButton(this).apply {
            text = "Conversation Gate öffnen"
            setOnClickListener { startActivity(Intent(this@CoreControlActivity, ConversationActivity::class.java)) }
        })

        root.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Gerätefreigaben öffnen"
            setOnClickListener { startActivity(Intent(this@CoreControlActivity, DeviceGrantsActivity::class.java)) }
        })

        val statusCard = card(::dp)
        statusCard.addView(text("Status", 18f, Color.parseColor("#E9EEF1")))
        memoryStatus = text("Memory: …", 14f, Color.parseColor("#C5D4DC")).also(statusCard::addView)
        networkStatus = text("Externes Netzwerk: …", 14f, Color.parseColor("#C5D4DC")).also(statusCard::addView)
        hostsStatus = text("Freigegebene Hosts: …", 14f, Color.parseColor("#C5D4DC")).also(statusCard::addView)
        modelStatus = text("Modell: …", 14f, Color.parseColor("#C5D4DC")).also(statusCard::addView)
        auditStatus = text("Audit: …", 14f, Color.parseColor("#C5D4DC")).also(statusCard::addView)
        root.addView(statusCard)

        val modelCard = card(::dp)
        modelCard.addView(text("Model Router", 18f, Color.parseColor("#E9EEF1")))
        modelCard.addView(text("Das Sprachmodell ist austauschbar. Provider-Daten bleiben lokal, Tokens liegen verschlüsselt im Android Keystore. Speichern eines Providers schaltet weder Netzwerk noch Host-Allowlist ein.", 14f, Color.parseColor("#8A9AA6")))
        modelCard.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Primären Provider konfigurieren"
            setOnClickListener { showProviderDialog() }
        })
        modelCard.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Provider deaktivieren"
            setOnClickListener {
                providerStore.setActive(null)
                auditLog.append(AuditLog.Event("model_policy", "deactivate_provider", "DISABLED", "Explicit local policy change"))
                toast("Kein Modell-Provider aktiv.")
                refreshCoreStatus()
            }
        })
        modelCard.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Gespeicherten Provider-Token löschen"
            setOnClickListener {
                providerSecrets.remove(PRIMARY_PROVIDER_ID)
                auditLog.append(AuditLog.Event("model_secret", "remove", "REMOVED", "Explicit local policy change"))
                toast("Provider-Token gelöscht.")
                refreshCoreStatus()
            }
        })
        root.addView(modelCard)

        val networkCard = card(::dp)
        networkCard.addView(text("Network Gate", 18f, Color.parseColor("#E9EEF1")))
        networkCard.addView(text("Deny-by-default innerhalb der X-Ariana-App. Nur explizit freigegebene HTTPS-Hosts dürfen nach außen.", 14f, Color.parseColor("#8A9AA6")))

        val switchRow = row()
        switchRow.addView(text("Externen Zugriff erlauben", 16f, Color.parseColor("#E9EEF1")).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
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

        networkCard.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Host freigeben"
            setOnClickListener { showAllowHostDialog() }
        })
        networkCard.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Freigegebenen Host entfernen"
            setOnClickListener { showRemoveHostDialog() }
        })
        networkCard.addView(MaterialButton(this).apply {
            text = "Alle externen Hosts sperren"
            setBackgroundColor(Color.parseColor("#C45C4A"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                networkGate.setExternalAccessEnabled(false)
                networkGate.clearExternalHosts()
                toast("Externes Netzwerk und Allowlist gesperrt.")
                refreshCoreStatus()
            }
        })
        root.addView(networkCard)

        val memoryCard = card(::dp)
        memoryCard.addView(text("Memory Vault", 18f, Color.parseColor("#E9EEF1")))
        memoryCard.addView(text("Memory liegt verschlüsselt im privaten App-Speicher. Ein Export ist bewusst und sichtbar.", 14f, Color.parseColor("#8A9AA6")))
        memoryCard.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Memory exportieren"
            setOnClickListener { exportMemoryLauncher.launch("x-ariana-memory.json") }
        })
        root.addView(memoryCard)

        val auditCard = card(::dp)
        auditCard.addView(text("Audit Log", 18f, Color.parseColor("#E9EEF1")))
        auditCard.addView(text("Zeigt Entscheidungen und Ziele, aber speichert keine Nachrichteninhalte im Netzwerkprotokoll.", 14f, Color.parseColor("#8A9AA6")))
        auditCard.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Letzte Audit-Einträge anzeigen"
            setOnClickListener { showAuditDialog() }
        })
        auditCard.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Audit-Log exportieren"
            setOnClickListener { exportAuditLauncher.launch("x-ariana-audit.jsonl") }
        })
        root.addView(auditCard)

        val boundaryCard = card(::dp)
        boundaryCard.addView(text("Sicherheitsgrenze", 18f, Color.parseColor("#E9EEF1")))
        boundaryCard.addView(text("Wichtig: Diese Stufe ist noch keine Android-weite Firewall. Sie schützt Netzwerkaufrufe, die durch das X-Ariana NetworkGate geführt werden. Eine VPN-/OS-Firewall bleibt eine separate Stufe.", 14f, Color.parseColor("#E0B66A")))
        root.addView(boundaryCard)

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F12"))
            addView(root)
        }
    }

    private fun showProviderDialog() {
        val existing = providerStore.get(PRIMARY_PROVIDER_ID)
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad)
        }
        val label = EditText(this).apply {
            hint = "Name, z. B. Lokal oder GPT"
            setText(existing?.label.orEmpty())
            setSingleLine(true)
        }
        val endpoint = EditText(this).apply {
            hint = "https://api.example.com/v1/... oder http://127.0.0.1:..."
            setText(existing?.endpoint.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        val model = EditText(this).apply {
            hint = "Modellname"
            setText(existing?.model.orEmpty())
            setSingleLine(true)
        }
        val token = EditText(this).apply {
            hint = if (providerSecrets.has(PRIMARY_PROVIDER_ID)) "Token bereits gespeichert · leer lassen = behalten" else "Bearer-Token optional"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        form.addView(label)
        form.addView(endpoint)
        form.addView(model)
        form.addView(token)

        AlertDialog.Builder(this)
            .setTitle("Primären Modell-Provider speichern")
            .setMessage("Das Speichern erlaubt noch keinen Netzwerkverkehr. Externe Hosts müssen separat im Network Gate freigegeben werden.")
            .setView(form)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Speichern") { _, _ ->
                runCatching {
                    providerStore.upsert(
                        ModelProviderStore.Provider(
                            id = PRIMARY_PROVIDER_ID,
                            label = label.text.toString().trim(),
                            endpoint = endpoint.text.toString().trim(),
                            model = model.text.toString().trim(),
                            enabled = true,
                        ),
                    )
                    val newToken = token.text.toString()
                    if (newToken.isNotBlank()) providerSecrets.put(PRIMARY_PROVIDER_ID, newToken)
                    auditLog.append(AuditLog.Event("model_policy", "save_provider", "CONFIGURED", "Provider saved; network policy unchanged", endpoint.text.toString().trim(), label.text.toString().trim()))
                }.onSuccess {
                    toast("Provider gespeichert. Network Gate bleibt unverändert.")
                    refreshCoreStatus()
                }.onFailure { error ->
                    toast("Provider nicht gespeichert: ${error.message ?: "ungültige Angaben"}")
                }
            }
            .show()
    }

    private fun confirmEnableExternalNetwork() {
        AlertDialog.Builder(this)
            .setTitle("Externen Zugriff aktivieren?")
            .setMessage("Damit wird nur der Network-Gate-Master eingeschaltet. Ohne freigegebene Hosts bleibt externer Verkehr weiterhin blockiert.")
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
        val message = if (events.isEmpty()) "Noch keine Audit-Einträge." else events.asReversed().joinToString("\n\n") { event ->
            buildString {
                append(event.decision).append(" · ").append(event.category).append(" · ").append(event.action)
                event.destination?.let { append("\nZiel: ").append(it) }
                event.reason?.let { append("\nGrund: ").append(it) }
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Audit · letzte ${events.size}")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun refreshCoreStatus() {
        if (!::memoryVault.isInitialized || !::networkGate.isInitialized || !::auditLog.isInitialized || !::providerStore.isInitialized) return
        val memoryCount = runCatching { memoryVault.list().size }.getOrDefault(-1)
        val hosts = networkGate.allowedExternalHosts()
        val auditCount = runCatching { auditLog.recent(1_000).size }.getOrDefault(-1)
        val activeProvider = providerStore.active()

        memoryStatus?.text = if (memoryCount >= 0) "Memory: $memoryCount Einträge · verschlüsselt" else "Memory: Fehler beim Lesen"
        networkStatus?.text = "Externes Netzwerk: ${if (networkGate.isExternalAccessEnabled) "MASTER AN" else "GESPERRT"}"
        hostsStatus?.text = "Freigegebene Hosts: ${hosts.size}${if (hosts.isEmpty()) " · keine" else " · ${hosts.joinToString()}"}"
        modelStatus?.text = if (activeProvider == null) {
            "Modell: kein Provider aktiv"
        } else {
            "Modell: ${activeProvider.label} · ${activeProvider.model} · Token ${if (providerSecrets.has(activeProvider.id)) "verschlüsselt gespeichert" else "nicht gespeichert"}"
        }
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
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
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

    companion object {
        private const val PRIMARY_PROVIDER_ID = "primary"
    }
}
