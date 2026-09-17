package de.snowworks.ariana.prompter

import de.snowworks.ariana.system.X88Capability
import de.snowworks.ariana.system.X88Core

sealed interface AppPrompterResult {
    data class Accepted(
        val spec: AppSpec,
        val validation: ValidationResult,
    ) : AppPrompterResult

    data class Rejected(
        val reason: String,
        val validation: ValidationResult? = null,
    ) : AppPrompterResult

    data class Denied(
        val reason: String,
    ) : AppPrompterResult
}

class X88AppPrompter(
    private val core: X88Core,
    private val compiler: PromptToAppSpecCompiler = PromptToAppSpecCompiler(),
    private val validator: AppSpecValidator = AppSpecValidator(),
) {
    fun compile(sessionId: String?, prompt: String): AppPrompterResult {
        if (!core.canExecute(X88Capability.APP_GENERATION, sessionId)) {
            return AppPrompterResult.Denied(
                reason = "APP_GENERATION is not enabled for the active X88 session.",
            )
        }

        val spec = try {
            compiler.compile(prompt)
        } catch (error: IllegalArgumentException) {
            return AppPrompterResult.Rejected(
                reason = error.message ?: "Prompt could not be compiled.",
            )
        }

        val validation = validator.validate(spec)
        return if (validation.valid) {
            AppPrompterResult.Accepted(spec = spec, validation = validation)
        } else {
            AppPrompterResult.Rejected(
                reason = "Generated AppSpec failed validation.",
                validation = validation,
            )
        }
    }
}
