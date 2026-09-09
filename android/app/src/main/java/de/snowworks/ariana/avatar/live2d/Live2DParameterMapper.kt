package de.snowworks.ariana.avatar.live2d

import de.snowworks.ariana.avatar.AvatarState

/**
 * Converts Ariana's renderer-neutral [AvatarState] into a stable Live2D
 * parameter contract. The app can use this mapping for VTube Studio first and
 * a native Cubism renderer later without changing the presence layer.
 */
object Live2DParameterMapper {

    data class Frame(
        val parameters: Map<String, Float>,
        val expressionId: String,
    )

    fun map(state: AvatarState): Frame {
        val s = state.normalized()
        return Frame(
            parameters = linkedMapOf(
                PARAM_ANGLE_X to s.headYaw * 30f,
                PARAM_ANGLE_Y to s.headPitch * 30f,
                PARAM_ANGLE_Z to s.headRoll * 30f,
                PARAM_EYE_BALL_X to s.eyeX,
                PARAM_EYE_BALL_Y to s.eyeY,
                PARAM_EYE_L_OPEN to s.leftEyeOpen,
                PARAM_EYE_R_OPEN to s.rightEyeOpen,
                PARAM_MOUTH_OPEN_Y to s.mouthOpen,
                PARAM_MOUTH_FORM to s.mouthForm,
                PARAM_BREATH to s.breath,
                PARAM_ARIANA_CORE_GLOW to s.coreGlow,
            ),
            expressionId = expressionId(s.expression),
        )
    }

    private fun expressionId(expression: AvatarState.Expression): String = when (expression) {
        AvatarState.Expression.NEUTRAL -> "neutral"
        AvatarState.Expression.SOFT_SMILE -> "soft_smile"
        AvatarState.Expression.HAPPY -> "happy"
        AvatarState.Expression.FOCUSED -> "focused"
        AvatarState.Expression.STERN -> "stern"
        AvatarState.Expression.SURPRISED -> "surprised"
    }

    const val PARAM_ANGLE_X = "ParamAngleX"
    const val PARAM_ANGLE_Y = "ParamAngleY"
    const val PARAM_ANGLE_Z = "ParamAngleZ"
    const val PARAM_EYE_BALL_X = "ParamEyeBallX"
    const val PARAM_EYE_BALL_Y = "ParamEyeBallY"
    const val PARAM_EYE_L_OPEN = "ParamEyeLOpen"
    const val PARAM_EYE_R_OPEN = "ParamEyeROpen"
    const val PARAM_MOUTH_OPEN_Y = "ParamMouthOpenY"
    const val PARAM_MOUTH_FORM = "ParamMouthForm"
    const val PARAM_BREATH = "ParamBreath"

    /** Custom parameter we will add to the Ariana Live2D model. */
    const val PARAM_ARIANA_CORE_GLOW = "ParamArianaCoreGlow"
}
