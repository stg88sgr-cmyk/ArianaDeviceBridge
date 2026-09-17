package de.snowworks.app.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.google.android.material.button.MaterialButton
import de.snowworks.ariana.sidecar.SidecarChannel
import de.snowworks.ariana.sidecar.SidecarChatStore
import de.snowworks.ariana.sidecar.SidecarMessage
import de.snowworks.ariana.sidecar.SidecarRelayClient
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

class X88SidecarChatsActivity : AppCompatActivity() {
    private lateinit var store: SidecarChatStore
    private val relay = SidecarRelayClient()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ArianaTriSidecar").apply { isDaemon = true }
    }

    private lateinit var channelTitle: TextView
    private lateinit var channelPurpose: TextView
    private lateinit var relayStatus: TextView
    private lateinit var historyView: TextView
    private lateinit var historyScroll: ScrollView
    private lateinit var input: EditText
    private lateinit var sendButton: MaterialButton
    private val channelButtons = linkedMapOf<SidecarChannel, MaterialButton>()
    private var activeChannel = SidecarChannel.OBSERVE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SidecarChatStore(this)
        setContentView(buildUi())
        selectChannel(activeChannel)
        checkRelay()
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

        root.addView(label("ARIANA X-88", 12f, "#7E93A6"))
        root.addView(label("TRI-SIDECAR CHAT", 28f, "#EAF7FF"))
        root.addView(label("3 unabhängige Chats · direkt an X88 gebunden", 12f, "#8DA9B8"))

        relayStatus = label("RELAY · CHECKING", 12f, "#25D9FF")
        root.addView(relayStatus)
        root.addView(spacer(8))

        SidecarChannel.entries.forEach { channel ->
            val button = button(channel.title) { selectChannel(channel) }
            channelButtons[channel] = button
            root.addView(button)
        }

        root.addView(spacer(10))
        channelTitle = label("", 20f, "#EAF7FF")
        channelPurpose = label("", 12f, "#8DA9B8")
        root.addView(channelTitle)
        root.addView(channelPurpose)

        historyView = label("", 14f, "#D8E7EF").apply {
            setTextIsSelectable(true)
        }
        historyScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#080D14"))
            isFillViewport = true
            addView(historyView)
        }
        root.addView(
            historyScroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(360)).apply {
                topMargin = dp(10)
                bottomMargin = dp(10)
            },
        )

        input = EditText(this).apply {
            hint = "Nachricht in diesen Sidecar-Chat …"
            setTextColor(Color.parseColor("#EAF7FF"))
            setHintTextColor(Color.parseColor("#6F8799"))
            setBackgroundColor(Color.parseColor("#0B1018"))
            setPadding(dp(12))
            minLines = 2
            maxLines = 6
            imeOptions = EditorInfo.IME_ACTION_SEND
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendMessage()
                    true
                } else false
            }
        }
        root.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        sendButton = button("SEND · ARIANA RELAY") { sendMessage() }
        root.addView(sendButton)
        root.addView(button("RELAY · TOKEN / STATUS") { showRelaySettings() })
        root.addView(button("OPEN TERMUX") { openTermux() })
        root.addView(button("CLEAR · CURRENT CHAT") { confirmClear() }.apply {
            setTextColor(Color.parseColor("#FFB2C3"))
        })
        root.addView(label("Der Sidecar-Pfad verwendet ausschließlich den lokalen Relay auf 127.0.0.1:8766. Die drei Verläufe werden getrennt gespeichert.", 11f, "#748A98"))

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#05070D"))
            addView(root)
        }
    }

    private fun selectChannel(channel: SidecarChannel) {
        activeChannel = channel
        channelTitle.text = channel.title
        channelPurpose.text = channel.purpose
        channelButtons.forEach { (candidate, button) ->
            button.alpha = if (candidate == channel) 1.0f else 0.55f
        }
        renderHistory()
    }

    private fun renderHistory() {
        val messages = store.load(activeChannel)
        historyView.text = if (messages.isEmpty()) {
            "Noch kein Verlauf in ${activeChannel.title}."
        } else {
            val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
            messages.joinToString("\n\n") { item ->
                val who = if (item.role == SidecarMessage.Role.USER) "Du" else "Ariana"
                val stamp = runCatching { formatter.format(Instant.ofEpochMilli(item.timestampMs)) }.getOrDefault("--:--")
                "$who · $stamp\n${item.text}"
            }
        }
        historyScroll.post { historyScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun sendMessage() {
        val message = input.text?.toString().orEmpty().trim()
        if (message.isBlank()) return
        val token = store.relayToken()
        if (token.length < 16) {
            relayStatus.text = "RELAY · TOKEN REQUIRED"
            showRelaySettings()
            return
        }

        val channel = activeChannel
        val previousHistory = store.load(channel)
        store.append(channel, SidecarMessage(SidecarMessage.Role.USER, message))
        input.text?.clear()
        renderHistory()
        setBusy(true)
        relayStatus.text = "RELAY · ${channel.wireId.uppercase()} · THINKING"

        executor.execute {
            val result = relay.send(channel, previousHistory, message, token)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (result.ok && !result.reply.isNullOrBlank()) {
                    store.append(channel, SidecarMessage(SidecarMessage.Role.ASSISTANT, result.reply))
                    relayStatus.text = "RELAY · ONLINE · ${result.model ?: "MODEL"}"
                } else {
                    relayStatus.text = "RELAY · ${result.error ?: "FAILED"}"
                }
                if (activeChannel == channel) renderHistory()
                setBusy(false)
            }
        }
    }

    private fun checkRelay() {
        relayStatus.text = "RELAY · CHECKING"
        executor.execute {
            val result = relay.health()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                relayStatus.text = if (result.ok) {
                    "RELAY · ONLINE · ${result.model ?: "READY"}"
                } else {
                    "RELAY · OFFLINE"
                }
            }
        }
    }

    private fun showRelaySettings() {
        val tokenInput = EditText(this).apply {
            hint = "X88 Sidecar Token"
            setText(store.relayToken())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSelection(text.length)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20))
            addView(label("Relay: 127.0.0.1:8766", 13f, "#33434F"))
            addView(label("Token wird lokal in der X88-App gespeichert. Der OpenAI-Schlüssel bleibt in Termux und gehört nicht in die APK.", 12f, "#526671"))
            addView(tokenInput)
        }
        AlertDialog.Builder(this)
            .setTitle("Sidecar Relay")
            .setView(box)
            .setNegativeButton("Abbrechen", null)
            .setNeutralButton("Termux") { _, _ -> openTermux() }
            .setPositiveButton("Speichern") { _, _ ->
                store.setRelayToken(tokenInput.text?.toString().orEmpty())
                checkRelay()
            }
            .show()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("${activeChannel.title} leeren?")
            .setMessage("Nur der Verlauf dieses Sidecar-Chats wird gelöscht.")
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Leeren") { _, _ ->
                store.clear(activeChannel)
                renderHistory()
            }
            .show()
    }

    private fun openTermux() {
        val launch = packageManager.getLaunchIntentForPackage("com.termux")
        if (launch != null) startActivity(launch)
        else startActivity(Intent(Intent.ACTION_VIEW).apply { data = android.net.Uri.parse("https://termux.dev") })
    }

    private fun setBusy(busy: Boolean) {
        sendButton.isEnabled = !busy
        input.isEnabled = !busy
        channelButtons.values.forEach { it.isEnabled = !busy }
    }

    private fun label(text: String, sizeSp: Float, color: String): TextView = TextView(this).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(Color.parseColor(color))
        setPadding(dp(8))
    }

    private fun button(text: String, action: () -> Unit): MaterialButton = MaterialButton(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4)
            bottomMargin = dp(4)
        }
    }

    private fun spacer(heightDp: Int) = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(heightDp))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
