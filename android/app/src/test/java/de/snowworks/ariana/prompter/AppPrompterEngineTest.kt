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

    @Test
    fun generatedProjectContainsManifestAndSourceFiles() {
        val project = AppPrompterEngine.generateProject("Baue eine App namens Luna Notes mit Startseite und Notizen.")
        assertEquals("Luna Notes", project.spec.appName)
        assertTrue(project.files.containsKey("project.json"))
        assertTrue(project.files.containsKey("index.html"))
        assertTrue(project.files.containsKey("README.txt"))
    }

    @Test
    fun generatesMultipleScreensAndNavigationFromPrompt() {
        val project = AppPrompterEngine.generateProject(
            "Baue eine App namens Luna Notes mit Startseite, Notizen und Einstellungen."
        )

        assertTrue(project.files.containsKey("screens/home.html"))
        assertTrue(project.files.containsKey("screens/notes.html"))
        assertTrue(project.files.containsKey("screens/settings.html"))
        assertTrue(project.files["index.html"]!!.contains("screens/notes.html"))
        assertTrue(project.files["index.html"]!!.contains("screens/settings.html"))
        assertTrue(project.files["screens/notes.html"]!!.contains("Luna Notes"))
    }
}
