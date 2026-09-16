package de.snowworks.ariana.apk

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import java.io.File

class ApkInstaller(
    context: Context,
    private val inspector: ApkInspector = ApkInspector(context),
    private val trustPolicy: ApkTrustPolicy = ApkTrustPolicy(context),
) {
    private val appContext = context.applicationContext
    private val packageInstaller = appContext.packageManager.packageInstaller

    data class StartResult(
        val ok: Boolean,
        val sessionId: Int? = null,
        val packageName: String? = null,
        val reason: String? = null,
    )

    fun startTrustedInstall(apkFile: File): StartResult {
        val info = inspector.inspect(apkFile)
        val trust = trustPolicy.evaluate(info)
        if (!trust.trusted) {
            return StartResult(false, packageName = info.packageName, reason = trust.reason)
        }

        val packageName = info.packageName
            ?: return StartResult(false, reason = "APK package name missing")

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            setSize(apkFile.length())
        }

        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        try {
            apkFile.inputStream().buffered().use { input ->
                session.openWrite("base.apk", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }

            val callbackIntent = Intent(appContext, ApkInstallReceiver::class.java).apply {
                action = ApkInstallReceiver.ACTION_INSTALL_STATUS
                putExtra(ApkInstallReceiver.EXTRA_PACKAGE_NAME, packageName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                sessionId,
                callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pendingIntent.intentSender)
            return StartResult(true, sessionId, packageName, trust.reason)
        } catch (error: Throwable) {
            runCatching { session.abandon() }
            return StartResult(false, sessionId, packageName, error.javaClass.simpleName)
        } finally {
            session.close()
        }
    }
}
