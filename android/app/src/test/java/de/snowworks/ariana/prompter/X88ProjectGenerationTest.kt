package de.snowworks.ariana.prompter

import de.snowworks.ariana.system.X88Capability
import de.snowworks.ariana.system.X88Core
import org.junit.Assert.assertTrue
import org.junit.Test

class X88ProjectGenerationTest {
    @Test
    fun x88GeneratesProjectOnlyAfterCapabilityGateOpens() {
        val core = X88Core()
        val sessionId = core.startSession()
        val prompter = X88AppPrompter(core)

        assertTrue(
            prompter.generateProject(sessionId, "Baue eine Listen App") is
                AppProjectGenerationResult.Denied,
        )

        core.capabilities.setMasterEnabled(true)
        core.capabilities.enable(X88Capability.APP_GENERATION)

        val result = prompter.generateProject(
            sessionId,
            "Baue eine Inventar App mit Liste und Rechner",
        )

        assertTrue(result is AppProjectGenerationResult.Generated)
        result as AppProjectGenerationResult.Generated
        assertTrue(result.project.files.containsKey("settings.gradle.kts"))
        assertTrue(result.project.files.keys.any { it.endsWith("/MainActivity.kt") })
    }
}
