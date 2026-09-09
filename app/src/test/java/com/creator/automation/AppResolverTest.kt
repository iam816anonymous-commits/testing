package com.creator.automation

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito

class AppResolverTest {

    private lateinit var mockContext: Context
    private lateinit var mockPackageManager: PackageManager

    @Before
    fun setUp() {
        mockContext = Mockito.mock(Context::class.java)
        mockPackageManager = Mockito.mock(PackageManager::class.java)

        Mockito.`when`(mockContext.packageManager).thenReturn(mockPackageManager)
    }

    @Test
    fun testResolveApplication_KnownInstalledPackage_ReturnsSuccess() {
        val appInfo = ApplicationInfo().apply { packageName = "com.android.chrome" }
        Mockito.`when`(mockPackageManager.getApplicationInfo("com.android.chrome", 0)).thenReturn(appInfo)

        val resolver = AppResolver(mockContext)
        val result = resolver.resolveApplication("Chrome")

        assertEquals(AppResolutionStatus.SUCCESS, result.status)
        assertEquals("com.android.chrome", result.packageName)
    }

    @Test
    fun testResolveApplication_UninstalledPackage_ReturnsAppNotInstalled() {
        Mockito.`when`(mockPackageManager.getApplicationInfo(any(), anyInt()))
            .thenThrow(PackageManager.NameNotFoundException("Not found"))

        val resolver = AppResolver(mockContext)
        val result = resolver.resolveApplication("NonExistentApp123")

        assertEquals(AppResolutionStatus.APP_NOT_INSTALLED, result.status)
    }

    @Test
    fun testResolveApplication_DirectPackage_Installed_ReturnsSuccess() {
        val appInfo = ApplicationInfo().apply { packageName = "com.whatsapp" }
        Mockito.`when`(mockPackageManager.getApplicationInfo("com.whatsapp", 0)).thenReturn(appInfo)

        val resolver = AppResolver(mockContext)
        val result = resolver.resolveApplication("com.whatsapp")

        assertEquals(AppResolutionStatus.SUCCESS, result.status)
        assertEquals("com.whatsapp", result.packageName)
    }
}
