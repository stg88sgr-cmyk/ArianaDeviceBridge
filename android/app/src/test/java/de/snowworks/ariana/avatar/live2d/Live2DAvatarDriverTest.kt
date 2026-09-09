package de.snowworks.ariana.avatar.live2d

import de.snowworks.ariana.avatar.AvatarState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Live2DAvatarDriverTest {

    @Test
    fun firstFramePublishesExpressionAndParameters() {
        val sink = RecordingSink()
        val driver = Live2DAvatarDriver(sink)

        driver.apply(AvatarState())

        assertEquals("neutral", sink.lastExpression)
        assertEquals(Live2DModelContract.requiredParameterIds, sink.parameters.keys)
        assertEquals(1, sink.commits)
    }

    @Test
    fun identicalFrameDoesNotCommitTwice() {
        val sink = RecordingSink()
        val driver = Live2DAvatarDriver(sink)
        val state = AvatarState()

        driver.apply(state)
        driver.apply(state)

        assertEquals(1, sink.commits)
    }

    @Test
    fun changedParameterCommitsIncrementally() {
        val sink = RecordingSink()
        val driver = Live2DAvatarDriver(sink)

        driver.apply(AvatarState())
        val writesBefore = sink.parameterWrites
        driver.apply(AvatarState(headYaw = 0.5f))

        assertEquals(2, sink.commits)
        assertTrue(sink.parameterWrites > writesBefore)
        assertEquals(15f, sink.parameters[Live2DParameterMapper.PARAM_ANGLE_X] ?: 0f, 0.001f)
    }

    @Test
    fun visibilityIsIdempotentAndCloseHides() {
        val sink = RecordingSink()
        val driver = Live2DAvatarDriver(sink)

        driver.show()
        driver.show()
        assertTrue(sink.isVisible)
        assertEquals(1, sink.visibilityWrites)

        driver.close()
        assertFalse(sink.isVisible)
        assertEquals(2, sink.visibilityWrites)
        assertTrue(sink.closed)
    }

    private class RecordingSink : Live2DFrameSink {
        val parameters = linkedMapOf<String, Float>()
        var lastExpression: String? = null
        var isVisible = false
        var commits = 0
        var parameterWrites = 0
        var visibilityWrites = 0
        var closed = false

        override fun setVisible(visible: Boolean) {
            isVisible = visible
            visibilityWrites += 1
        }

        override fun setExpression(expressionId: String) {
            lastExpression = expressionId
        }

        override fun setParameter(parameterId: String, value: Float) {
            parameters[parameterId] = value
            parameterWrites += 1
        }

        override fun commitFrame() {
            commits += 1
        }

        override fun close() {
            closed = true
        }
    }
}
