package de.snowworks.ariana.joyfun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JoyFunEngineTest {
    @Test
    fun humorRaisesFunAndKeepsBounds() {
        val engine = JoyFunEngine()
        val before = engine.state.value.fun
        engine.dispatch(JoyFunEvent.Humor(1.0f))
        val after = engine.state.value.fun
        assertTrue(after > before)
        assertTrue(after in 0f..1f)
    }

    @Test
    fun resetRestoresBaseline() {
        val engine = JoyFunEngine()
        engine.dispatch(JoyFunEvent.Stress(0.3f))
        engine.dispatch(JoyFunEvent.Reset)
        assertEquals(JoyFunState().joy, engine.state.value.joy, 0.0001f)
        assertEquals(JoyFunState().fun, engine.state.value.fun, 0.0001f)
    }
}
