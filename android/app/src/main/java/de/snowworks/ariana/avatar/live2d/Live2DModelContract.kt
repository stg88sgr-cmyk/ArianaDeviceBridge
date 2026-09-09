package de.snowworks.ariana.avatar.live2d

/**
 * Runtime-facing contract for the final Ariana Live2D model.
 *
 * The art pipeline can validate exported parameter and expression IDs before
 * the model is ever loaded on Android. This avoids discovering a typo only
 * after the Cubism bundle reaches the phone.
 */
object Live2DModelContract {

    val requiredParameterIds: Set<String> = linkedSetOf(
        Live2DParameterMapper.PARAM_ANGLE_X,
        Live2DParameterMapper.PARAM_ANGLE_Y,
        Live2DParameterMapper.PARAM_ANGLE_Z,
        Live2DParameterMapper.PARAM_EYE_BALL_X,
        Live2DParameterMapper.PARAM_EYE_BALL_Y,
        Live2DParameterMapper.PARAM_EYE_L_OPEN,
        Live2DParameterMapper.PARAM_EYE_R_OPEN,
        Live2DParameterMapper.PARAM_MOUTH_OPEN_Y,
        Live2DParameterMapper.PARAM_MOUTH_FORM,
        Live2DParameterMapper.PARAM_BREATH,
        Live2DParameterMapper.PARAM_ARIANA_CORE_GLOW,
    )

    val requiredExpressionIds: Set<String> = linkedSetOf(
        "neutral",
        "soft_smile",
        "happy",
        "focused",
        "stern",
        "surprised",
    )

    data class ValidationResult(
        val missingParameterIds: Set<String>,
        val missingExpressionIds: Set<String>,
    ) {
        val isValid: Boolean
            get() = missingParameterIds.isEmpty() && missingExpressionIds.isEmpty()

        fun compactLabel(): String = if (isValid) {
            "live2d-contract-ok"
        } else {
            buildString {
                append("live2d-contract-missing")
                if (missingParameterIds.isNotEmpty()) {
                    append(" · params=")
                    append(missingParameterIds.joinToString(","))
                }
                if (missingExpressionIds.isNotEmpty()) {
                    append(" · expressions=")
                    append(missingExpressionIds.joinToString(","))
                }
            }
        }
    }

    fun validate(
        parameterIds: Collection<String>,
        expressionIds: Collection<String>,
    ): ValidationResult {
        val parameters = parameterIds.toSet()
        val expressions = expressionIds.toSet()
        return ValidationResult(
            missingParameterIds = requiredParameterIds - parameters,
            missingExpressionIds = requiredExpressionIds - expressions,
        )
    }
}
