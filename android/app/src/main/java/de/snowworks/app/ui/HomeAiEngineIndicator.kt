package de.snowworks.app.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import de.snowworks.ariana.bridge.AiRouteStateStore
import java.lang.ref.WeakReference

/**
 * Small, non-invasive HUD for X88HomeActivity.
 *
 * Shows which AI engine handled the most recent routed dialogue without
 * storing or rendering prompts, replies or credentials.
 */
object HomeAiEngineIndicator {
    private const val TAG = "x88_ai_engine_indicator"
    private const val REFRESH_MS = 750L

    fun attach(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val existing = content.findViewWithTag<TextView>(TAG)
        val view = existing ?: TextView(activity).apply {
            tag = TAG
            textSize = 12f
            setTextColor(Color.parseColor("#EAF7FF"))
            setBackgroundColor(Color.parseColor("#B3111822"))
            gravity = Gravity.CENTER
            setPadding(dp(activity, 12), dp(activity, 7), dp(activity, 12), dp(activity, 7))
            isClickable = true
            isFocusable = true
            contentDescription = "Aktiver KI-Motor. Tippen für KI-Provider-Einstellungen."
            setOnClickListener {
                activity.startActivity(Intent(activity, AiProviderSettingsActivity::class.java))
            }
        }.also { badge ->
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            ).apply {
                topMargin = dp(activity, 18)
                marginEnd = dp(activity, 14)
            }
            content.addView(badge, params)
        }

        val activityRef = WeakReference(activity)
        val viewRef = WeakReference(view)
        val handler = Handler(Looper.getMainLooper())
        val updater = object : Runnable {
            override fun run() {
                val currentActivity = activityRef.get() ?: return
                val badge = viewRef.get() ?: return
                if (currentActivity.isFinishing || currentActivity.isDestroyed || badge.parent == null) return

                val state = AiRouteStateStore.read(currentActivity.applicationContext)
                badge.text = buildString {
                    append("AI · ").append(state.engine)
                    if (state.fallbackUsed) append(" · FALLBACK")
                }
                handler.postDelayed(this, REFRESH_MS)
            }
        }
        handler.removeCallbacksAndMessages(view)
        handler.post(updater)
    }

    fun detach(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        content.findViewWithTag<TextView>(TAG)?.let(content::removeView)
    }

    private fun dp(activity: Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
