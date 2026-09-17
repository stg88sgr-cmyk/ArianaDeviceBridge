package de.snowworks.ariana.image.model

import java.util.UUID

data class ImageGenerationRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val prompt: String,
    val negativePrompt: String? = null,
    val width: Int = 1024,
    val height: Int = 1024,
    val steps: Int = 30,
    val seed: Long? = null,
    val backend: GeneratorBackend = GeneratorBackend.LOCAL_DIFFUSION,
    val modelId: String? = null,
)
