package de.snowworks.app.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.google.android.material.button.MaterialButton
import de.snowworks.ariana.core.NetworkGate
import de.snowworks.ariana.core.model.ArianaConversationEngine

/**
 * Transparent conversation screen. Preparing is local-only. Network traffic can
 * happen only after the user presses SEND and NetworkGate allows the endpoint.
 */
class ConversationActivity : AppCompatActivity() {
    private lateinit var engine: ArianaConversationEngine
    private lateinit var networkGate: NetworkGate
    private lateinit var promptInput: EditText
    private lateinit var previewText: TextView
    private lateinit var replyText: TextView
    private lateinit var sendButton: MaterialButton
    private var prepared: ArianaConversationEngine.PreparedRequest? = null
    @Volatile private var sending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = ArianaConversationEngine(this)
        networkGate = NetworkGate(this)
        setContentView(buildUi())
    }

    private fun buildUi(): ScrollView {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0F12"))
            setPadding(dp(20))
        }

        root.addView(text("X-ARIANA", 12f, Color.parseColor("#8A9AA6")))
        root.addView(text("Conversation Gate", 28f, Color.parseColor("#E9EEF1")))
        root.addView(text("Vor dem Senden siehst du Provider, Memory-Auswahl und Network-Gate-Entscheidung. Vorbereiten sendet keine Daten.", 14f, Color.parseColor("#8A9AA6")))

        promptInput = EditText(this).apply {
            hint = "Nachricht eingeben"
            minLines = 4
            maxLines = 10
            setTextColor(Color.parseColor("#E9EEF1"))
            setHintTextColor(Color.parseColor("#6F7D86"))
            setBackgroundColor(Color.parseColor("#13191E"))
            setPadding(dp(14))
        }
        root.addView(promptInput)

        root.addView(MaterialButton(this).apply {
            text = "Lokal vorbereiten · nichts senden"
            setOnClickListener { prepareLocally() }
        })

        previewText = text("Noch nichts vorbereitet.", 14f, Color.parseColor("#C5D4DC"))
        root.addView(previewText)

        sendButton = MaterialButton(this).apply {
            text = "SENDEN"
            isEnabled = false
            setBackgroundColor(Color.parseColor("#3B6E72"))
            setTextColor(Color.WHITE)
            setOnClickListener { sendPrepared() }
        }
        root.addView(sendButton)

        root.addView(text("Antwort", 18f, Color.parseColor("#E9EEF1")))
        replyText = text("", 15f, Color.parseColor("#E9EEF1"))
        root.addView(replyText)

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F12"))
            addView(root)
        }
    }

    private fun prepareLocally() {
        if (sending) return
        val message = promptInput.text.toString().trim()
        runCatching { engine.prepare(message) }
            .onSuccess { request ->
                prepared = request
                val policy = networkGate.preview(
                    NetworkGate.Request(
                        destination = request.provider.endpoint,
                        purpose = "conversation_preview",
                        provider = request.provider.label,
                        payload = request.payloadJson.toByteArray(Charsets.UTF_8),
                    ),
                )
                previewText.text = buildString {
                    append("Provider: ${request.provider.label}\n")
                    append("Modell: ${request.provider.model}\n")
                    append("Ziel: ${request.provider.endpoint}\n")
                    append("Memory: ${request.memory.records.size} Einträge · ${request.memory.totalChars} Zeichen\n")
                    if (request.memory.records.isNotEmpty()) {
                        append("Memory-IDs: ")
                        append(request.memory.records.take(8).joinToString { it.id })
                        if (request.memory.records.size > 8) append(" …")
                        append("\n")
                    }
                    append("Network Gate: ${policy.decision} · ${policy.reason}\n")
                    append("Gesendet: NEIN")
                }
                sendButton.isEnabled = policy.allowed
                if (!policy.allowed) toast("Senden bleibt gesperrt: ${policy.reason}")
            }
            .onFailure { error ->
                prepared = null
                sendButton.isEnabled = false
                previewText.text = "Vorbereitung fehlgeschlagen: ${error.message ?: "Unbekannter Fehler"}"
            }
    }

    private fun sendPrepared() {
        val request = prepared ?: return
        if (sending) return

        val currentText = promptInput.text.toString().trim()
        if (currentText != request.userText) {
            prepared = null
            sendButton.isEnabled = false
            toast("Text wurde nach der Vorbereitung geändert. Bitte erneut lokal vorbereiten.")
            previewText.append("\nBLOCKIERT: Text nach Vorbereitung geändert.")
            return
        }

        val policy = networkGate.preview(
            NetworkGate.Request(
                destination = request.provider.endpoint,
                purpose = "conversation_send",
                provider = request.provider.label,
                payload = request.payloadJson.toByteArray(Charsets.UTF_8),
            ),
        )
        if (!policy.allowed) {
            sendButton.isEnabled = false
            toast("Network Gate blockiert: ${policy.reason}")
            return
        }

        sending = true
        sendButton.isEnabled = false
        replyText.text = "Anfrage läuft …"
        Thread({
            val result = runCatching { engine.send(request) }
            runOnUiThread {
                sending = false
                prepared = null
                sendButton.isEnabled = false
                result.onSuccess { reply ->
                    replyText.text = reply.text
                    previewText.append("\nGesendet: JA · HTTP ${reply.statusCode}\nFür erneutes Senden bitte neu vorbereiten.")
                }.onFailure { error ->
                    replyText.text = "Fehler: ${error.message ?: "Unbekannter Fehler"}"
                    previewText.append("\nSendeversuch beendet. Für einen neuen Versuch bitte neu vorbereiten.")
                }
            }
        }, "X-ArianaModelRequest").apply {
            isDaemon = true
            start()
        }
    }

    private fun text(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        setPadding(0, 8, 0, 8)
        gravity = Gravity.START
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
