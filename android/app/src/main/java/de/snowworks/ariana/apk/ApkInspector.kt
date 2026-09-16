package de.snowworks.ariana.apk

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import de.snowworks.ariana.bridge.UniversalBridgeStateStore
import java.io.File
import java.security.MessageDigest

class ApkInspector(
    context: Context,
    private val stateStore: UniversalBridgeStateStore = UniversalBridgeStateStore(context),
) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    fun inspect(file: File): ApkInfo {
        if (!file.isFile || !file.name.endsWith(".apk", ignoreCase = true)) {
            return invalid(file, "Not an APK file")
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val packageInfo = packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: return invalid(file, "Android could not parse the APK")

        val packageName = packageInfo.packageName
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        val signerDigests = runCatching { signerDigests(packageInfo) }.getOrElse { emptySet() }
        val fileDigest = runCatching { sha256(file) }.getOrNull()

        return ApkInfo(
            fileName = file.name,
            packageName = packageName,
            versionName = packageInfo.versionName,
            versionCode = versionCode,
            sha256 = fileDigest,
            signerSha256 = signerDigests,
            trustedPackage = stateStore.isTrustedPackage(packageName),
            valid = fileDigest != null && packageName.isNotBlank() && signerDigests.isNotEmpty(),
            error = when {
                fileDigest == null -> "Unable to hash APK"
                signerDigests.isEmpty() -> "APK signer could not be read"
                else -> null
            },
        )
    }

    private fun signerDigests(packageInfo: android.content.pm.PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures ?: emptyArray()
        }

        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }.toSet()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun invalid(file: File, reason: String) = ApkInfo(
        fileName = file.name,
        packageName = null,
        versionName = null,
        versionCode = null,
        sha256 = null,
        signerSha256 = emptySet(),
        trustedPackage = false,
        valid = false,
        error = reason,
    )
}
