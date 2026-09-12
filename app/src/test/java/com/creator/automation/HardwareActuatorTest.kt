package com.creator.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareActuatorTest {

    @Test
    fun testHardwareActuatorRegistry() {
        val actuators = HardwareActuatorRegistry.getAllActuators()
        assertTrue("Actuators list should not be empty", actuators.isNotEmpty())

        val torchActuator = HardwareActuatorRegistry.findActuatorForGoal("turn on flashlight")
        assertNotNull("Should resolve FlashlightActuator", torchActuator)
        assertEquals(HardwareCapabilityType.FLASHLIGHT, torchActuator?.type)

        val hapticActuator = HardwareActuatorRegistry.findActuatorForGoal("vibrate phone")
        assertNotNull("Should resolve HapticActuator", hapticActuator)
        assertEquals(HardwareCapabilityType.HAPTIC, hapticActuator?.type)

        val audioActuator = HardwareActuatorRegistry.findActuatorForGoal("mute audio")
        assertNotNull("Should resolve AudioActuator", audioActuator)
        assertEquals(HardwareCapabilityType.AUDIO, audioActuator?.type)

        val displayActuator = HardwareActuatorRegistry.findActuatorForGoal("wake up screen")
        assertNotNull("Should resolve DisplayActuator", displayActuator)
        assertEquals(HardwareCapabilityType.DISPLAY, displayActuator?.type)
    }

    @Test
    fun testFlashlightActuatorProperties() {
        val flashlight = FlashlightActuator()
        assertEquals("Camera Flash Torch", flashlight.name)
        assertEquals("Light/Actuator", flashlight.category)
        assertEquals(HardwareCapabilityType.FLASHLIGHT, flashlight.type)
    }
}
