package de.snowworks.ariana.universal.v2

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskAuthorityTest {

    @Test
    fun normalIntentFieldsAreAccepted() {
        val result = TaskAuthority.rejectReservedFields(
            setOf("taskId", "actionId", "payload", "token"),
        )
        assertTrue(result.isSuccess)
    }

    @Test
    fun callerCannotSupplyConfirmationLevel() {
        val result = TaskAuthority.rejectReservedFields(
            setOf("taskId", "actionId", "confirmationLevel"),
        )
        assertFalse(result.isSuccess)
    }

    @Test
    fun callerCannotSupplyRequiredCapabilities() {
        val result = TaskAuthority.rejectReservedFields(
            setOf("taskId", "actionId", "required_capabilities"),
        )
        assertFalse(result.isSuccess)
    }
}
