package de.snowworks.ariana.avatar.live2d

import de.snowworks.ariana.avatar.AvatarState
import org.junit.Assert.assertEquals
import org.junit.Test

class Live2DParameterMapperTest {

    @Test
    fun `normalized state maps to expected Live2D ranges`() {
        val frame = Live2DParameterMapper.map(
            AvatarState(
                headYaw = 1f,
                headPitch = -1f,
                headRoll = 0.5f,
                mouthOpen = 0.75f,
                mouthForm = -0.5f,
                coreGlow = 0.9f,
                expression = AvatarState.Expression.SOFT_SMILE,
            ),
        )

        assertEquals(30f, frame.parameters[Live2DParameterMapper.PARAM_ANGLE_X]!!, 0.001f)
        assertEquals(-30f, frame.parameters[Live2DParameterMapper.PARAM_ANGLE_Y]!!, 0.001f)
        assertEquals(15f, frame.parameters[Live2DParameterMapper.PARAM_ANGLE_Z]!!, 0.001f)
        assertEquals(0.75f, frame.parameters[Live2DParameterMapper.PARAM_MOUTH_OPEN_Y]!!, 0.001f)
        assertEquals(-0.5f, frame.parameters[Live2DParameterMapper.PARAM_MOUTH_FORM]!!, 0.001f)
        assertEquals(0.9f, frame.parameters[Live2DParameterMapper.PARAM_ARIANA_CORE_GLOW]!!, 0.001f)
        assertEquals("soft_smile", frame.expressionId)
    }

    @Test
    fun `out-of-range input is clamped before mapping`() {
        val frame = Live2DParameterMapper.map(
            AvatarState(
                headYaw = 9f,
                mouthOpen = 4f,
                mouthForm = -8f,
            ),
        )

        assertEquals(30f, frame.parameters[Live2DParameterMapper.PARAM_ANGLE_X]!!, 0.001f)
        assertEquals(1f, frame.parameters[Live2DParameterMapper.PARAM_MOUTH_OPEN_Y]!!, 0.001f)
        assertEquals(-1f, frame.parameters[Live2DParameterMapper.PARAM_MOUTH_FORM]!!, 0.001f)
    }
}
