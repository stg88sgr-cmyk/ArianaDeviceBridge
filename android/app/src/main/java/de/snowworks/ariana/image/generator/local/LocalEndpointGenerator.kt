package de.snowworks.ariana.image.generator.local

import de.snowworks.ariana.image.generator.EndpointSecurity
import de.snowworks.ariana.image.generator.LocalImageGenerator
import de.snowworks.ariana.image.model.GeneratorBackend
import de.snowworks.ariana.image.model.ImageGenerationRequest

class LocalEndpointGenerator : LocalImageGenerator {
    override val backend = GeneratorBackend.CUSTOM_LOCAL_ENDPOINT

    override suspend fun generate(request: ImageGenerationRequest, onProgress: (Float) -> Unit): ByteArray {
        val endpoint = request.modelId ?: throw IllegalArgumentException("custom endpoint is missing")
        EndpointSecurity.validate(endpoint)
        throw UnsupportedOperationException("Custom local transport adapter is not wired in this source gate")
    }
}
