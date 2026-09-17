package de.snowworks.ariana.image.generator.local

import de.snowworks.ariana.image.generator.LocalImageGenerator
import de.snowworks.ariana.image.model.GeneratorBackend
import de.snowworks.ariana.image.model.ImageGenerationRequest

class LocalDiffusionGenerator(
    private val runner: (suspend (ImageGenerationRequest, (Float) -> Unit) -> ByteArray)? = null,
) : LocalImageGenerator {
    override val backend = GeneratorBackend.LOCAL_DIFFUSION

    override suspend fun generate(request: ImageGenerationRequest, onProgress: (Float) -> Unit): ByteArray {
        val activeRunner = runner ?: throw UnsupportedOperationException("Local diffusion runner is not wired in this source gate")
        return activeRunner(request, onProgress)
    }
}
