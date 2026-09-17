package de.snowworks.ariana.image

import de.snowworks.ariana.image.model.GeneratorBackend
import de.snowworks.ariana.image.model.ImageGenerationRequest
import de.snowworks.ariana.image.validation.ImageRequestValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageRequestValidatorTest {
    private val validator = ImageRequestValidator()

    @Test fun validatesBounds() {
        assertTrue(validator.validate(ImageGenerationRequest(prompt = "ok")).isValid)
        assertFalse(validator.validate(ImageGenerationRequest(prompt = "")).isValid)
        assertFalse(validator.validate(ImageGenerationRequest(prompt = "x", width = 63)).isValid)
        assertFalse(validator.validate(ImageGenerationRequest(prompt = "x", width = 65)).isValid)
        assertFalse(validator.validate(ImageGenerationRequest(prompt = "x", height = 4104)).isValid)
        assertFalse(validator.validate(ImageGenerationRequest(prompt = "x", steps = 0)).isValid)
        assertFalse(validator.validate(ImageGenerationRequest(prompt = "x", steps = 151)).isValid)
    }

    @Test fun customEndpointMustBePresent() {
        assertFalse(validator.validate(ImageGenerationRequest(prompt = "x", backend = GeneratorBackend.CUSTOM_LOCAL_ENDPOINT)).isValid)
        assertTrue(validator.validate(ImageGenerationRequest(prompt = "x", backend = GeneratorBackend.CUSTOM_LOCAL_ENDPOINT, modelId = "http://127.0.0.1:8188")).isValid)
    }
}
