package com.creator.automation

enum class PerceptionSource {
    ACCESSIBILITY,
    OCR,
    VISION,
    FUSED
}

enum class VisualRegionType {
    TEXT_REGION,
    RECTANGULAR_CONTROL,
    IMAGE_REGION,
    INPUT_REGION,
    LIST_REGION,
    SCROLL_REGION,
    DIALOG_REGION,
    UNKNOWN
}

enum class VisionResourceTier {
    VISION_IDLE,
    VISION_EVENT,
    VISION_TARGET,
    VISION_VERIFY
}

data class VisualRegion(
    val id: String,
    val bounds: TargetBounds,
    val confidence: Double,
    val regionType: VisualRegionType
)

data class VisualText(
    val text: String,
    val bounds: TargetBounds,
    val confidence: Double,
    val language: String = "en"
)

data class UnifiedElement(
    val type: String = "UNKNOWN",
    val text: String? = null,
    val contentDescription: String? = null,
    val viewId: String? = null,
    val bounds: TargetBounds,
    val sources: List<PerceptionSource> = listOf(PerceptionSource.ACCESSIBILITY),
    val confidence: Double = 1.0,
    val nodeRef: Any? = null
)

data class VisionPerformanceMetrics(
    val captureTimeMs: Long = 0L,
    val preprocessingTimeMs: Long = 0L,
    val ocrTimeMs: Long = 0L,
    val cvTimeMs: Long = 0L,
    val fusionTimeMs: Long = 0L,
    val totalPerceptionTimeMs: Long = 0L,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val peakMemoryMb: Long = 0L
)

data class ScreenObservation(
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val windowState: String = "ACTIVE",
    val accessibilityNodes: List<UiNodeInfo> = emptyList(),
    val visualRegions: List<VisualRegion> = emptyList(),
    val visualTexts: List<VisualText> = emptyList(),
    val fusedElements: List<UnifiedElement> = emptyList(),
    val screenChange: VisualChangeResult = VisualChangeResult.UNKNOWN,
    val screenBounds: TargetBounds = TargetBounds(0, 0, 720, 1280),
    val perceptionSources: List<PerceptionSource> = listOf(PerceptionSource.ACCESSIBILITY),
    val metrics: VisionPerformanceMetrics = VisionPerformanceMetrics()
)
