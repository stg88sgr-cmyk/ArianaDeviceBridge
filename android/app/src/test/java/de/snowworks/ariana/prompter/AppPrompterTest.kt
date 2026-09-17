package de.snowworks.ariana.prompter

import de.snowworks.ariana.system.X88Capability
import de.snowworks.ariana.system.X88Core
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPrompterTest {
    @Test
    fun compilerProducesValidBoundedSpec() {
        val compiler = PromptToAppSpecCompiler()
        val validator = AppSpecValidator()

        val spec = compiler.compile(
            "Baue eine Inventar App mit Liste, Formular, Einstellungen und Rechner",
        )
        val validation = validator.validate(spec)

        assertTrue(validation.issues.joinToString { it.code }, validation.valid)
        assertTrue(spec.screens.size <= AppSpecValidator.MAX_SCREENS)
        assertTrue(spec.screens.sumOf { it.components.size } <= AppSpecValidator.MAX_COMPONENTS)
        assertTrue(spec.packageName.startsWith("de.snowworks.generated."))
    }

    @Test
    fun validatorRejectsUnknownNavigationTarget() {
        val spec = AppSpec(
            name = "Broken",
            packageName = "de.snowworks.generated.broken",
            description = "test",
            screens = listOf(
                ScreenSpec(
                    id = "main",
                    title = "Main",
                    components = listOf(
                        ComponentSpec(
                            id = "go",
                            type = ComponentType.BUTTON,
                            text = "Go",
                            action = ActionSpec(
                                type = ActionType.NAVIGATE,
                                target = "missing",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val validation = AppSpecValidator().validate(spec)

        assertFalse(validation.valid)
        assertTrue(validation.issues.any { it.code == "INVALID_NAVIGATION_TARGET" })
    }

    @Test
    fun x88GateDeniesThenAllowsAppGeneration() {
        val core = X88Core()
        val sessionId = core.startSession()
        val prompter = X88AppPrompter(core)

        assertTrue(prompter.compile(sessionId, "Baue eine Listen App") is AppPrompterResult.Denied)

        core.capabilities.setMasterEnabled(true)
        core.capabilities.enable(X88Capability.APP_GENERATION)

        val allowed = prompter.compile(sessionId, "Baue eine Listen App")
        assertTrue(allowed is AppPrompterResult.Accepted)
        allowed as AppPrompterResult.Accepted
        assertEquals("de.snowworks.generated.baueeinelistenapp", allowed.spec.packageName)
    }
}
