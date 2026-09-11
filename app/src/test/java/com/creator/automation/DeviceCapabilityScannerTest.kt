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
        // Model real Android runtime permission state: CAMERA is declared in manifest but ungranted until runtime request
        shadowOf(app).denyPermissions(android.Manifest.permission.CAMERA)

        val scanner = DeviceCapabilityScanner(app)

        val permissions = scanner.scanPermissions()

        assertFalse(permissions.isEmpty())

        // 1. Internet permission (normal level): declared AND granted
        val internetPerm = permissions.find { it.permission == android.Manifest.permission.INTERNET }
        assertNotNull(internetPerm)
        assertTrue(internetPerm!!.isDeclared)
        assertTrue(internetPerm.isGranted)
        assertTrue(internetPerm.isUsable)

        // 2. Camera permission (dangerous level): declared BUT NOT granted
        val cameraPerm = permissions.find { it.permission == android.Manifest.permission.CAMERA }
        assertNotNull(cameraPerm)
        assertTrue(cameraPerm!!.isDeclared)
        assertFalse(cameraPerm.isGranted)
        assertFalse(cameraPerm.isUsable)
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
