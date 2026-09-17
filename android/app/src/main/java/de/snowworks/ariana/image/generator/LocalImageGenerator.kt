package de.snowworks.ariana.image.generator

import de.snowworks.ariana.image.model.GeneratorBackend
import de.snowworks.ariana.image.model.ImageGenerationRequest

interface LocalImageGenerator {
    val backend: GeneratorBackend
    suspend fun generate(request: ImageGenerationRequest, onProgress: (Float) -> Unit = {}): ByteArray
    suspend fun cancel(requestId: String) {}
    fun close() {}
}
