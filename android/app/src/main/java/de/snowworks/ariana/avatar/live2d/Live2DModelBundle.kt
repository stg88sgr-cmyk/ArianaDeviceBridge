package de.snowworks.ariana.avatar.live2d

/**
 * Lightweight description of an exported Ariana Live2D bundle.
 *
 * This deliberately validates names/structure without depending on Cubism SDK
 * classes, so CI can reject incomplete art exports before Android runtime work.
 */
data class Live2DModelBundle(
    val modelJsonPath: String,
    val mocPath: String,
    val physicsPath: String,
    val cdiPath: String,
    val texturePaths: List<String>,
    val expressionPaths: Map<String, String>,
) {
    data class ValidationResult(
        val missingCoreFiles: Set<String>,
        val missingExpressionIds: Set<String>,
        val missingTextures: Boolean,
    ) {
        val isValid: Boolean
            get() = missingCoreFiles.isEmpty() &&
                missingExpressionIds.isEmpty() &&
                !missingTextures

        fun compactLabel(): String = if (isValid) {
            "live2d-bundle-ok"
        } else {
            buildString {
                append("live2d-bundle-invalid")
                if (missingCoreFiles.isNotEmpty()) {
                    append(" · core=")
                    append(missingCoreFiles.joinToString(","))
                }
                if (missingExpressionIds.isNotEmpty()) {
                    append(" · expressions=")
                    append(missingExpressionIds.joinToString(","))
                }
                if (missingTextures) append(" · textures=missing")
            }
        }
    }

    fun validate(existingPaths: Collection<String>): ValidationResult {
        val existing = existingPaths.toSet()
        val requiredCore = linkedSetOf(modelJsonPath, mocPath, physicsPath, cdiPath)
        return ValidationResult(
            missingCoreFiles = requiredCore - existing,
            missingExpressionIds = Live2DModelContract.requiredExpressionIds.filterTo(linkedSetOf()) { id ->
                expressionPaths[id]?.let(existing::contains) != true
            },
            missingTextures = texturePaths.isEmpty() || texturePaths.any { it !in existing },
        )
    }

    companion object {
        fun arianaV1(root: String = "Ariana_X88_v1"): Live2DModelBundle {
            val base = root.trimEnd('/')
            return Live2DModelBundle(
                modelJsonPath = "$base/Ariana_X88_v1.model3.json",
                mocPath = "$base/Ariana_X88_v1.moc3",
                physicsPath = "$base/Ariana_X88_v1.physics3.json",
                cdiPath = "$base/Ariana_X88_v1.cdi3.json",
                texturePaths = listOf(
                    "$base/textures/texture_00.png",
                    "$base/textures/texture_01.png",
                ),
                expressionPaths = Live2DModelContract.requiredExpressionIds.associateWith { id ->
                    "$base/expressions/$id.exp3.json"
                },
            )
        }
    }
}
