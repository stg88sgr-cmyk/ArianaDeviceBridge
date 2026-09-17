package de.snowworks.ariana.prompter

data class ValidationIssue(
    val code: String,
    val path: String,
    val message: String,
)

data class ValidationResult(
    val valid: Boolean,
    val issues: List<ValidationIssue>,
)

class AppSpecValidator {
    private val idPattern = Regex("^[A-Za-z][A-Za-z0-9_]{0,63}$")
    private val packagePattern = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")

    fun validate(spec: AppSpec): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        if (spec.schemaVersion != 1) {
            issues += issue("UNSUPPORTED_SCHEMA", "schemaVersion", "Only AppSpec schemaVersion 1 is supported.")
        }
        if (spec.name.isBlank() || spec.name.length > 64) {
            issues += issue("INVALID_NAME", "name", "App name must contain 1 to 64 characters.")
        }
        if (!packagePattern.matches(spec.packageName)) {
            issues += issue("INVALID_PACKAGE", "packageName", "Package name must be a valid lowercase dotted identifier.")
        }
        if (spec.screens.isEmpty() || spec.screens.size > MAX_SCREENS) {
            issues += issue("INVALID_SCREEN_COUNT", "screens", "AppSpec must contain 1 to $MAX_SCREENS screens.")
        }

        val screenIds = spec.screens.map { it.id }
        if (screenIds.toSet().size != screenIds.size) {
            issues += issue("DUPLICATE_SCREEN_ID", "screens", "Screen ids must be unique.")
        }

        val totalComponents = spec.screens.sumOf { it.components.size }
        if (totalComponents > MAX_COMPONENTS) {
            issues += issue("TOO_MANY_COMPONENTS", "screens", "AppSpec may contain at most $MAX_COMPONENTS components.")
        }

        val knownScreens = screenIds.toSet()
        spec.screens.forEachIndexed { screenIndex, screen ->
            val screenPath = "screens[$screenIndex]"
            if (!idPattern.matches(screen.id)) {
                issues += issue("INVALID_SCREEN_ID", "$screenPath.id", "Screen id must be a stable identifier.")
            }
            if (screen.title.isBlank() || screen.title.length > 80) {
                issues += issue("INVALID_SCREEN_TITLE", "$screenPath.title", "Screen title must contain 1 to 80 characters.")
            }

            val componentIds = screen.components.map { it.id }
            if (componentIds.toSet().size != componentIds.size) {
                issues += issue("DUPLICATE_COMPONENT_ID", "$screenPath.components", "Component ids must be unique within a screen.")
            }

            screen.components.forEachIndexed { componentIndex, component ->
                val componentPath = "$screenPath.components[$componentIndex]"
                if (!idPattern.matches(component.id)) {
                    issues += issue("INVALID_COMPONENT_ID", "$componentPath.id", "Component id must be a stable identifier.")
                }
                if (component.text != null && component.text.length > 240) {
                    issues += issue("TEXT_TOO_LONG", "$componentPath.text", "Component text may contain at most 240 characters.")
                }
                if (component.type == ComponentType.BUTTON && component.action == null) {
                    issues += issue("BUTTON_WITHOUT_ACTION", componentPath, "Buttons require an action.")
                }

                val action = component.action
                if (action?.type == ActionType.NAVIGATE) {
                    val target = action.target
                    if (target.isNullOrBlank() || target !in knownScreens) {
                        issues += issue("INVALID_NAVIGATION_TARGET", "$componentPath.action.target", "Navigation target must name an existing screen.")
                    }
                }
                if (action?.type in setOf(ActionType.SET_STATE, ActionType.TOGGLE, ActionType.RESET) && component.stateKey.isNullOrBlank()) {
                    issues += issue("STATE_KEY_REQUIRED", componentPath, "This action requires a stateKey.")
                }
            }
        }

        return ValidationResult(valid = issues.isEmpty(), issues = issues)
    }

    private fun issue(code: String, path: String, message: String) =
        ValidationIssue(code = code, path = path, message = message)

    companion object {
        const val MAX_SCREENS = 5
        const val MAX_COMPONENTS = 20
    }
}
