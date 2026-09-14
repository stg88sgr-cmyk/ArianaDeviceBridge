package de.snowworks.ariana.character

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class X88CharacterCoreTest {

    @Test
    fun identityPromptKeepsMasterTraits() {
        val prompt = X88PromptComposer.compose(X88Scenes.master)

        assertTrue(prompt.contains("ARIANA X-88"))
        assertTrue(prompt.contains("long dark hair"))
        assertTrue(prompt.contains("blue eyes"))
        assertTrue(prompt.contains("glowing geometric chest core"))
    }

    @Test
    fun generationRequestIsStableAndDeduplicatesReferences() {
        val first = X88GenerationRequestFactory.create(
            scene = X88Scenes.widget,
            aspectRatio = "9:16",
            referenceUris = listOf("content://x88/master", "content://x88/master"),
        )
        val second = X88GenerationRequestFactory.create(
            scene = X88Scenes.widget,
            aspectRatio = "9:16",
            referenceUris = listOf("content://x88/master"),
        )

        assertEquals(1, first.referenceUris.size)
        assertEquals(first.seed, second.seed)
        assertEquals(X88CharacterCore.id, first.characterId)
    }
}
