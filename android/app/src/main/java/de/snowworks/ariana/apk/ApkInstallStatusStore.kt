package de.snowworks.ariana.apk

import android.content.Context

class ApkInstallStatusStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class Snapshot(
        val state: String,
        val packageName: String?,
        val sessionId: Int?,
        val statusCode: Int?,
        val message: String?,
        val updatedAtMs: Long,
    )

    fun snapshot(): Snapshot = Snapshot(
        state = prefs.getString(KEY_STATE, STATE_IDLE) ?: STATE_IDLE,
        packageName = prefs.getString(KEY_PACKAGE_NAME, null),
        sessionId = prefs.getInt(KEY_SESSION_ID, -1).takeIf { it >= 0 },
        statusCode = prefs.getInt(KEY_STATUS_CODE, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
        message = prefs.getString(KEY_MESSAGE, null),
        updatedAtMs = prefs.getLong(KEY_UPDATED_AT, 0L),
    )

    fun markStarted(packageName: String, sessionId: Int) = write(
        state = STATE_STARTED,
        packageName = packageName,
        sessionId = sessionId,
        statusCode = null,
        message = null,
    )

    fun markPendingUserAction(packageName: String?, sessionId: Int?, message: String?) = write(
        state = STATE_PENDING_USER_ACTION,
        packageName = packageName,
        sessionId = sessionId,
        statusCode = android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION,
        message = message,
    )

    fun markSuccess(packageName: String?, sessionId: Int?, message: String?) = write(
        state = STATE_SUCCESS,
        packageName = packageName,
        sessionId = sessionId,
        statusCode = android.content.pm.PackageInstaller.STATUS_SUCCESS,
        message = message,
    )

    fun markFailure(packageName: String?, sessionId: Int?, statusCode: Int, message: String?) = write(
        state = STATE_FAILURE,
        packageName = packageName,
        sessionId = sessionId,
        statusCode = statusCode,
        message = message,
    )

    private fun write(
        state: String,
        packageName: String?,
        sessionId: Int?,
        statusCode: Int?,
        message: String?,
    ) {
        prefs.edit()
            .putString(KEY_STATE, state)
            .putString(KEY_PACKAGE_NAME, packageName)
            .putInt(KEY_SESSION_ID, sessionId ?: -1)
            .putInt(KEY_STATUS_CODE, statusCode ?: Int.MIN_VALUE)
            .putString(KEY_MESSAGE, message)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    companion object {
        const val STATE_IDLE = "idle"
        const val STATE_STARTED = "started"
        const val STATE_PENDING_USER_ACTION = "pending_user_action"
        const val STATE_SUCCESS = "success"
        const val STATE_FAILURE = "failure"

        private const val PREFS_NAME = "ariana_apk_install_status"
        private const val KEY_STATE = "state"
        private const val KEY_PACKAGE_NAME = "package_name"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_STATUS_CODE = "status_code"
        private const val KEY_MESSAGE = "message"
        private const val KEY_UPDATED_AT = "updated_at_ms"
    }
}
