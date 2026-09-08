package de.snowworks.ariana.files

import android.content.Context
import android.net.Uri

/** Stores only the SAF tree URI explicitly selected by the user. */
class TreePermissionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(uri: Uri) {
        prefs.edit().putString(KEY_URI, uri.toString()).apply()
    }

    fun get(): Uri? = prefs.getString(KEY_URI, null)?.let(Uri::parse)

    fun clear() {
        prefs.edit().remove(KEY_URI).apply()
    }

    companion object {
        private const val PREFS = "ariana_files"
        private const val KEY_URI = "tree_uri"
    }
}
