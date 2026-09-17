package de.snowworks.ariana.image.validation

import de.snowworks.ariana.image.model.GeneratorBackend
import de.snowworks.ariana.image.model.ImageGenerationRequest

data class ValidationResult(val isValid: Boolean, val error: String? = null)

class ImageRequestValidator {
    fun validate(request: ImageGenerationRequest): ValidationResult {
        if (request.requestId.isBlank() || request.requestId.length > 128) return invalid("invalid requestId")
        if (request.prompt.isBlank()) return invalid("prompt is blank")
        if (request.prompt.length > 8_000) return invalid("prompt too long")
        if ((request.negativePrompt?.length ?: 0) > 8_000) return invalid("negative prompt too long")
        if (request.width !in 64..4096 || request.height !in 64..4096) return invalid("dimensions out of range")
        if (request.width % 8 != 0 || request.height % 8 != 0) return invalid("dimensions must be divisible by 8")
        if (request.steps !in 1..150) return invalid("steps out of range")
        if (request.backend == GeneratorBackend.CUSTOM_LOCAL_ENDPOINT && request.modelId.isNullOrBlank()) return invalid("custom endpoint is missing")
        return ValidationResult(true)
    }

    private fun invalid(message: String) = ValidationResult(false, message)
}

class OutputValidator(
    private val maxBytes: Int = 50 * 1024 * 1024,
) {
    fun validateImageFile(bytes: ByteArray): ValidationResult {
        if (bytes.isEmpty()) return ValidationResult(false, "empty image")
        if (bytes.size > maxBytes) return ValidationResult(false, "image too large")
        val png = bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        val jpeg = bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        return if (png || jpeg) ValidationResult(true) else ValidationResult(false, "unsupported image signature")
    }
}
