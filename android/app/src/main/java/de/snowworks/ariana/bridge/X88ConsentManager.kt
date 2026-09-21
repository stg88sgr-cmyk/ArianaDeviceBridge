package de.snowworks.ariana.bridge

import android.content.Context

/**
 * Explicit X88 data-use gate.
 *
 * Provider activation never grants access to sensitive data. Public/code
 * content may be routed according to the normal provider policy. Sensitive
 * and optional personal/device content requires an explicit user grant.
 */
class X88ConsentManager(context: Context) {
    enum class DataClass { PUBLIC, CODE, OPTIONAL, SENSITIVE, LOCAL_ONLY, NOT_REQUIRED }
    enum class ConsentDecision { ALLOW_LOCAL, ALLOW_PROVIDER, DENY }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setMetaEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_META_ENABLED, enabled).apply()
    }

    fun isMetaEnabled(): Boolean = prefs.getBoolean(KEY_META_ENABLED, true)

    fun setAllowOptional(allowed: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_OPTIONAL, allowed).apply()
    }

    fun setAllowSensible(allowed: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_SENSITIVE, allowed).apply()
    }

    fun evaluate(dataClass: DataClass, providerId: String): ConsentDecision {
        if (dataClass == DataClass.LOCAL_ONLY) return ConsentDecision.ALLOW_LOCAL
        if (!providerId.startsWith("meta", ignoreCase = true)) return ConsentDecision.ALLOW_PROVIDER
        if (!isMetaEnabled()) return ConsentDecision.DENY

        return when (dataClass) {
            DataClass.OPTIONAL -> if (prefs.getBoolean(KEY_ALLOW_OPTIONAL, false)) ConsentDecision.ALLOW_PROVIDER else ConsentDecision.DENY
            DataClass.SENSITIVE -> if (prefs.getBoolean(KEY_ALLOW_SENSITIVE, false)) ConsentDecision.ALLOW_PROVIDER else ConsentDecision.DENY
            DataClass.PUBLIC, DataClass.CODE, DataClass.NOT_REQUIRED -> ConsentDecision.ALLOW_PROVIDER
            DataClass.LOCAL_ONLY -> ConsentDecision.ALLOW_LOCAL
        }
    }

    fun evaluateCloudPolicy(classification: CloudAiPolicy.Classification, providerId: String): ConsentDecision {
        val dataClass = when (classification) {
            CloudAiPolicy.Classification.DEVICE_SENSITIVE -> DataClass.LOCAL_ONLY
            CloudAiPolicy.Classification.SECRET,
            CloudAiPolicy.Classification.PERSONAL -> DataClass.SENSITIVE
            CloudAiPolicy.Classification.CODE -> DataClass.CODE
            CloudAiPolicy.Classification.PUBLIC -> DataClass.PUBLIC
        }
        return evaluate(dataClass, providerId)
    }

    companion object {
        private const val PREFS = "x88_consent"
        private const val KEY_META_ENABLED = "meta_enabled"
        private const val KEY_ALLOW_OPTIONAL = "allow_optional"
        private const val KEY_ALLOW_SENSITIVE = "allow_sensitive"
    }
}
