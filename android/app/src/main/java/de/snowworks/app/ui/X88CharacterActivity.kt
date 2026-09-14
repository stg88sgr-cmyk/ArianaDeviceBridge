package de.snowworks.app.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.google.android.material.button.MaterialButton
import de.snowworks.ariana.ArianaDeviceApi
import de.snowworks.ariana.character.X88CharacterCore
import de.snowworks.ariana.character.X88CharacterSettingsStore
import de.snowworks.ariana.character.X88PromptComposer
import de.snowworks.ariana.character.X88ScenePreset
import de.snowworks.ariana.character.X88Scenes

class X88CharacterActivity : AppCompatActivity() {
    private lateinit var api: ArianaDeviceApi
    private lateinit var avatar: X88AvatarView
    private lateinit var settings: X88CharacterSettingsStore
    private lateinit var sceneView: TextView
    private lateinit var statusView: TextView
    private lateinit var promptView: TextView
    private var selectedScene: X88ScenePreset = X88Scenes.master

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = ArianaDeviceApi(this)
        settings = X88CharacterSettingsStore(this)
        selectedScene = X88Scenes.byId(settings.sceneId)
        setContentView(buildUi())
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::statusView.isInitialized) refresh()
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#05070D"))
            setPadding(dp(18))
        }

        root.addView(label("ARIANA X-88", 12f, "#7E93A6"))
        root.addView(label("CHARACTER CORE", 28f, "#EAF7FF"))
        root.addView(label("${X88CharacterCore.version} · identity locked", 11f, "#6F8799"))

        avatar = X88AvatarView(this)
        root.addView(avatar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(360)))

        statusView = label("Status …", 13f, "#25D9FF")
        sceneView = label("SCENE · ${selectedScene.name}", 13f, "#FFD56A")
        promptView = label("", 11f, "#AFC2D2")
        root.addView(statusView)
        root.addView(sceneView)
        root.addView(label("Long dark hair · blue eyes · biomechanical black/white · cyan/magenta/gold", 12f, "#E8D9F2"))
        root.addView(spacer(8))

        root.addView(row(
            button("GALLERY") { startActivity(Intent(this, X88CharacterGalleryActivity::class.java)) },
            button("GENERATE") { startActivity(Intent(this, X88CharacterGenerateActivity::class.java)) },
        ))
        root.addView(row(
            button("VOICE") { startActivity(Intent(this, VoiceTestActivity::class.java)) },
            button("DEVICE CORE") { finish() },
        ))

        root.addView(spacer(10))
        root.addView(label("SCENE PRESETS", 11f, "#7896A6"))
        X88Scenes.all.chunked(2).forEach { pair ->
            val left = sceneButton(pair[0])
            val right = if (pair.size > 1) sceneButton(pair[1]) else button("—") {}
            root.addView(row(left, right))
        }

        root.addView(spacer(10))
        root.addView(label("IDENTITY PROMPT", 11f, "#7896A6"))
        root.addView(promptView)
        root.addView(spacer(18))

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#05070D"))
            addView(root)
        }
    }

    private fun sceneButton(scene: X88ScenePreset) = button(scene.name) {
        selectedScene = scene
        settings.sceneId = scene.id
        avatar.mode = when (scene.id) {
            X88Scenes.techLab.id -> X88AvatarView.Mode.THINKING
            X88Scenes.night.id -> X88AvatarView.Mode.ATTENTION
            else -> X88AvatarView.Mode.IDLE
        }
        refreshScene()
    }

    private fun refresh() {
        val master = api.isMasterEnabled() && !api.isBlocked()
        statusView.text = when {
            api.isBlocked() -> "DEVICE CORE · STOP ALL"
            master -> "DEVICE CORE · MASTER ON · ${api.getConnection().label}"
            else -> "DEVICE CORE · MASTER OFF · ${api.getConnection().label}"
        }
        if (api.isBlocked()) avatar.mode = X88AvatarView.Mode.STOPPED
        else if (avatar.mode == X88AvatarView.Mode.STOPPED) avatar.mode = X88AvatarView.Mode.IDLE
        refreshScene()
    }

    private fun refreshScene() {
        sceneView.text = "SCENE · ${selectedScene.name.uppercase()}"
        promptView.text = X88PromptComposer.compose(selectedScene)
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
