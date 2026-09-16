package de.snowworks.ariana.apk

import android.content.Context
import android.os.Build

data class ApkHealthResult(
    val ok: Boolean,
    val checks: List<Check>,
) {
    data class Check(
        val name: String,
        val ok: Boolean,
        val detail: String,
        val skipped: Boolean = false,
    )
}

object ApkHealthCheck {
    fun run(context: Context): ApkHealthResult {
        val app = context.applicationContext
        val checks = mutableListOf<ApkHealthResult.Check>()

        val sourceReady = ApkInstallSourceController.isReady(app)
        checks += ApkHealthResult.Check(
            name = "Installationsquelle",
            ok = sourceReady,
            detail = if (sourceReady) {
                "Diese App darf Android-Installationssessions anfordern."
            } else {
                "Android-Freigabe für diese Installationsquelle fehlt."
            },
        )

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            checks += ApkHealthResult.Check(
                name = "Download-Erkennung",
                ok = true,
                skipped = true,
                detail = "MediaStore.Downloads wird erst ab Android 10 für diesen Pfad verwendet.",
            )
            return ApkHealthResult(checks.all { it.ok || it.skipped }, checks)
        }

        val manager = ApkManager(app)
        val latest = runCatching { manager.listDownloadedApks(limit = 1).firstOrNull() }.getOrElse { error ->
            checks += ApkHealthResult.Check(
                name = "Download-Erkennung",
                ok = false,
                detail = "Download-Abfrage fehlgeschlagen: ${error.javaClass.simpleName}",
            )
            return ApkHealthResult(false, checks)
        }

        if (latest == null) {
            checks += ApkHealthResult.Check(
                name = "Download-Erkennung",
                ok = true,
                detail = "Download-Abfrage funktioniert; aktuell keine APK vorhanden.",
            )
            checks += ApkHealthResult.Check(
                name = "APK-Prüfung",
                ok = true,
                skipped = true,
                detail = "Keine APK zum Prüfen vorhanden.",
            )
            return ApkHealthResult(checks.all { it.ok || it.skipped }, checks)
        }

        checks += ApkHealthResult.Check(
            name = "Download-Erkennung",
            ok = true,
            detail = "Gefunden: ${latest.displayName} (${latest.sizeBytes} Bytes)",
        )

        val inspection = runCatching {
            val staged = manager.stage(latest)
            try {
                val info = ApkInspector(app).inspect(staged)
                val trust = ApkTrustPolicy(app).evaluate(info)
                Triple(info, trust, staged.length())
            } finally {
                manager.clearStaging()
            }
        }

        inspection.onSuccess { (info, trust, stagedSize) ->
            checks += ApkHealthResult.Check(
                name = "APK-Prüfung",
                ok = info.valid,
                detail = "Paket=${info.packageName ?: "?"} · Version=${info.versionName ?: "?"} · staged=$stagedSize · valid=${info.valid}",
            )
            checks += ApkHealthResult.Check(
                name = "Signer-Trust",
                ok = trust.trusted,
                detail = trust.reason,
            )
        }.onFailure { error ->
            checks += ApkHealthResult.Check(
                name = "APK-Prüfung",
                ok = false,
                detail = "Prüfung fehlgeschlagen: ${error.javaClass.simpleName}",
            )
        }

        return ApkHealthResult(checks.all { it.ok || it.skipped }, checks)
    }
}
