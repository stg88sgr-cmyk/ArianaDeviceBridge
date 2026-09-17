package de.snowworks.ariana.prompter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildAutomationTest {
    @Test
    fun buildRunnerUsesDeterministicGradleGate() {
        val project = ComposeProjectGenerator().generate(
            PromptToAppSpecCompiler().compile("Baue eine Listen App"),
        )
        var observed: BuildExecutionRequest? = null
        val executor = BuildExecutor { request ->
            observed = request
            BuildExecutionResult(
                success = true,
                exitCode = 0,
                stage = BuildStage.COMPLETE,
                artifacts = listOf("app/build/outputs/apk/debug/app-debug.apk"),
            )
        }

        val result = X88BuildRunner().build(project, executor)

        assertTrue(result.success)
        assertEquals(1, observed?.attempt)
        assertEquals("./gradlew", observed?.command?.first())
        assertTrue(observed?.command?.contains(":app:testDebugUnitTest") == true)
        assertTrue(observed?.command?.contains("lintDebug") == true)
        assertTrue(observed?.command?.contains(":app:assembleDebug") == true)
    }

    @Test
    fun repairLoopRepairsOnceThenReturnsGreen() {
        val initial = ComposeProjectGenerator().generate(
            PromptToAppSpecCompiler().compile("Baue eine Listen App"),
        )
        var executions = 0
        val executor = BuildExecutor { request ->
            executions += 1
            if (request.attempt == 1) {
                BuildExecutionResult(
                    success = false,
                    exitCode = 1,
                    stage = BuildStage.ASSEMBLE,
                    stderr = "synthetic compiler error",
                )
            } else {
                BuildExecutionResult(
                    success = true,
                    exitCode = 0,
                    stage = BuildStage.COMPLETE,
                    artifacts = listOf("app/build/outputs/apk/debug/app-debug.apk"),
                )
            }
        }
        val strategy = GeneratedProjectRepairStrategy { project, _, _ ->
            val files = project.files.toMutableMap()
            files["gradle.properties"] = files.getValue("gradle.properties") + "x88.repaired=true\n"
            RepairDecision.Patched(
                project = GeneratedProject(project.plan, files),
                note = "Applied deterministic test repair.",
            )
        }

        val result = X88RepairLoop(maxAttempts = 3).run(initial, executor, strategy)

        assertTrue(result is AutoBuildResult.Success)
        result as AutoBuildResult.Success
        assertEquals(2, executions)
        assertEquals(2, result.history.size)
        assertEquals("Applied deterministic test repair.", result.history.first().repairNote)
        assertTrue(result.project.files.getValue("gradle.properties").contains("x88.repaired=true"))
    }

    @Test
    fun repairLoopStopsWithoutFabricatingSuccess() {
        val initial = ComposeProjectGenerator().generate(
            PromptToAppSpecCompiler().compile("Baue eine Listen App"),
        )
        val executor = BuildExecutor {
            BuildExecutionResult(
                success = false,
                exitCode = 1,
                stage = BuildStage.TEST,
                stderr = "real failure",
            )
        }
        val strategy = GeneratedProjectRepairStrategy { _, _, _ ->
            RepairDecision.Stop("No safe repair available.")
        }

        val result = X88RepairLoop().run(initial, executor, strategy)

        assertTrue(result is AutoBuildResult.Failed)
        result as AutoBuildResult.Failed
        assertEquals("No safe repair available.", result.reason)
        assertEquals(1, result.history.size)
    }
}
