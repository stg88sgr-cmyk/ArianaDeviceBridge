package de.snowworks.ariana.system

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class X88UpdateVerifierTest {

    @Test
    fun verifiesMatchingShaForEligibleUpdate() {
        val file = File.createTempFile("x88-update", ".bin")
        try {
            file.writeText("x88-test-artifact")
            val verifier = X88UpdateVerifier(
                X88UpdateManager(currentVersion = X88Version(1, 0, 0, X88UpdateChannel.DEV)),
            )
            val hash = verifier.sha256(file)
            val descriptor = X88UpdateDescriptor(
                version = X88Version(1, 0, 1, X88UpdateChannel.DEV),
                artifactUrl = "https://example.invalid/x88.bin",
                sha256 = hash,
            )

            assertTrue(verifier.verify(descriptor, file))
            assertFalse(verifier.verify(descriptor.copy(sha256 = "0".repeat(64)), file))
        } finally {
            file.delete()
        }
    }
}
