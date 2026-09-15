package de.snowworks.app.ui

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout

/**
 * Stable avatar facade for X88HomeActivity.
 *
 * V11 prefers the Filament/glTF surface when a valid model exists, otherwise it
 * leaves the proven procedural V7 avatar untouched and visible.
 */
class X88AvatarHostView(context: Context) : FrameLayout(context) {
    companion object {
        private const val TAG = "X88AvatarHost"
    }

    private val fallbackView = X88AvatarView(context)
    private val modelView = X88Avatar3DView(context)

    var mode: X88AvatarView.Mode = X88AvatarView.Mode.IDLE
        set(value) {
            field = value
            fallbackView.mode = value
            modelView.mode = value
        }

    val isUsing3D: Boolean
        get() = modelView.visibility == View.VISIBLE

    init {
        setBackgroundColor(android.graphics.Color.BLACK)

        addView(
            fallbackView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        addView(
            modelView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )

        modelView.visibility = View.INVISIBLE
        fallbackView.visibility = View.VISIBLE

        modelView.onModelReady = {
            Log.i(TAG, "ARIANA X-88 V11 GLB loaded; switching from procedural fallback to 3D.")
            fallbackView.visibility = View.GONE
            modelView.visibility = View.VISIBLE
            modelView.mode = mode
        }
        modelView.onModelError = { error ->
            Log.w(TAG, "3D avatar unavailable; keeping procedural fallback.", error)
            modelView.visibility = View.GONE
            fallbackView.visibility = View.VISIBLE
            fallbackView.mode = mode
        }
    }
}
