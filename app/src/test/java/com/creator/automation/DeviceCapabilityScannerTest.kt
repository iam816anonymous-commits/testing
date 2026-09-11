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
        // Revoke dangerous runtime permission CAMERA in Robolectric test environment
        shadowOf(app).denyPermissions(android.Manifest.permission.CAMERA)

        val scanner = DeviceCapabilityScanner(app)

        val permissions = scanner.scanPermissions()

        assertFalse(permissions.isEmpty())

        val internetPerm = permissions.find { it.permission == android.Manifest.permission.INTERNET }
        val cameraPerm = permissions.find { it.permission == android.Manifest.permission.CAMERA }

        val diag = "INTERNET: $internetPerm | CAMERA: $cameraPerm"

        assertNotNull("Internet perm missing. $diag", internetPerm)
        assertTrue("Internet isDeclared false. $diag", internetPerm!!.isDeclared)
        assertTrue("Internet isGranted false. $diag", internetPerm.isGranted)
        assertTrue("Internet isUsable false. $diag", internetPerm.isUsable)

        assertNotNull("Camera perm missing. $diag", cameraPerm)
        assertTrue("Camera isDeclared false. $diag", cameraPerm!!.isDeclared)
        assertFalse("Camera isGranted true. $diag", cameraPerm.isGranted)
        assertFalse("Camera isUsable true. $diag", cameraPerm.isUsable)
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
