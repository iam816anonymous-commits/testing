package com.creator.automation

interface ObservationProvider {
    fun getSource(): ObservationSource
    suspend fun captureObservation(): CurrentObservation
}

class AccessibilityObservationProvider(
    private val service: AutomationAccessibilityService? = AutomationAccessibilityService.instance
) : ObservationProvider {

    override fun getSource(): ObservationSource = ObservationSource.ACCESSIBILITY

    override suspend fun captureObservation(): CurrentObservation {
        if (service == null) {
            return CurrentObservation(
                source = ObservationSource.ACCESSIBILITY,
                packageName = "unknown",
                stateSignature = "acc_disabled",
                summary = "AccessibilityService is disabled",
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
