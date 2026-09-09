package com.creator.automation

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class CameraObservationProviderTest {

    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockContext = Mockito.mock(Context::class.java)
        CameraObservationProvider.disableCameraPerception()
    }

    @Test
    fun testGetSource_ReturnsCamera() {
        val provider = CameraObservationProvider(mockContext)
        assertEquals(ObservationSource.CAMERA, provider.getSource())
    }

    @Test
    fun testCaptureObservation_WhenNotGrantedOrDisabled_ReturnsUnavailableObservation() = runBlocking {
        val provider = CameraObservationProvider(mockContext)
        val obs = provider.captureObservation()

        assertEquals(ObservationSource.CAMERA, obs.source)
        assertEquals("CAMERA_UNAVAILABLE", obs.stateSignature)
        assertEquals(0.0, obs.confidence, 0.001)
        assertEquals("UNKNOWN", obs.visualChangeState)
        assertFalse(CameraObservationProvider.isCameraRunning.value)
    }

    @Test
    fun testDisableCameraPerception_ResetsState() {
        CameraObservationProvider.disableCameraPerception()
        assertFalse(CameraObservationProvider.isCameraEnabled.value)
        assertFalse(CameraObservationProvider.isCameraRunning.value)
    }
}
