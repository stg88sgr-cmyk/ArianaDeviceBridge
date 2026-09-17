package de.snowworks.ariana.xx88y

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class X88ActionBusTest {

    @Test
    fun unknownActionIsBlockedByPolicy() = runTest {
        val holder = X88StateHolder()
        holder.setMasterEnabled(true)
        val bus = X88ActionBus(holder)

        val result = bus.dispatch(
            ActionRequest(type = "unknown.action", source = "test", target = "none")
        )

        assertFalse(result.success)
        assertEquals(ActionStatus.BLOCKED, result.status)
    }

    @Test
    fun confirmationActionNeverPretendsSuccessWithoutHandler() = runTest {
        val holder = X88StateHolder()
        holder.setMasterEnabled(true)
        val bus = X88ActionBus(holder)
        val request = ActionRequest(type = "runtime.start", source = "test", target = "x88-build")

        val pending = bus.dispatch(request, confirmed = false)
        assertEquals(ActionStatus.PENDING, pending.status)

        val confirmed = bus.dispatch(request, confirmed = true)
        assertFalse(confirmed.success)
        assertEquals(ActionStatus.NOT_IMPLEMENTED, confirmed.status)
    }

    @Test
    fun emergencyStopBlocksExecution() = runTest {
        val holder = X88StateHolder()
        holder.setMasterEnabled(true)
        holder.activateEmergencyStop()
        val bus = X88ActionBus(holder)

        val result = bus.dispatch(
            ActionRequest(type = "runtime.status", source = "test", target = "runtime")
        )

        assertFalse(result.success)
        assertEquals(ActionStatus.BLOCKED, result.status)
    }
}
