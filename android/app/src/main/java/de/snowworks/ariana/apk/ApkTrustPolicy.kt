package de.snowworks.ariana.apk

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

class ApkTrustPolicy(context: Context) {
    private val appContext = context.applicationContext

    data class Result(
        val trusted: Boolean,
        val reason: String,
    )

    fun evaluate(candidate: ApkInfo): Result {
        if (!candidate.valid) return Result(false, candidate.error ?: "APK inspection failed")
        if (!candidate.trustedPackage) return Result(false, "Package id is not in the Snowworks allowlist")
        if (candidate.signerSha256.isEmpty()) return Result(false, "Candidate APK has no readable signer")

        val hostSigners = installedSignerDigests(appContext.packageName)
        if (hostSigners.isEmpty()) return Result(false, "Installed Snowworks signer could not be read")

        val signerMatches = candidate.signerSha256.any { it in hostSigners }
        return if (signerMatches) {
            Result(true, "Package id and signer continuity verified")
        } else {
            Result(false, "Signer mismatch")
        }
    }

    private fun installedSignerDigests(packageName: String): Set<String> {
        val pm = appContext.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val info = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, flags)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return emptySet()
        }

        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures ?: emptyArray()
        }

        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }.toSet()
    }
}
