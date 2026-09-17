package de.snowworks.ariana.system

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class X88UpdateVerifier(
    private val updateManager: X88UpdateManager = X88UpdateManager(),
) {
    fun verify(candidate: X88UpdateDescriptor, artifact: File): Boolean {
        if (!updateManager.isEligible(candidate) || !artifact.isFile) return false
        return sha256(artifact).equals(candidate.sha256, ignoreCase = true)
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
