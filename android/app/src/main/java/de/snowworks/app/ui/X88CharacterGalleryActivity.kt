package de.snowworks.app.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.google.android.material.button.MaterialButton
import de.snowworks.ariana.character.X88CharacterGalleryStore
import de.snowworks.ariana.character.X88GalleryItem
import de.snowworks.ariana.character.X88Scenes

class X88CharacterGalleryActivity : AppCompatActivity() {
    private lateinit var store: X88CharacterGalleryStore
    private lateinit var listRoot: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = X88CharacterGalleryStore(this)
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#05070D"))
            setPadding(dp(18))
        }
        root.addView(label("ARIANA X-88", 12f, "#7E93A6"))
        root.addView(label("CHARACTER GALLERY", 26f, "#EAF7FF"))
        root.addView(button("BACK") { finish() })
        listRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listRoot)
        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#05070D"))
            addView(root)
        }
    }

    private fun refresh() {
        if (!::listRoot.isInitialized) return
        listRoot.removeAllViews()
        val items = store.list()
        if (items.isEmpty()) {
            listRoot.addView(label("Noch keine generierten Bilder gespeichert.", 13f, "#7896A6"))
            return
        }
        items.forEach { listRoot.addView(itemCard(it)) }
    }

    private fun itemCard(item: X88GalleryItem): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10))
        setBackgroundColor(Color.parseColor("#101722"))

        val image = ImageView(this@X88CharacterGalleryActivity).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_CROP
            minimumHeight = dp(180)
            runCatching { setImageURI(Uri.parse(item.uri)) }
            setOnClickListener { openUri(item.uri) }
        }
        addView(image, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(240)))
        addView(label("${X88Scenes.byId(item.sceneId).name} · ${if (item.favorite) "FAVORITE" else "SAVED"}", 12f, "#25D9FF"))
        addView(label(item.uri, 10f, "#AFC2D2"))
        addView(row(
            button(if (item.favorite) "★ FAVORITE" else "☆ FAVORITE") {
                store.setFavorite(item.id, !item.favorite)
                refresh()
            },
            button("REMOVE") {
                store.remove(item.id)
                refresh()
            },
        ))
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.setMargins(0, dp(6), 0, dp(10))
        layoutParams = params
    }

    private fun openUri(value: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(value)).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        }.onFailure {
            Toast.makeText(this, "Bild kann nicht geöffnet werden.", Toast.LENGTH_SHORT).show()
        }
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

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
