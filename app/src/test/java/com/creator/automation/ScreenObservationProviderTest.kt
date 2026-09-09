package com.creator.automation

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class ScreenObservationProviderTest {

    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockContext = Mockito.mock(Context::class.java)
        ScreenObservationProvider.stopProjectionSession()
    }

    @Test
    fun testGetSource_ReturnsScreen() {
        val provider = ScreenObservationProvider(mockContext)
        assertEquals(ObservationSource.SCREEN, provider.getSource())
    }

    @Test
    fun testCaptureObservation_WhenNotAuthorized_ReturnsUnavailableObservation() = runBlocking {
        val provider = ScreenObservationProvider(mockContext)
        val obs = provider.captureObservation()

        assertEquals(ObservationSource.SCREEN, obs.source)
        assertEquals("SCREEN_UNAVAILABLE", obs.stateSignature)
        assertEquals(0.0, obs.confidence, 0.001)
        assertEquals("UNKNOWN", obs.visualChangeState)
        assertFalse(ScreenObservationProvider.isAuthorized.value)
    }

    @Test
    fun testStopProjectionSession_ResetsAuthorizationState() {
        ScreenObservationProvider.stopProjectionSession()
        assertFalse(ScreenObservationProvider.isAuthorized.value)
        assertEquals(0, ScreenObservationProvider.lastFrameWidth.value)
        assertEquals(0, ScreenObservationProvider.lastFrameHeight.value)
    }
}
