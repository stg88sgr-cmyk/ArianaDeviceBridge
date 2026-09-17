package de.snowworks.ariana.prompter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectGenerationTest {
    @Test
    fun plannerProducesDeterministicAndroidProjectLayout() {
        val spec = PromptToAppSpecCompiler().compile(
            "Baue eine Inventar App mit Liste, Formular, Einstellungen und Rechner",
        )

        val plan = ProjectPlanner().plan(spec)

        assertEquals(spec.packageName, plan.packageName)
        assertEquals(spec.screens.first().id, plan.entryScreenId)
        assertEquals(6, plan.files.size)
        assertEquals(plan.files.size, plan.files.map { it.path }.distinct().size)
        assertTrue(plan.files.any { it.path == "settings.gradle.kts" })
        assertTrue(plan.files.any { it.path == "app/build.gradle.kts" })
        assertTrue(
            plan.files.any {
                it.path == "app/src/main/java/${spec.packageName.replace('.', '/')}/MainActivity.kt"
            },
        )
    }

    @Test
    fun plannerRefusesInvalidSpec() {
        val bad = AppSpec(
            name = "Bad",
            packageName = "Not.A.Valid.Package",
            description = "invalid",
            screens = listOf(ScreenSpec("main", "Main", emptyList())),
        )

        assertThrows(IllegalArgumentException::class.java) {
            ProjectPlanner().plan(bad)
        }
    }

    @Test
    fun generatorRendersCompleteComposeProject() {
        val spec = PromptToAppSpecCompiler().compile(
            "Baue eine Inventar App mit Liste, Formular, Einstellungen und Rechner",
        )

        val generated = ComposeProjectGenerator().generate(spec)
        val mainPath = "app/src/main/java/${spec.packageName.replace('.', '/')}/MainActivity.kt"
        val main = generated.files.getValue(mainPath)
        val appBuild = generated.files.getValue("app/build.gradle.kts")
        val manifest = generated.files.getValue("app/src/main/AndroidManifest.xml")

        assertEquals(generated.plan.files.map { it.path }.toSet(), generated.files.keys)
        assertTrue(main.contains("setContent { MaterialTheme { GeneratedApp() } }"))
        assertTrue(main.contains("currentScreen = \"list\""))
        assertTrue(main.contains("left + right"))
        assertTrue(main.contains("LazyColumn"))
        assertTrue(appBuild.contains("buildFeatures { compose = true }"))
        assertTrue(appBuild.contains("targetSdk = 36"))
        assertTrue(manifest.contains("android.intent.category.LAUNCHER"))
        assertFalse(manifest.contains("REQUEST_INSTALL_PACKAGES"))
    }

    @Test
    fun generationIsDeterministicForSameSpec() {
        val spec = PromptToAppSpecCompiler().compile("Baue eine Listen App")
        val generator = ComposeProjectGenerator()

        assertEquals(generator.generate(spec), generator.generate(spec))
    }
}
