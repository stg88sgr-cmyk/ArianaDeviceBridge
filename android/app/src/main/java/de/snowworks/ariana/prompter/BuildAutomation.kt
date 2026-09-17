package de.snowworks.ariana.prompter

enum class BuildStage {
    TEST,
    LINT,
    ASSEMBLE,
    COMPLETE,
}

data class BuildExecutionRequest(
    val project: GeneratedProject,
    val attempt: Int,
    val command: List<String>,
)

data class BuildExecutionResult(
    val success: Boolean,
    val exitCode: Int,
    val stage: BuildStage,
    val stdout: String = "",
    val stderr: String = "",
    val artifacts: List<String> = emptyList(),
)

fun interface BuildExecutor {
    fun execute(request: BuildExecutionRequest): BuildExecutionResult
}

class X88BuildRunner(
    private val command: List<String> = listOf(
        "./gradlew",
        ":app:testDebugUnitTest",
        "lintDebug",
        ":app:assembleDebug",
        "--stacktrace",
    ),
) {
    init {
        require(command.isNotEmpty()) { "Build command must not be empty." }
    }

    fun build(
        project: GeneratedProject,
        executor: BuildExecutor,
        attempt: Int = 1,
    ): BuildExecutionResult {
        require(attempt > 0) { "Build attempt must be positive." }
        return executor.execute(
            BuildExecutionRequest(
                project = project,
                attempt = attempt,
                command = command,
            ),
        )
    }
}

sealed interface RepairDecision {
    data class Patched(
        val project: GeneratedProject,
        val note: String,
    ) : RepairDecision

    data class Stop(
        val reason: String,
    ) : RepairDecision
}

fun interface GeneratedProjectRepairStrategy {
    fun repair(
        project: GeneratedProject,
        failure: BuildExecutionResult,
        attempt: Int,
    ): RepairDecision
}

data class RepairAttempt(
    val attempt: Int,
    val build: BuildExecutionResult,
    val repairNote: String? = null,
)

sealed interface AutoBuildResult {
    data class Success(
        val project: GeneratedProject,
        val build: BuildExecutionResult,
        val history: List<RepairAttempt>,
    ) : AutoBuildResult

    data class Failed(
        val project: GeneratedProject,
        val build: BuildExecutionResult,
        val history: List<RepairAttempt>,
        val reason: String,
    ) : AutoBuildResult
}

class X88RepairLoop(
    private val runner: X88BuildRunner = X88BuildRunner(),
    private val maxAttempts: Int = 3,
) {
    init {
        require(maxAttempts in 1..5) { "Repair loop must use 1 to 5 attempts." }
    }

    fun run(
        initialProject: GeneratedProject,
        executor: BuildExecutor,
        repairStrategy: GeneratedProjectRepairStrategy,
    ): AutoBuildResult {
        var current = initialProject
        val history = mutableListOf<RepairAttempt>()

        for (attempt in 1..maxAttempts) {
            val build = runner.build(current, executor, attempt)
            if (build.success) {
                history += RepairAttempt(attempt = attempt, build = build)
                return AutoBuildResult.Success(
                    project = current,
                    build = build,
                    history = history.toList(),
                )
            }

            if (attempt == maxAttempts) {
                history += RepairAttempt(attempt = attempt, build = build)
                return AutoBuildResult.Failed(
                    project = current,
                    build = build,
                    history = history.toList(),
                    reason = "Build remained red after $maxAttempts attempts.",
                )
            }

            when (val decision = repairStrategy.repair(current, build, attempt)) {
                is RepairDecision.Patched -> {
                    require(decision.project.plan == current.plan) {
                        "Repair strategies may modify generated file contents but not the approved project plan."
                    }
                    history += RepairAttempt(
                        attempt = attempt,
                        build = build,
                        repairNote = decision.note,
                    )
                    current = decision.project
                }

                is RepairDecision.Stop -> {
                    history += RepairAttempt(attempt = attempt, build = build)
                    return AutoBuildResult.Failed(
                        project = current,
                        build = build,
                        history = history.toList(),
                        reason = decision.reason,
                    )
                }
            }
        }

        error("Repair loop exhausted unexpectedly.")
    }
}
