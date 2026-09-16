package de.snowworks.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import de.snowworks.app.R

object PresenceAvatarBitmap {
    @Volatile private var cached: Bitmap? = null

    fun get(context: Context): Bitmap? {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: build(context)?.also { cached = it }
        }
    }

    private fun build(context: Context): Bitmap? {
        val source = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.ariana_x88_presence_master,
        ) ?: return null
        val left = (source.width * 0.18f).toInt().coerceAtLeast(0)
        val top = (source.height * 0.05f).toInt().coerceAtLeast(0)
        val width = (source.width * 0.68f).toInt()
            .coerceAtMost(source.width - left)
            .coerceAtLeast(1)
        val height = (source.height * 0.72f).toInt()
            .coerceAtMost(source.height - top)
            .coerceAtLeast(1)

        val cropped = Bitmap.createBitmap(source, left, top, width, height)
        val scaled = Bitmap.createScaledBitmap(cropped, 144, 180, true)
        if (cropped !== source) cropped.recycle()
        source.recycle()
        return scaled
    }
}
