package de.snowworks.ariana.apk

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

object ApkBridgeStatus {
    fun status(context: Context): JSONObject {
        val app = context.applicationContext
        val manager = ApkManager(app)
        val latest = manager.listDownloadedApks(limit = 1).firstOrNull()
        val install = ApkInstallStatusStore(app).snapshot()

        return JSONObject()
            .put("ok", true)
            .put("model", "ariana-apk-status-v1")
            .put("canRequestPackageInstalls", canRequestPackageInstalls(app))
            .put("latestDownload", latest?.let(::candidateJson) ?: JSONObject.NULL)
            .put("lastInstall", installJson(install))
    }

    fun list(context: Context, limit: Int = 25): JSONObject {
        val manager = ApkManager(context.applicationContext)
        return JSONObject()
            .put("ok", true)
            .put("model", "ariana-apk-list-v1")
            .put("items", JSONArray(manager.listDownloadedApks(limit).map(::candidateJson)))
    }

    fun inspectLatest(context: Context): JSONObject {
        val app = context.applicationContext
        val manager = ApkManager(app)
        val candidate = manager.listDownloadedApks(limit = 1).firstOrNull()
            ?: return JSONObject()
                .put("ok", false)
                .put("error", "APK_NOT_FOUND")
                .put("message", "Keine APK im Download-Bereich gefunden.")

        return runCatching {
            val staged = manager.stage(candidate)
            try {
                val info = ApkInspector(app).inspect(staged)
                val trust = ApkTrustPolicy(app).evaluate(info)
                JSONObject()
                    .put("ok", true)
                    .put("model", "ariana-apk-inspection-v1")
                    .put("download", candidateJson(candidate))
                    .put("packageName", info.packageName)
                    .put("versionName", info.versionName)
                    .put("versionCode", info.versionCode)
                    .put("sha256", info.sha256)
                    .put("signerSha256", JSONArray(info.signerSha256.toList()))
                    .put("valid", info.valid)
                    .put("trustedPackage", info.trustedPackage)
                    .put("trustedUpdate", trust.trusted)
                    .put("trustReason", trust.reason)
            } finally {
                manager.clearStaging()
            }
        }.getOrElse { error ->
            JSONObject()
                .put("ok", false)
                .put("error", "APK_INSPECTION_FAILED")
                .put("message", error.javaClass.simpleName)
        }
    }

    private fun canRequestPackageInstalls(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            false
        }

    private fun candidateJson(candidate: ApkManager.DownloadCandidate) = JSONObject()
        .put("displayName", candidate.displayName)
        .put("sizeBytes", candidate.sizeBytes)
        .put("dateModifiedSeconds", candidate.dateModifiedSeconds)

    private fun installJson(snapshot: ApkInstallStatusStore.Snapshot) = JSONObject()
        .put("state", snapshot.state)
        .put("packageName", snapshot.packageName)
        .put("sessionId", snapshot.sessionId)
        .put("statusCode", snapshot.statusCode)
        .put("message", snapshot.message)
        .put("updatedAtMs", snapshot.updatedAtMs)
}
