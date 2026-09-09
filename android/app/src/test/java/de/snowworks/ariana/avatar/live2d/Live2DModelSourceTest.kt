package de.snowworks.ariana.avatar.live2d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Live2DModelSourceTest {

    @Test
    fun existingPathsReturnsOnlyFilesPresentInSource() {
        val bundle = Live2DModelBundle.arianaV1()
        val missing = bundle.expressionPaths.getValue("stern")
        val present = bundle.requiredPaths() - missing
        val source = RecordingSource(present.associateWith { it.encodeToByteArray() })

        val existing = source.existingPaths(bundle)

        assertEquals(present, existing)
        assertFalse(missing in existing)
        assertEquals(bundle.requiredPaths(), source.probed.toSet())
    }

    @Test
    fun readTextUsesUtf8Bytes() {
        val source = RecordingSource(mapOf("hello.txt" to "Ariana 💜".encodeToByteArray()))

        assertEquals("Ariana 💜", source.readText("hello.txt"))
        assertTrue(source.exists("hello.txt"))
    }

    private class RecordingSource(
        private val files: Map<String, ByteArray>,
    ) : Live2DModelSource {
        val probed = mutableListOf<String>()

        override fun exists(path: String): Boolean {
            probed += path
            return path in files
        }

        override fun readBytes(path: String): ByteArray =
            files[path] ?: error("Missing test asset: $path")
    }
}
