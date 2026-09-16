package de.snowworks.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.google.android.material.button.MaterialButton
import de.snowworks.ariana.character.X88CharacterGalleryStore
import de.snowworks.ariana.character.X88CharacterGeneratorRegistry
import de.snowworks.ariana.character.X88CharacterReferenceStore
import de.snowworks.ariana.character.X88CharacterSettingsStore
import de.snowworks.ariana.character.X88GenerationRequestFactory
import de.snowworks.ariana.character.X88GenerationResult
import de.snowworks.ariana.character.X88PromptComposer
import de.snowworks.ariana.character.X88ScenePreset
import de.snowworks.ariana.character.X88Scenes

class X88CharacterGenerateActivity : AppCompatActivity() {
    private lateinit var settings: X88CharacterSettingsStore
    private lateinit var references: X88CharacterReferenceStore
    private lateinit var gallery: X88CharacterGalleryStore
    private lateinit var sceneView: TextView
    private lateinit var referenceView: TextView
    private lateinit var promptView: TextView
    private lateinit var generateButton: MaterialButton

    private var scene: X88ScenePreset = X88Scenes.master
    private var aspectRatio = "9:16"
    private var working = false

    private val referencePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        references.add(uri)
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = X88CharacterSettingsStore(this)
        references = X88CharacterReferenceStore(this)
        gallery = X88CharacterGalleryStore(this)
        scene = X88Scenes.byId(settings.sceneId)
        aspectRatio = settings.aspectRatio
        setContentView(buildUi())
        refresh()
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#05070D"))
            setPadding(dp(18))
        }
        root.addView(label("ARIANA X-88", 12f, "#7E93A6"))
        root.addView(label("IMAGE GENERATION", 26f, "#EAF7FF"))

        sceneView = label("", 13f, "#FFD56A")
        referenceView = label("", 12f, "#25D9FF")
        promptView = label("", 11f, "#AFC2D2")
        root.addView(sceneView)
        root.addView(referenceView)

        root.addView(row(
            button("ADD REFERENCE") { referencePicker.launch(arrayOf("image/*")) },
            button("CLEAR REFERENCES") {
                references.clear()
                refresh()
            },
        ))

        root.addView(label("SCENE", 11f, "#7896A6"))
        X88Scenes.all.chunked(2).forEach { pair ->
            root.addView(row(sceneButton(pair[0]), if (pair.size > 1) sceneButton(pair[1]) else button("—") {}))
        }

        root.addView(label("FORMAT", 11f, "#7896A6"))
        root.addView(row(formatButton("9:16"), formatButton("1:1")))
        root.addView(row(formatButton("16:9"), button("COPY PROMPT") { copyPrompt() }))

        root.addView(label("IDENTITY-LOCK PROMPT", 11f, "#7896A6"))
        root.addView(promptView)
        root.addView(spacer(8))

        generateButton = button("GENERATE") { generate() }
        root.addView(generateButton)
        root.addView(button("OPEN GALLERY") { startActivity(Intent(this, X88CharacterGalleryActivity::class.java)) })
        root.addView(button("BACK") { finish() })

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#05070D"))
            addView(root)
        }
    }

    private fun sceneButton(value: X88ScenePreset) = button(value.name) {
        scene = value
        settings.sceneId = value.id
        refresh()
    }

    private fun formatButton(value: String) = button(value) {
        aspectRatio = value
        settings.aspectRatio = value
        refresh()
    }

    private fun refresh() {
        if (!::sceneView.isInitialized) return
        sceneView.text = "SCENE · ${scene.name.uppercase()} · FORMAT $aspectRatio"
        val refs = references.list()
        referenceView.text = "IDENTITY REFERENCES · ${refs.size}"
        promptView.text = X88PromptComposer.compose(scene) + "\n\nNEGATIVE\n" + X88PromptComposer.negative()
        val provider = X88CharacterGeneratorRegistry.current()
        generateButton.text = when {
            working -> "GENERATING …"
            provider == null -> "GENERATE · PROVIDER NOT CONNECTED"
            else -> "GENERATE · ${provider.id}"
        }
        generateButton.isEnabled = !working
    }

    private fun generate() {
        if (working) return
        val provider = X88CharacterGeneratorRegistry.current()
        if (provider == null) {
            Toast.makeText(this, "Noch kein Bild-Provider verbunden. Prompt und Referenzen sind bereit.", Toast.LENGTH_LONG).show()
            return
        }
        val request = X88GenerationRequestFactory.create(
            scene = scene,
            aspectRatio = aspectRatio,
            referenceUris = references.list(),
        )
        working = true
        refresh()
        provider.generate(request) { result ->
            runOnUiThread {
                working = false
                when (result) {
                    is X88GenerationResult.Success -> {
                        result.images.forEach { image ->
                            gallery.add(
                                uri = image.uri,
                                sceneId = request.sceneId,
                                prompt = request.prompt,
                            )
                        }
                        Toast.makeText(this, "${result.images.size} Bild(er) gespeichert.", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, X88CharacterGalleryActivity::class.java))
                    }
                    is X88GenerationResult.Error -> Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
                refresh()
            }
        }
    }

    private fun copyPrompt() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ARIANA X-88 prompt", X88PromptComposer.compose(scene)))
        Toast.makeText(this, "Prompt kopiert.", Toast.LENGTH_SHORT).show()
    }

    private fun row(left: MaterialButton, right: MaterialButton): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
        addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
    }

    private fun button(text: String, click: () -> Unit) = MaterialButton(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { click() }
    }

    private fun label(text: String, size: Float, color: String) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(Color.parseColor(color))
        setPadding(0, dp(5), 0, dp(5))
    }

    private fun spacer(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
