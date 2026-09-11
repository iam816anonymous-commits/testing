package com.creator.automation

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class DeviceCapabilityScannerTest {

    @Test
    fun testScanDeviceProfileReturnsValidStructure() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val scanner = DeviceCapabilityScanner(context)

        val profile = scanner.scanDeviceProfile()

        assertNotNull(profile)
        assertNotNull(profile.manufacturer)
        assertNotNull(profile.model)
        assertEquals(27, profile.apiLevel)
        assertTrue(profile.totalMemoryMb >= 0)
        assertTrue(profile.totalStorageMb >= 0)
        assertNotNull(profile.sensors)
        assertNotNull(profile.actuators)
        assertNotNull(profile.permissions)
        assertNotNull(profile.capabilityMappings)
    }

    @Test
    fun testPermissionScanDistinguishesDeclaredAndGranted() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).denyPermissions(android.Manifest.permission.CAMERA)

        val scanner = DeviceCapabilityScanner(app)
        val permissions = scanner.scanPermissions()

        assertFalse(permissions.isEmpty())

        val internetPerm = permissions.find { it.permission == android.Manifest.permission.INTERNET }
        val cameraPerm = permissions.find { it.permission == android.Manifest.permission.CAMERA }
        val locationPerm = permissions.find { it.permission == android.Manifest.permission.ACCESS_FINE_LOCATION }

        val diagMsg = "INTERNET: $internetPerm\nCAMERA: $cameraPerm\nLOCATION: $locationPerm"

        // Case 1: Internet (Normal level, declared in manifest) -> DECLARED = true, GRANTED = true, USABLE = true
        assertNotNull("Internet permission missing from scan", internetPerm)
        assertEquals("Internet declared mismatch. $diagMsg", true, internetPerm!!.isDeclared)
        assertEquals("Internet granted mismatch. $diagMsg", true, internetPerm.isGranted)
        assertEquals("Internet usable mismatch. $diagMsg", true, internetPerm.isUsable)
        assertEquals("Internet runtimeApplicable mismatch. $diagMsg", false, internetPerm.isRuntimeApplicable)

        // Case 2: Camera (Dangerous level, declared in manifest, denied at runtime) -> DECLARED = true, GRANTED = false, USABLE = false
        assertNotNull("Camera permission missing from scan", cameraPerm)
        assertEquals("Camera declared mismatch. $diagMsg", true, cameraPerm!!.isDeclared)
        assertEquals("Camera granted mismatch. $diagMsg", false, cameraPerm.isGranted)
        assertEquals("Camera usable mismatch. $diagMsg", false, cameraPerm.isUsable)
        assertEquals("Camera runtimeApplicable mismatch. $diagMsg", true, cameraPerm.isRuntimeApplicable)

        // Case 3: Location (Dangerous level, NOT declared in manifest) -> DECLARED = false, GRANTED = false, USABLE = false
        assertNotNull("Location permission missing from scan", locationPerm)
        assertEquals("Location declared mismatch. $diagMsg", false, locationPerm!!.isDeclared)
        assertEquals("Location granted mismatch. $diagMsg", false, locationPerm.isGranted)
        assertEquals("Location usable mismatch. $diagMsg", false, locationPerm.isUsable)
        assertEquals("Location runtimeApplicable mismatch. $diagMsg", true, locationPerm.isRuntimeApplicable)
    }

    @Test
    fun testDeviceProfileJsonSerialization() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val scanner = DeviceCapabilityScanner(context)

        val profile = scanner.scanDeviceProfile()
        val json = profile.toJson()

        assertNotNull(json)
        assertEquals(27, json.getInt("apiLevel"))
        assertTrue(json.has("sensors"))
        assertTrue(json.has("actuators"))
        assertTrue(json.has("permissions"))
        assertTrue(json.has("capabilities"))
    }
}
