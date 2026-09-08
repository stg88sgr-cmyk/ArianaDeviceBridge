package de.snowworks.app.ui

import android.content.Intent

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
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
import de.snowworks.ariana.ArianaDeviceApi
import de.snowworks.ariana.ArianaResult
import de.snowworks.ariana.Feature
import de.snowworks.ariana.camera.CameraFrameStore
import de.snowworks.ariana.session.ArianaCaptureService
import de.snowworks.ariana.files.TreePermissionStore
import de.snowworks.ariana.bridge.LocalBridgeServer

class DeviceGrantsActivity : AppCompatActivity() {

    private lateinit var api: ArianaDeviceApi
    private var pendingFeature: Feature? = null
    private val statusViews = mutableMapOf<Feature, TextView>()
    private val switches = mutableMapOf<Feature, SwitchCompat>()
    private var masterSwitch: SwitchCompat? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refresh()
        }

    private val screenLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val feature = pendingFeature
            pendingFeature = null
            if (feature != Feature.SCREEN) return@registerForActivityResult
            if (result.resultCode != RESULT_OK || result.data == null) {
                toast("Systemdialog abgebrochen. Bildschirmübertragung bleibt aus.")
                refresh()
                return@registerForActivityResult
            }
            ArianaCaptureService.startWithProjection(this, result.resultCode, result.data!!)
            refresh()
        }

    private val documentTreeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) {
                toast("Kein Ordner gewählt.")
            } else {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, flags)
                TreePermissionStore(this).save(uri)
                toast("Ordner dauerhaft für diese App freigegeben. Inhalte werden nicht übertragen.")
                api.start(this, Feature.FILES)
            }
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = ArianaDeviceApi(this)
        LocalBridgeServer.start(this) // starts only if the persisted master gate is enabled
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun buildUi(): ScrollView {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0F12"))
            setPadding(dp(20))
        }

        root.addView(text("SNOWWORKS", 12f, Color.parseColor("#8A9AA6")))
        root.addView(text("Gerätefreigaben", 28f, Color.parseColor("#E9EEF1")).apply {
            setPadding(0, dp(4), 0, dp(8))
        })
        root.addView(
            text(
                api.getConnection().detail,
                14f,
                Color.parseColor("#8A9AA6"),
            ),
        )

        val masterRow = row()
        masterRow.addView(
            text("Ariana-Gerätezugriff", 16f, Color.parseColor("#E9EEF1")).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            },
        )
        masterSwitch = SwitchCompat(this).apply {
            isChecked = api.isMasterEnabled()
            setOnCheckedChangeListener { _, on ->
                api.setMasterEnabled(on)
                toast(
                    if (on) "Zugriff ein. Keine Sitzung automatisch gestartet."
                    else "Zugriff aus. Sitzungen beendet.",
                )
                refresh()
            }
        }
        masterRow.addView(masterSwitch)
        root.addView(masterRow)

        root.addView(
            MaterialButton(this).apply {
                text = "Alles stoppen"
                setBackgroundColor(Color.parseColor("#C45C4A"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    api.stopAll()
                    toast("Alles gestoppt. Neue Aktionen sind gesperrt.")
                    refresh()
                }
            },
        )

        Feature.entries.forEach { feature ->
            root.addView(featureCard(feature, ::dp))
        }

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F12"))
            addView(root)
        }
    }

    private fun featureCard(feature: Feature, dp: (Int) -> Int): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#13191E"))
            setPadding(dp(16))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = dp(12)
            layoutParams = lp
        }
        card.addView(text(feature.title, 18f, Color.parseColor("#E9EEF1")))
        card.addView(text(feature.purpose, 14f, Color.parseColor("#8A9AA6")))
        val status = text("", 13f, Color.parseColor("#C5D4DC"))
        statusViews[feature] = status
        card.addView(status)

        val actions = row()
        actions.addView(
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Freigeben"
                setOnClickListener { requestGrant(feature) }
            },
        )
        val sw = SwitchCompat(this).apply {
            setOnCheckedChangeListener { view, on ->
                if (!view.isPressed && !view.isHovered) return@setOnCheckedChangeListener
                if (on) enableFeature(feature) else {
                    api.stop(feature)
                    refresh()
                }
            }
        }
        switches[feature] = sw
        actions.addView(sw)
        card.addView(actions)
        if (feature == Feature.CAMERA) {
            card.addView(
                MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = "Testbild anzeigen"
                    setOnClickListener { showCameraTestImage() }
                },
            )
        }
        return card
    }

    private fun requestGrant(feature: Feature) {
        AlertDialog.Builder(this)
            .setTitle(feature.title)
            .setMessage(feature.purpose + "\n\nDie Berechtigung wird erst jetzt angefragt.")
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Weiter") { _, _ ->
                when (feature) {
                    Feature.NOTIFY_READ -> startActivity(api.openNotificationListenerSettings())
                    Feature.FILES -> documentTreeLauncher.launch(null)
                    Feature.SCREEN -> {
                        pendingFeature = Feature.SCREEN
                        screenLauncher.launch(api.createScreenCaptureIntent())
                    }
                    else -> {
                        val perms = api.requiredRuntimePermissions(feature)
                        if (perms.isEmpty()) {
                            toast("Keine Runtime-Berechtigung nötig.")
                        } else {
                            permissionLauncher.launch(perms)
                        }
                    }
                }
            }
            .show()
    }

    private fun enableFeature(feature: Feature) {
        AlertDialog.Builder(this)
            .setTitle("${feature.title} aktivieren")
            .setMessage(feature.purpose)
            .setNegativeButton("Abbrechen") { _, _ -> refresh() }
            .setPositiveButton("Aktivieren") { _, _ ->
                when (val gate = api.canUse(feature)) {
                    is ArianaResult.Err -> {
                        if (gate.error.code == de.snowworks.ariana.ArianaError.Code.DENIED) {
                            requestGrant(feature)
                        } else {
                            toast(gate.error.message)
                        }
                        refresh()
                        return@setPositiveButton
                    }
                    is ArianaResult.Ok -> Unit
                }
                if (feature == Feature.SCREEN) {
                    pendingFeature = Feature.SCREEN
                    screenLauncher.launch(api.createScreenCaptureIntent())
                    return@setPositiveButton
                }
                if (feature == Feature.FILES) {
                    documentTreeLauncher.launch(null)
                    return@setPositiveButton
                }
                if (feature == Feature.NOTIFY_READ && !api.getStatus(feature).permissionGranted) {
                    startActivity(api.openNotificationListenerSettings())
                    refresh()
                    return@setPositiveButton
                }
                when (val r = api.start(this, feature)) {
                    is ArianaResult.Err -> toast(r.error.message)
                    is ArianaResult.Ok -> Unit
                }
                refresh()
            }
            .show()
    }

    private fun refresh() {
        masterSwitch?.isChecked = api.isMasterEnabled()
        Feature.entries.forEach { feature ->
            val status = api.getStatus(feature)
            val cameraFrames = feature == Feature.CAMERA && status.sessionActive && CameraFrameStore.hasFrame()
            statusViews[feature]?.text = buildString {
                append("Status: ${status.permissionLabel}")
                if (status.sessionActive) append(" · Sitzung aktiv")
                if (cameraFrames) append(" · Bilddaten aktiv")
            }
            switches[feature]?.isChecked = status.enabled || status.sessionActive
        }
    }

    private fun showCameraTestImage() {
        val frame = CameraFrameStore.latest()
        if (frame == null) {
            toast("Noch kein Kamerabild da. Kamera einschalten und kurz warten.")
            return
        }
        val bitmap = BitmapFactory.decodeByteArray(frame.jpeg, 0, frame.jpeg.size)
        if (bitmap == null) {
            toast("Kamerabild konnte nicht dekodiert werden.")
            return
        }
        val image = ImageView(this).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(24, 16, 24, 16)
        }
        AlertDialog.Builder(this)
            .setTitle("Kamera-Testbild · ${frame.width}×${frame.height}")
            .setView(image)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
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
}
