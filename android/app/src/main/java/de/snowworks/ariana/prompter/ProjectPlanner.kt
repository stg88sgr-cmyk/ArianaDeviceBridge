package de.snowworks.ariana.prompter

enum class ProjectFileRole {
    SETTINGS_GRADLE,
    ROOT_BUILD_GRADLE,
    GRADLE_PROPERTIES,
    APP_BUILD_GRADLE,
    ANDROID_MANIFEST,
    MAIN_ACTIVITY,
}

data class PlannedProjectFile(
    val path: String,
    val role: ProjectFileRole,
)

data class ProjectPlan(
    val projectName: String,
    val packageName: String,
    val packagePath: String,
    val entryScreenId: String,
    val compileSdk: Int = 36,
    val targetSdk: Int = 36,
    val minSdk: Int = 28,
    val files: List<PlannedProjectFile>,
)

class ProjectPlanner(
    private val validator: AppSpecValidator = AppSpecValidator(),
) {
    fun plan(spec: AppSpec): ProjectPlan {
        val validation = validator.validate(spec)
        require(validation.valid) {
            "AppSpec is invalid: " + validation.issues.joinToString { "${it.code}@${it.path}" }
        }

        val packagePath = spec.packageName.replace('.', '/')
        val files = listOf(
            PlannedProjectFile("settings.gradle.kts", ProjectFileRole.SETTINGS_GRADLE),
            PlannedProjectFile("build.gradle.kts", ProjectFileRole.ROOT_BUILD_GRADLE),
            PlannedProjectFile("gradle.properties", ProjectFileRole.GRADLE_PROPERTIES),
            PlannedProjectFile("app/build.gradle.kts", ProjectFileRole.APP_BUILD_GRADLE),
            PlannedProjectFile("app/src/main/AndroidManifest.xml", ProjectFileRole.ANDROID_MANIFEST),
            PlannedProjectFile(
                "app/src/main/java/$packagePath/MainActivity.kt",
                ProjectFileRole.MAIN_ACTIVITY,
            ),
        )

        check(files.map { it.path }.distinct().size == files.size) {
            "Project plan must not contain duplicate paths."
        }

        return ProjectPlan(
            projectName = spec.name,
            packageName = spec.packageName,
            packagePath = packagePath,
            entryScreenId = spec.screens.first().id,
            files = files,
        )
    }
}
