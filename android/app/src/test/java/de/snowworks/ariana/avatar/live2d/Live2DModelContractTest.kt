package de.snowworks.ariana.avatar.live2d

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Live2DModelContractTest {

    @Test
    fun acceptsCompleteArianaContract() {
        val result = Live2DModelContract.validate(
            parameterIds = Live2DModelContract.requiredParameterIds,
            expressionIds = Live2DModelContract.requiredExpressionIds,
        )

        assertTrue(result.isValid)
        assertTrue(result.missingParameterIds.isEmpty())
        assertTrue(result.missingExpressionIds.isEmpty())
    }

    @Test
    fun reportsMissingCoreGlowAndExpression() {
        val parameters = Live2DModelContract.requiredParameterIds -
            Live2DParameterMapper.PARAM_ARIANA_CORE_GLOW
        val expressions = Live2DModelContract.requiredExpressionIds - "stern"

        val result = Live2DModelContract.validate(parameters, expressions)

        assertFalse(result.isValid)
        assertTrue(Live2DParameterMapper.PARAM_ARIANA_CORE_GLOW in result.missingParameterIds)
        assertTrue("stern" in result.missingExpressionIds)
    }
}
