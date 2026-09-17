package de.snowworks.ariana.image

import de.snowworks.ariana.image.model.GeneratorBackend
import de.snowworks.ariana.image.storage.PrivateMediaStore
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateMediaStoreTest {
    @Test fun savesListsDeletesAndCleansTemporaryFiles() {
        val dir = Files.createTempDirectory("private-images-test").toFile()
        try {
            val store = PrivateMediaStore(dir)
            val image = store.save("req-1", byteArrayOf(1, 2, 3), 64, 64, GeneratorBackend.LOCAL_DIFFUSION, null)
            assertTrue(image.absolutePath.startsWith(dir.absolutePath))
            assertNotNull(store.get(image.id))
            assertEquals(1, store.list().size)
            assertFalse(store.delete("../escape"))
            val tmp = java.io.File(dir, "deadbeef.tmp.${java.util.UUID.randomUUID()}")
            tmp.writeText("partial")
            assertTrue(tmp.exists())
            assertEquals(2, store.deleteAll())
            assertEquals(0, store.list().size)
            assertFalse(tmp.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
