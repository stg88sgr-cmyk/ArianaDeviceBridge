package de.snowworks.ariana.image.generator.comfy

import de.snowworks.ariana.image.generator.EndpointSecurity
import de.snowworks.ariana.image.generator.LocalImageGenerator
import de.snowworks.ariana.image.model.GeneratorBackend
import de.snowworks.ariana.image.model.ImageGenerationRequest

class ComfyUiLocalGenerator(
    private val endpoint: String = "http://127.0.0.1:8188",
) : LocalImageGenerator {
    override val backend = GeneratorBackend.COMFYUI_LOCAL

    override suspend fun generate(request: ImageGenerationRequest, onProgress: (Float) -> Unit): ByteArray {
        EndpointSecurity.validate(endpoint)
        throw UnsupportedOperationException("ComfyUI transport adapter is not wired in this source gate")
    }
}
