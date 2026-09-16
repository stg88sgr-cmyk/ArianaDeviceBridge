package de.snowworks.ariana.apk

data class ApkInfo(
    val fileName: String,
    val packageName: String?,
    val versionName: String?,
    val versionCode: Long?,
    val sha256: String?,
    val signerSha256: Set<String>,
    val trustedPackage: Boolean,
    val valid: Boolean,
    val error: String? = null,
)
