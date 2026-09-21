package de.snowworks.ariana.prompter

import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.io.File

class AppPrompterActivity : AppCompatActivity() {
    private lateinit var promptInput: EditText
    private lateinit var resultView: TextView
    private var currentHtml: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(android.graphics.Color.parseColor("#05070D"))
        }
        root.addView(TextView(this).apply {
            text = "SNOWWORKS APP PROMPTER V3"
            textSize = 24f
            setTextColor(android.graphics.Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "Prompt → App-Spezifikation → HTML-Vorschau"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#25D9FF"))
            setPadding(0, 8, 0, 20)
        })
        promptInput = EditText(this).apply {
            hint = "Beschreibe deine App …"
            setHintTextColor(android.graphics.Color.parseColor("#7896A6"))
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#101722"))
            minLines = 5
            gravity = android.view.Gravity.TOP
            setPadding(20, 20, 20, 20)
        }
        root.addView(promptInput)
        root.addView(MaterialButton(this).apply {
            text = "APP ERZEUGEN"
            setOnClickListener { generate() }
        })
        root.addView(MaterialButton(this).apply {
            text = "HTML SPEICHERN"
            setOnClickListener { saveCurrentHtml() }
        })
        resultView = TextView(this).apply {
            text = "Noch kein Projekt erzeugt."
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#EAF7FF"))
            setPadding(8, 20, 8, 8)
        }
        root.addView(resultView)
        return ScrollView(this).apply { addView(root) }
    }

    private fun generate() {
        val prompt = promptInput.text.toString()
        if (prompt.isBlank()) {
            Toast.makeText(this, "Bitte zuerst einen App-Prompt eingeben.", Toast.LENGTH_SHORT).show()
            return
        }
        val spec = AppPrompterEngine.generate(prompt)
        currentHtml = spec.html
        resultView.text = buildString {
            appendLine("APP · " + spec.appName)
            appendLine()
            appendLine("FEATURES")
            spec.features.forEach { appendLine("• " + it) }
            appendLine()
            appendLine("HTML · " + spec.html.length + " Zeichen")
            appendLine("Bereit zum Speichern.")
        }
    }

    private fun saveCurrentHtml() {
        val html = currentHtml
        if (html == null) {
            Toast.makeText(this, "Erzeuge zuerst eine App.", Toast.LENGTH_SHORT).show()
            return
        }
        val file = File(filesDir, "AppPrompter")
        file.mkdirs()
        val output = File(file, "app-preview.html")
        output.writeText(html, Charsets.UTF_8)
        Toast.makeText(this, "Gespeichert: " + output.name, Toast.LENGTH_LONG).show()
    }
}