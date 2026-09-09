package de.snowworks.ariana.avatar.live2d

import de.snowworks.ariana.avatar.AvatarDriver
import de.snowworks.ariana.avatar.AvatarDriverRouter
import de.snowworks.ariana.avatar.AvatarState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class Live2DRendererInstallerTest {

    @Test
    fun invalidCandidateNeverCreatesOrInstallsRenderer() {
        val original = RecordingDriver()
        val router = AvatarDriverRouter(original)
        val installer = Live2DRendererInstaller(router)
        var factoryCalls = 0

        val result = installer.install(invalidCandidate()) {
            factoryCalls += 1
            RecordingDriver()
        }

        assertFalse(result.installed)
        assertFalse(result.gate.ready)
        assertEquals(0, factoryCalls)
        assertSame(original, router.currentDriver())
        assertFalse(original.closed)
    }

    @Test
    fun validCandidateInstallsRendererAndReplaysPresenceState() {
        val original = RecordingDriver()
        val router = AvatarDriverRouter(original)
        val installer = Live2DRendererInstaller(router)
        val state = AvatarState(
            headYaw = 0.35f,
            mouthOpen = 0.65f,
            coreGlow = 0.8f,
            speaking = true,
        )
        router.apply(state)
        router.show()

        val replacement = RecordingDriver()
        val result = installer.install(validCandidate()) { replacement }

        assertTrue(result.gate.ready)
        assertTrue(result.installed)
        assertSame(replacement, router.currentDriver())
        assertEquals(state.normalized(), replacement.lastState)
        assertTrue(replacement.visible)
        assertTrue(original.closed)
    }

    private fun validCandidate(): Live2DInstallGate.Candidate {
        val bundle = Live2DModelBundle.arianaV1()
        val existingPaths = linkedSetOf(
            bundle.modelJsonPath,
            bundle.mocPath,
            bundle.physicsPath,
            bundle.cdiPath,
        ).apply {
            addAll(bundle.texturePaths)
            addAll(bundle.expressionPaths.values)
        }

        return Live2DInstallGate.Candidate(
            bundle = bundle,
            existingPaths = existingPaths,
            parameterIds = Live2DModelContract.requiredParameterIds,
            expressionIds = Live2DModelContract.requiredExpressionIds,
        )
    }

    private fun invalidCandidate(): Live2DInstallGate.Candidate {
        val valid = validCandidate()
        return valid.copy(
            existingPaths = valid.existingPaths - valid.bundle.modelJsonPath,
        )
    }

    private class RecordingDriver : AvatarDriver {
        var lastState: AvatarState? = null
        var visible = false
        var closed = false

        override fun apply(state: AvatarState) {
            lastState = state
        }

        override fun show() {
            visible = true
        }

        override fun hide() {
            visible = false
        }

        override fun close() {
            closed = true
        }
    }
}
