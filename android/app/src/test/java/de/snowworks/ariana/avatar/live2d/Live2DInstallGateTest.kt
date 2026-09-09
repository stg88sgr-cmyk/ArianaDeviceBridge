package de.snowworks.ariana.avatar.live2d

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Live2DInstallGateTest {

    @Test
    fun acceptsCompleteArianaBundleAndContract() {
        val bundle = Live2DModelBundle.arianaV1()
        val existing = buildSet {
            add(bundle.modelJsonPath)
            add(bundle.mocPath)
            add(bundle.physicsPath)
            add(bundle.cdiPath)
            addAll(bundle.texturePaths)
            addAll(bundle.expressionPaths.values)
        }

        val result = Live2DInstallGate.validate(
            Live2DInstallGate.Candidate(
                bundle = bundle,
                existingPaths = existing,
                parameterIds = Live2DModelContract.requiredParameterIds,
                expressionIds = Live2DModelContract.requiredExpressionIds,
            ),
        )

        assertTrue(result.ready)
    }

    @Test
    fun rejectsIncompleteExportBeforeHotSwap() {
        val bundle = Live2DModelBundle.arianaV1()
        val existing = buildSet {
            add(bundle.modelJsonPath)
            add(bundle.mocPath)
            add(bundle.physicsPath)
            add(bundle.cdiPath)
            add(bundle.texturePaths.first())
            addAll(bundle.expressionPaths.values)
        }

        val result = Live2DInstallGate.validate(
            Live2DInstallGate.Candidate(
                bundle = bundle,
                existingPaths = existing,
                parameterIds = Live2DModelContract.requiredParameterIds - Live2DParameterMapper.PARAM_MOUTH_FORM,
                expressionIds = Live2DModelContract.requiredExpressionIds,
            ),
        )

        assertFalse(result.ready)
        assertTrue(result.bundle.missingTextures)
        assertTrue(Live2DParameterMapper.PARAM_MOUTH_FORM in result.contract.missingParameterIds)
    }
}
