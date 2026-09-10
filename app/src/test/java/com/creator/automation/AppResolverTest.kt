package com.creator.automation

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
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
    fun testResolveApplication_DynamicLauncherMatch_ReturnsSuccess() {
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply { packageName = "com.android.chrome" }
        }
        val mockResolveInfo = Mockito.spy(resolveInfo)
        Mockito.doReturn("Chrome").`when`(mockResolveInfo).loadLabel(mockPackageManager)

        Mockito.`when`(mockPackageManager.queryIntentActivities(any(), anyInt()))
            .thenReturn(listOf(mockResolveInfo))
        Mockito.`when`(mockPackageManager.getLaunchIntentForPackage("com.android.chrome"))
            .thenReturn(Intent("android.intent.action.MAIN"))

        val resolver = AppResolver(mockContext)
        val result = resolver.resolveApplication("Chrome")

        assertEquals(AppResolutionStatus.SUCCESS, result.status)
        assertEquals("com.android.chrome", result.packageName)
    }

    @Test
    fun testResolveApplication_UninstalledPackage_ReturnsAppNotInstalled() {
        Mockito.`when`(mockPackageManager.queryIntentActivities(any(), anyInt()))
            .thenReturn(emptyList())

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

    @Test
    fun testResolveApplication_AmbiguousLauncherMatches_ReturnsAmbiguous() {
        val resolveInfo1 = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply { packageName = "com.google.android.googlequicksearchbox" }
        }
        val spy1 = Mockito.spy(resolveInfo1)
        Mockito.doReturn("Google Search").`when`(spy1).loadLabel(mockPackageManager)

        val resolveInfo2 = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply { packageName = "com.google.android.apps.maps" }
        }
        val spy2 = Mockito.spy(resolveInfo2)
        Mockito.doReturn("Google Maps").`when`(spy2).loadLabel(mockPackageManager)

        Mockito.`when`(mockPackageManager.queryIntentActivities(any(), anyInt()))
            .thenReturn(listOf(spy1, spy2))

        val resolver = AppResolver(mockContext)
        val result = resolver.resolveApplication("Google")

        assertEquals(AppResolutionStatus.AMBIGUOUS_APPLICATION, result.status)
        assertEquals(2, result.candidatePackages.size)
    }

    @Test
    fun testIntentSeparation_OpenUrlIntent() {
        val parsedGoal = GoalModel.parse("Go to google.com")
        assertEquals(ActionType.OPEN_URL, parsedGoal.requestedActionType)
        assertEquals("google.com", parsedGoal.requestedActionTarget)
    }

    @Test
    fun testIntentSeparation_GenericOpenAppIntent() {
        val parsedGoal = GoalModel.parse("Open Google")
        assertEquals(ActionType.LAUNCH_APP, parsedGoal.requestedActionType)
        assertEquals("Google", parsedGoal.targetAppQuery)
    }
}
