package de.snowworks.ariana.image.policy

import de.snowworks.ariana.image.model.ImageGenerationRequest

sealed interface PolicyDecision {
    data object Allow : PolicyDecision
    data class Block(val reason: String) : PolicyDecision
}

fun interface ImagePolicyEngine {
    suspend fun evaluate(request: ImageGenerationRequest): PolicyDecision
}
