package de.snowworks.app.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import de.snowworks.app.resonance.ResonanceMapper
import de.snowworks.app.resonance.X88RuneGraph

class X88ResonanceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
    }

    private fun buildUi(): ScrollView {
        val signature = ResonanceMapper.map(X88RuneGraph.default)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#05070D"))
            setPadding(dp(20))
        }
        root.addView(label("ARIANA X-88", 12f, "#7E93A6"))
        root.addView(label("RESONANCE CORE", 28f, "#EAF7FF"))
        root.addView(label("DETERMINISTIC · LOCAL · PASSIVE", 11f, "#25D9FF"))
        root.addView(label("∞   ᛉ   ᚱ   ᚲ   ⬡", 34f, "#F35BFF"))
        root.addView(label("Fundamental · %.2f Hz".format(signature.fundamentalHz), 16f, "#EAF7FF"))
        root.addView(label("Pulse · %.2f Hz".format(signature.pulseHz), 16f, "#EAF7FF"))
        root.addView(label("Modulation · %.2f Hz".format(signature.modulationHz), 16f, "#EAF7FF"))
        root.addView(label("Harmonics · ${signature.harmonicsHz.joinToString { "%.2f".format(it) }}", 14f, "#B8CAD6"))
        root.addView(label("Fingerprint", 12f, "#7E93A6"))
        root.addView(label(signature.fingerprint.chunked(16).joinToString("\n"), 13f, "#25D9FF"))
        root.addView(label("Dieser Screen liest keine Sensoren und startet kein Mikrofon. Er visualisiert nur den deterministischen Resonance-Core.", 12f, "#7896A6"))
        return ScrollView(this).apply { addView(root) }
    }

    private fun label(text: String, sizeSp: Float, color: String) = TextView(this).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(Color.parseColor(color))
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(8))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
