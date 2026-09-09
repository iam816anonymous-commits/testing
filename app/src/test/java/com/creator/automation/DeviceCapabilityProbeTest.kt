package com.creator.automation

import android.content.Context
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class DeviceCapabilityProbeTest {

    private lateinit var mockContext: Context
    private lateinit var mockPackageManager: PackageManager

    @Before
    fun setUp() {
        mockContext = Mockito.mock(Context::class.java)
        mockPackageManager = Mockito.mock(PackageManager::class.java)
        Mockito.`when`(mockContext.packageManager).thenReturn(mockPackageManager)
    }

    @Test
    fun testProbeCapabilities_returnsValidDeviceProbeResult() {
        val probe = DeviceCapabilityProbe(mockContext)
        val result = probe.probeCapabilities()

        assertNotNull(result)
        assertNotNull(result.androidVersion)
        assertTrue(result.apiLevel >= 0)
        assertNotNull(result.cpuAbi)
        assertEquals(8, result.reports.size)

        val capabilityNames = result.reports.map { it.capability }
        assertTrue(capabilityNames.contains(Capability.ACCESSIBILITY))
        assertTrue(capabilityNames.contains(Capability.SCREEN_CAPTURE))
        assertTrue(capabilityNames.contains(Capability.CAMERA))
        assertTrue(capabilityNames.contains(Capability.WORK_MANAGER))
        assertTrue(capabilityNames.contains(Capability.NETWORK))
        assertTrue(capabilityNames.contains(Capability.STORAGE))
    }

    @Test
    fun testCapabilityRegistry_statesAreDefined() {
        assertEquals(5, CapabilityState.values().size)
        assertTrue(CapabilityState.values().contains(CapabilityState.AVAILABLE))
        assertTrue(CapabilityState.values().contains(CapabilityState.UNAVAILABLE))
        assertTrue(CapabilityState.values().contains(CapabilityState.NOT_GRANTED))
        assertTrue(CapabilityState.values().contains(CapabilityState.NOT_IMPLEMENTED))
        assertTrue(CapabilityState.values().contains(CapabilityState.UNKNOWN))
    }
}
