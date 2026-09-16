package de.snowworks.ariana.bridge

import android.content.Context

/**
 * Persistent configuration for Universal Bridge V2.
 *
 * This store contains only durable bridge preferences and trusted package ids.
 * Secrets/tokens stay in their dedicated secure stores.
 */
class UniversalBridgeStateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val schemaVersion: Int
        get() = prefs.getInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)

    var apkManagerEnabled: Boolean
        get() = prefs.getBoolean(KEY_APK_MANAGER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_APK_MANAGER_ENABLED, value).apply()

    var watchDownloads: Boolean
        get() = prefs.getBoolean(KEY_WATCH_DOWNLOADS, true)
        set(value) = prefs.edit().putBoolean(KEY_WATCH_DOWNLOADS, value).apply()

    var restoreOnStart: Boolean
        get() = prefs.getBoolean(KEY_RESTORE_ON_START, true)
        set(value) = prefs.edit().putBoolean(KEY_RESTORE_ON_START, value).apply()

    var lastKnownBuild: String
        get() = prefs.getString(KEY_LAST_KNOWN_BUILD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_KNOWN_BUILD, value).apply()

    fun trustedPackages(): Set<String> =
        prefs.getStringSet(KEY_TRUSTED_PACKAGES, DEFAULT_TRUSTED_PACKAGES)?.toSet()
            ?: DEFAULT_TRUSTED_PACKAGES

    fun isTrustedPackage(packageName: String): Boolean = packageName in trustedPackages()

    fun addTrustedPackage(packageName: String) {
        val normalized = packageName.trim()
        if (normalized.isEmpty()) return
        prefs.edit().putStringSet(KEY_TRUSTED_PACKAGES, trustedPackages() + normalized).apply()
    }

    fun removeTrustedPackage(packageName: String) {
        prefs.edit().putStringSet(KEY_TRUSTED_PACKAGES, trustedPackages() - packageName).apply()
    }

    /** Ensures defaults exist after first launch or an app update. */
    fun ensureDefaults(currentBuild: String) {
        val editor = prefs.edit()
        if (!prefs.contains(KEY_SCHEMA_VERSION)) editor.putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
        if (!prefs.contains(KEY_APK_MANAGER_ENABLED)) editor.putBoolean(KEY_APK_MANAGER_ENABLED, true)
        if (!prefs.contains(KEY_WATCH_DOWNLOADS)) editor.putBoolean(KEY_WATCH_DOWNLOADS, true)
        if (!prefs.contains(KEY_RESTORE_ON_START)) editor.putBoolean(KEY_RESTORE_ON_START, true)
        if (!prefs.contains(KEY_TRUSTED_PACKAGES)) editor.putStringSet(KEY_TRUSTED_PACKAGES, DEFAULT_TRUSTED_PACKAGES)
        editor.putString(KEY_LAST_KNOWN_BUILD, currentBuild)
        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "ariana_universal_bridge_v2"
        private const val SCHEMA_VERSION = 1

        private const val KEY_SCHEMA_VERSION = "schema_version"
        private const val KEY_APK_MANAGER_ENABLED = "apk_manager_enabled"
        private const val KEY_WATCH_DOWNLOADS = "watch_downloads"
        private const val KEY_RESTORE_ON_START = "restore_on_start"
        private const val KEY_LAST_KNOWN_BUILD = "last_known_build"
        private const val KEY_TRUSTED_PACKAGES = "trusted_packages"

        private val DEFAULT_TRUSTED_PACKAGES = setOf(
            "de.snowworks.app",
            "de.snowworks.app.debug",
            "de.snowworks.app.safe",
            "de.snowworks.app.sideload",
            "de.snowworks.app.bootstrap",
        )
    }
}
