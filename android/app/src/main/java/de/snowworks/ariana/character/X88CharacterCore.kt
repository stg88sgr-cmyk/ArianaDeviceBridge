package de.snowworks.ariana.character

data class X88ScenePreset(
    val id: String,
    val name: String,
    val outfit: String,
    val pose: String,
    val expression: String,
    val lighting: String,
    val camera: String,
    val environment: String,
    val extra: String = "",
)

object X88CharacterCore {
    const val id = "arian-x88"
    const val name = "ARIANA X-88"
    const val version = "character-core-v3"

    val visualTraits = listOf(
        "long dark hair",
        "blue eyes",
        "black biomechanical body",
        "white segmented shoulders and arms",
        "cyan and magenta luminous lines",
        "gold chest ornaments",
        "glowing geometric chest core",
        "elegant premium futuristic silhouette",
    )

    val styleRules = listOf(
        "Preserve face identity, eye color, hair color and overall silhouette.",
        "Keep the design premium and futuristic rather than generic sci-fi.",
        "Use black, white, cyan, magenta and gold as the primary visual language.",
        "Do not replace the biomechanical design with unrelated armor styles.",
        "Scene, pose, expression and camera may vary without changing identity.",
        "Generate one coherent ARIANA X-88 character, not multiple variants.",
    )

    val negativeRules = listOf(
        "different character identity",
        "different face",
        "different eye color",
        "different hair color",
        "short hair",
        "generic unrelated armor",
        "duplicate character",
        "multiple people",
        "extra limbs",
        "deformed face",
        "distorted hands",
        "watermark",
        "logo",
        "random text",
    )
}

object X88Scenes {
    val master = X88ScenePreset(
        id = "master",
        name = "Master",
        outfit = "MASTER_X88",
        pose = "FRONT",
        expression = "NEUTRAL",
        lighting = "SOFT_STUDIO",
        camera = "HALF_BODY",
        environment = "DARK_STUDIO",
    )

    val portrait = X88ScenePreset(
        id = "portrait",
        name = "Portrait",
        outfit = "MASTER_X88",
        pose = "THREE_QUARTER",
        expression = "WARM",
        lighting = "CINEMATIC",
        camera = "PORTRAIT",
        environment = "DARK_STUDIO",
    )

    val techLab = X88ScenePreset(
        id = "tech_lab",
        name = "Tech Lab",
        outfit = "LAB",
        pose = "COMMAND",
        expression = "FOCUSED",
        lighting = "NEON_EDGE",
        camera = "HALF_BODY",
        environment = "FUTURE_LAB",
    )

    val night = X88ScenePreset(
        id = "night",
        name = "Night",
        outfit = "ELEGANT",
        pose = "RELAXED",
        expression = "WARM",
        lighting = "NIGHT",
        camera = "HALF_BODY",
        environment = "CITY_NIGHT",
    )

    val widget = X88ScenePreset(
        id = "widget",
        name = "Widget",
        outfit = "MASTER_X88",
        pose = "FRONT",
        expression = "FOCUSED",
        lighting = "HOLOGRAPHIC",
        camera = "WIDGET_CROP",
        environment = "TRANSPARENT_UI",
    )

    val cinematic = X88ScenePreset(
        id = "cinematic",
        name = "Cinematic",
        outfit = "CINEMATIC",
        pose = "WALKING",
        expression = "SERIOUS",
        lighting = "CINEMATIC",
        camera = "WIDE",
        environment = "HOLOGRAPHIC_SPACE",
        extra = "high-end film still, restrained depth of field, premium production design",
    )

    val all = listOf(master, portrait, techLab, night, widget, cinematic)

    fun byId(id: String?): X88ScenePreset = all.firstOrNull { it.id == id } ?: master
}

object X88PromptComposer {
    fun compose(scene: X88ScenePreset): String = buildString {
        appendLine("CHARACTER IDENTITY LOCK")
        appendLine("Name: ${X88CharacterCore.name}")
        appendLine("Version: ${X88CharacterCore.version}")
        appendLine()
        appendLine("VISUAL IDENTITY")
        X88CharacterCore.visualTraits.forEach { appendLine("- $it") }
        appendLine()
        appendLine("STYLE RULES")
        X88CharacterCore.styleRules.forEach { appendLine("- $it") }
        appendLine()
        appendLine("SCENE")
        appendLine("Outfit: ${scene.outfit}")
        appendLine("Pose: ${scene.pose}")
        appendLine("Expression: ${scene.expression}")
        appendLine("Lighting: ${scene.lighting}")
        appendLine("Camera: ${scene.camera}")
        appendLine("Environment: ${scene.environment}")
        if (scene.extra.isNotBlank()) appendLine("Additional direction: ${scene.extra}")
    }.trim()

    fun negative(): String = X88CharacterCore.negativeRules.joinToString(", ")
}
