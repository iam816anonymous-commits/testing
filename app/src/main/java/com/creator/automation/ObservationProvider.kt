package com.creator.automation

interface ObservationProvider {
    fun getSource(): ObservationSource
    suspend fun captureObservation(): CurrentObservation
}

class AccessibilityObservationProvider(
    private val serviceProvider: () -> AutomationAccessibilityService? = { AutomationAccessibilityService.instance }
) : ObservationProvider {

    override fun getSource(): ObservationSource = ObservationSource.ACCESSIBILITY

    override suspend fun captureObservation(): CurrentObservation {
        val service = serviceProvider()
        if (service == null) {
            return CurrentObservation(
                source = ObservationSource.ACCESSIBILITY,
                packageName = "unknown",
                stateSignature = "acc_disabled",
                summary = "AccessibilityService:\nconnected = false\nserviceInstance = null\ncanRetrieveWindowContent = false\ncanPerformGestures = false\nactiveWindow = false\npackage = unknown\nlastAccessibilityEvent = None\nlastEventTime = 0",
                confidence = 0.0
            )
        }

        val root = service.getRootNode()
        val snapshot = ActionResolver.captureSnapshot(root, service.packageName ?: "")
        val stateSig = StateSignatureGenerator.generateSignature(snapshot)
        val textSummary = snapshot.visibleTexts.take(8).joinToString("; ")

        return CurrentObservation(
            source = ObservationSource.ACCESSIBILITY,
            timestamp = System.currentTimeMillis(),
            packageName = snapshot.packageName,
            stateSignature = stateSig,
            summary = if (textSummary.isNotBlank()) textSummary else "No visible text",
            confidence = 1.0,
            snapshot = snapshot
        )
    }
}
