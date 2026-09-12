package com.creator.automation

import org.junit.Assert.*
import org.junit.Test

class VisualPerceptionTest {

    @Test
    fun testVisualPerceptionModelsDefaults() {
        val obs = ScreenObservation(
            packageName = "com.google.android.googlequicksearchbox"
        )

        assertEquals("com.google.android.googlequicksearchbox", obs.packageName)
        assertEquals("ACTIVE", obs.windowState)
        assertTrue(obs.fusedElements.isEmpty())
        assertEquals(TargetBounds(0, 0, 720, 1280), obs.screenBounds)
    }

    @Test
    fun testVisualStateAnalyzerSignatureComparison() {
        val sig1 = "V_W720H1280_abcdef123456"
        val sig2 = "V_W720H1280_abcdef123456"
        val sig3 = "V_W720H1280_999999999999"

        assertEquals(VisualChangeResult.NO_VISUAL_CHANGE, VisualStateAnalyzer.compareSignatures(sig1, sig2))
        assertEquals(VisualChangeResult.VISUAL_CHANGE, VisualStateAnalyzer.compareSignatures(sig1, sig3))
        assertEquals(VisualChangeResult.UNKNOWN, VisualStateAnalyzer.compareSignatures(null, sig1))
    }

    @Test
    fun testVisualRegionDetectorNullFallback() {
        val regions = VisualRegionDetector.detectRegions(null)
        assertTrue(regions.isEmpty())
    }

    @Test
    fun testPerceptionFusionEngine() {
        val node1 = UiNodeInfo(
            text = "Google Search",
            boundsInScreen = "100, 200, 500, 300",
            isClickable = true,
            isEditable = false
        )
        val snapshot = UiSnapshot(
            packageName = "com.google.android.googlequicksearchbox",
            allNodes = listOf(node1)
        )

        val vr = VisualRegion(
            id = "region_1",
            bounds = TargetBounds(105, 205, 495, 295),
            confidence = 0.9,
            regionType = VisualRegionType.INPUT_REGION
        )

        val vt = VisualText(
            text = "Google Search",
            bounds = TargetBounds(100, 200, 500, 300),
            confidence = 0.95
        )

        val observation = PerceptionFusionEngine.fuse(
            snapshot = snapshot,
            visualRegions = listOf(vr),
            visualTexts = listOf(vt),
            packageName = "com.google.android.googlequicksearchbox"
        )

        assertEquals("com.google.android.googlequicksearchbox", observation.packageName)
        assertFalse(observation.fusedElements.isEmpty())

        val fusedElem = observation.fusedElements.first()
        assertEquals("Google Search", fusedElem.text)
        assertTrue(fusedElem.sources.contains(PerceptionSource.ACCESSIBILITY))
        assertTrue(fusedElem.sources.contains(PerceptionSource.VISION))
        assertTrue(fusedElem.sources.contains(PerceptionSource.OCR))
    }

    @Test
    fun testActionResolverFusedTargetResolution() {
        val resolver = ActionResolver()

        val node1 = UiNodeInfo(
            text = "Voice Search",
            contentDescription = "Search by voice",
            boundsInScreen = "600, 100, 700, 200",
            isClickable = true
        )
        val snapshot = UiSnapshot(
            packageName = "com.google.android.googlequicksearchbox",
            allNodes = listOf(node1)
        )

        val observation = PerceptionFusionEngine.fuse(
            snapshot = snapshot,
            packageName = "com.google.android.googlequicksearchbox"
        )

        val result = resolver.resolveFusedTarget(observation, "Voice Search")
        assertEquals(TargetResolutionStatus.FOUND_UNIQUE, result.status)
        assertNotNull(result.match)
        assertEquals("Voice Search", result.match?.node?.text)
    }
}
