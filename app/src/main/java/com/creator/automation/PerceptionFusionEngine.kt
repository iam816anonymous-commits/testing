package com.creator.automation

object PerceptionFusionEngine {

    /**
     * Fuses accessibility hierarchy snapshot data with visual perception regions and OCR text readings
     * into a single ScreenObservation containing UnifiedElements.
     */
    fun fuse(
        snapshot: UiSnapshot?,
        visualRegions: List<VisualRegion> = emptyList(),
        visualTexts: List<VisualText> = emptyList(),
        packageName: String = "unknown",
        screenBounds: TargetBounds = TargetBounds(0, 0, 720, 1280),
        metrics: VisionPerformanceMetrics = VisionPerformanceMetrics()
    ): ScreenObservation {
        val unifiedList = mutableListOf<UnifiedElement>()
        val sourcesUsed = mutableSetOf<PerceptionSource>()
        val effectivePackage = snapshot?.packageName?.takeIf { it.isNotBlank() } ?: packageName

        if (snapshot != null && snapshot.allNodes.isNotEmpty()) {
            sourcesUsed.add(PerceptionSource.ACCESSIBILITY)
            for (node in snapshot.allNodes) {
                val bounds = parseBounds(node.boundsInScreen) ?: TargetBounds(0, 0, 0, 0)
                val typeStr = when {
                    node.isEditable -> "INPUT"
                    node.isClickable -> "BUTTON"
                    node.isScrollable -> "SCROLLABLE"
                    !node.text.isNullOrBlank() -> "TEXT"
                    !node.contentDescription.isNullOrBlank() -> "ICON"
                    else -> "VIEW"
                }

                unifiedList.add(
                    UnifiedElement(
                        type = typeStr,
                        text = node.text,
                        contentDescription = node.contentDescription,
                        viewId = node.viewIdResourceName,
                        bounds = bounds,
                        sources = listOf(PerceptionSource.ACCESSIBILITY),
                        confidence = 1.0,
                        nodeRef = node
                    )
                )
            }
        }

        if (visualRegions.isNotEmpty()) {
            sourcesUsed.add(PerceptionSource.VISION)
            for (vr in visualRegions) {
                // Check for overlap with existing accessibility elements
                val overlapping = unifiedList.firstOrNull { elem ->
                    isOverlapping(elem.bounds, vr.bounds)
                }

                if (overlapping != null) {
                    // Enrich existing element
                    val updatedSources = (overlapping.sources + PerceptionSource.VISION).distinct()
                    val idx = unifiedList.indexOf(overlapping)
                    if (idx >= 0) {
                        unifiedList[idx] = overlapping.copy(
                            sources = updatedSources,
                            confidence = maxOf(overlapping.confidence, vr.confidence)
                        )
                    }
                } else {
                    unifiedList.add(
                        UnifiedElement(
                            type = vr.regionType.name,
                            bounds = vr.bounds,
                            sources = listOf(PerceptionSource.VISION),
                            confidence = vr.confidence
                        )
                    )
                }
            }
        }

        if (visualTexts.isNotEmpty()) {
            sourcesUsed.add(PerceptionSource.OCR)
            for (vt in visualTexts) {
                val overlapping = unifiedList.firstOrNull { elem ->
                    isOverlapping(elem.bounds, vt.bounds)
                }

                if (overlapping != null) {
                    val updatedSources = (overlapping.sources + PerceptionSource.OCR).distinct()
                    val updatedText = if (overlapping.text.isNullOrBlank()) vt.text else overlapping.text
                    val idx = unifiedList.indexOf(overlapping)
                    if (idx >= 0) {
                        unifiedList[idx] = overlapping.copy(
                            text = updatedText,
                            sources = updatedSources,
                            confidence = maxOf(overlapping.confidence, vt.confidence)
                        )
                    }
                } else {
                    unifiedList.add(
                        UnifiedElement(
                            type = "TEXT",
                            text = vt.text,
                            bounds = vt.bounds,
                            sources = listOf(PerceptionSource.OCR),
                            confidence = vt.confidence
                        )
                    )
                }
            }
        }

        val finalSources = if (sourcesUsed.isEmpty()) listOf(PerceptionSource.ACCESSIBILITY) else sourcesUsed.toList()

        return ScreenObservation(
            timestamp = System.currentTimeMillis(),
            packageName = effectivePackage,
            windowState = "ACTIVE",
            accessibilityNodes = snapshot?.allNodes ?: emptyList(),
            visualRegions = visualRegions,
            visualTexts = visualTexts,
            fusedElements = unifiedList,
            screenBounds = screenBounds,
            perceptionSources = finalSources,
            metrics = metrics
        )
    }

    private fun parseBounds(boundsStr: String?): TargetBounds? {
        if (boundsStr.isNullOrBlank()) return null
        return try {
            // Rect format e.g. "Rect(0, 0 - 720, 1280)" or "0, 0, 720, 1280"
            val nums = boundsStr.replace("[^0-9, -]".toRegex(), "")
                .split(" ", ",", "-")
                .mapNotNull { it.trim().toIntOrNull() }

            if (nums.size >= 4) {
                TargetBounds(nums[0], nums[1], nums[2], nums[3])
            } else null
        } catch (e: Throwable) {
            null
        }
    }

    private fun isOverlapping(b1: TargetBounds, b2: TargetBounds): Boolean {
        if (b1.width <= 0 || b1.height <= 0 || b2.width <= 0 || b2.height <= 0) return false
        val noOverlap = b1.left >= b2.right || b2.left >= b1.right ||
                b1.top >= b2.bottom || b2.top >= b1.bottom
        return !noOverlap
    }
}
