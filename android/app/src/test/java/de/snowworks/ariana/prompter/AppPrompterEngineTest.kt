package de.snowworks.ariana.prompter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPrompterEngineTest {
    @Test
    fun generatesAnAppSpecFromPrompt() {
        val result = AppPrompterEngine.generate(
            "Baue eine App namens Luna Notes mit Startseite, Notizen speichern und dunklem Design."
        )

        assertEquals("Luna Notes", result.appName)
        assertTrue(result.features.any { it.contains("Notizen", ignoreCase = true) })
        assertTrue(result.html.contains("<title>Luna Notes</title>"))
        assertTrue(result.html.contains("Notizen"))
        assertTrue(result.html.contains("Dunkles Design"))
    }

    @Test
    fun keepsTheOriginalPromptWhenItCannotInferAName() {
        val prompt = "Erstelle etwas Neues"
        val result = AppPrompterEngine.generate(prompt)

        assertEquals("Snowworks App", result.appName)
        assertEquals(prompt, result.originalPrompt)
        assertTrue(result.html.contains("Snowworks App"))
    }
}
