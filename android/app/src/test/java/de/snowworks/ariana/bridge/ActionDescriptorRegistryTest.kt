package de.snowworks.ariana.bridge

import de.snowworks.ariana.Feature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionDescriptorRegistryTest {

    @Test
    fun unknownActionIsNotDescribed() {
        assertNull(ActionDescriptorRegistry.find("totally_unknown_action"))
    }

    @Test
    fun cameraStartDerivesConfirmationAndCapability() {
        val descriptor = ActionDescriptorRegistry.find("camera_start")
        assertNotNull(descriptor)
        assertEquals(ConfirmationRequirement.CONFIRM, descriptor!!.confirmation)
        assertTrue(descriptor.requiredFeatures.contains(Feature.CAMERA))
    }

    @Test
    fun stopAllIsSafe() {
        val descriptor = ActionDescriptorRegistry.find("stop_all")
        assertNotNull(descriptor)
        assertEquals(ConfirmationRequirement.SAFE, descriptor!!.confirmation)
    }

    @Test
    fun payloadRejectsUnknownFields() {
        val descriptor = ActionDescriptorRegistry.find("camera_start")!!
        val result = ActionDescriptorRegistry.validatePayload(
            descriptor,
            mapOf("caller_confirmation" to "safe"),
        )
        assertFalse(result.isSuccess)
    }

    @Test
    fun emptyPayloadIsValidForCurrentCameraStartSchema() {
        val descriptor = ActionDescriptorRegistry.find("camera_start")!!
        assertTrue(ActionDescriptorRegistry.validatePayload(descriptor, emptyMap()).isSuccess)
    }
}
