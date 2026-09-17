package de.snowworks.ariana.image.model

data class GeneratedImage(
    val id: String,
    val requestId: String,
    val absolutePath: String,
    val width: Int,
    val height: Int,
    val modelId: String?,
    val backend: GeneratorBackend,
)
