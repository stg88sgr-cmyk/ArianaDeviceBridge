package de.snowworks.ariana.image.policy

import de.snowworks.ariana.image.model.ImageGenerationRequest

/**
 * Structural default only. Product/content policy can be supplied by the caller.
 * Request validation still runs before this policy hook.
 */
class DefaultImagePolicyEngine : ImagePolicyEngine {
    override suspend fun evaluate(request: ImageGenerationRequest): PolicyDecision = PolicyDecision.Allow
}
