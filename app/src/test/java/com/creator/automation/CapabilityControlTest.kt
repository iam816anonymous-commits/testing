package com.creator.automation

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class CapabilityControlTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var mockPm: PackageManager

    @Before
    fun setUp() {
        mockContext = Mockito.mock(Context::class.java)
        mockPrefs = Mockito.mock(SharedPreferences::class.java)
        mockEditor = Mockito.mock(SharedPreferences.Editor::class.java)
        mockPm = Mockito.mock(PackageManager::class.java)

        Mockito.`when`(mockContext.applicationContext).thenReturn(mockContext)
        Mockito.`when`(mockContext.packageManager).thenReturn(mockPm)
        Mockito.`when`(mockContext.getSharedPreferences(Mockito.anyString(), Mockito.anyInt())).thenReturn(mockPrefs)
        Mockito.`when`(mockPrefs.edit()).thenReturn(mockEditor)
        Mockito.`when`(mockEditor.putBoolean(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(mockEditor)
        Mockito.`when`(mockPrefs.getBoolean(Mockito.anyString(), Mockito.anyBoolean())).thenAnswer { invocation ->
            invocation.getArgument(1) as Boolean
        }
    }

    @Test
    fun testCapabilityPreferenceStore_UserTogglePolicyEnforced() {
        CapabilityPreferenceStore.init(mockContext)

        CapabilityPreferenceStore.setUserEnabled(mockContext, "CAMERA_VISION", false)
        assertFalse(CapabilityPreferenceStore.isUserEnabled(mockContext, "CAMERA_VISION"))

        CapabilityPreferenceStore.setUserEnabled(mockContext, "CAMERA_VISION", true)
        assertTrue(CapabilityPreferenceStore.isUserEnabled(mockContext, "CAMERA_VISION"))
    }

    @Test
    fun testDeviceCapabilityScanner_IncludesUserEnabledAndUsableState() {
        CapabilityPreferenceStore.init(mockContext)
        val scanner = DeviceCapabilityScanner(mockContext)
        val profile = scanner.scanDeviceProfile()

        assertNotNull(profile.capabilityMappings)
        val accMapping = profile.capabilityMappings.firstOrNull { it.name == "ACCESSIBILITY_AUTOMATION" }
        assertNotNull("ACCESSIBILITY_AUTOMATION mapping should be present", accMapping)
        assertTrue(accMapping!!.isUserEnabled)
    }
}
