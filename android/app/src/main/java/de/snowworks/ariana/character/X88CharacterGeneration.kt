package de.snowworks.ariana.character

data class X88GenerationRequest(
    val characterId: String,
    val sceneId: String,
    val prompt: String,
    val negativePrompt: String,
    val referenceUris: List<String>,
    val identityStrength: Float,
    val seed: Long,
    val aspectRatio: String,
)

data class X88GeneratedImage(
    val uri: String,
    val width: Int? = null,
    val height: Int? = null,
)

sealed class X88GenerationResult {
    data class Success(val images: List<X88GeneratedImage>) : X88GenerationResult()
    data class Error(val message: String) : X88GenerationResult()
}

interface X88CharacterGenerator {
    val id: String

    /** Provider owns threading; callback may arrive on any thread. */
    fun generate(request: X88GenerationRequest, callback: (X88GenerationResult) -> Unit)
}

object X88CharacterGeneratorRegistry {
    @Volatile
    private var provider: X88CharacterGenerator? = null

    fun register(value: X88CharacterGenerator) {
        provider = value
    }

    fun clear() {
        provider = null
    }

    fun current(): X88CharacterGenerator? = provider
}

object X88GenerationRequestFactory {
    fun create(
        scene: X88ScenePreset,
        aspectRatio: String,
        referenceUris: List<String>,
        identityStrength: Float = 0.82f,
    ): X88GenerationRequest {
        require(aspectRatio in setOf("9:16", "1:1", "16:9"))
        require(identityStrength in 0f..1f)
        return X88GenerationRequest(
            characterId = X88CharacterCore.id,
            sceneId = scene.id,
            prompt = X88PromptComposer.compose(scene),
            negativePrompt = X88PromptComposer.negative(),
            referenceUris = referenceUris.distinct(),
            identityStrength = identityStrength,
            seed = stableSeed(X88CharacterCore.id),
            aspectRatio = aspectRatio,
        )
    }

    private fun stableSeed(value: String): Long {
        var acc = 1125899906842597L
        value.forEach { acc = 31L * acc + it.code }
        return if (acc == Long.MIN_VALUE) 0L else kotlin.math.abs(acc)
    }
}
