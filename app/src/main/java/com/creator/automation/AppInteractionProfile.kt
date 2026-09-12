package com.creator.automation

enum class InteractionSurfaceType {
    EDITABLE_FIELD,
    CLICKABLE_BUTTON,
    LONG_CLICKABLE_CONTROL,
    SCROLLABLE_CONTAINER,
    FOCUSABLE_CONTROL,
    SELECTABLE_OPTION,
    TOGGLEABLE_SWITCH,
    SUBMIT_CANDIDATE,
    NAVIGATION_CONTROL,
    DIALOG_ACTION,
    UNKNOWN
}

enum class InteractionSafetyLevel {
    OBSERVED,
    SUPPORTED,
    SAFE_TO_EXPLORE,
    REQUIRES_APPROVAL,
    DESTRUCTIVE_BLOCKED
}

data class InteractionSurface(
    val id: String,
    val surfaceType: InteractionSurfaceType,
    val label: String? = null,
    val contentDescription: String? = null,
    val resourceId: String? = null,
    val className: String? = null,
    val bounds: TargetBounds? = null,
    val supportedActions: List<ActionType> = emptyList(),
    val confidence: Double = 1.0,
    val source: PerceptionSource = PerceptionSource.ACCESSIBILITY,
    val stateSignature: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class InteractionCandidate(
    val surface: InteractionSurface,
    val proposedAction: ActionType,
    val safetyLevel: InteractionSafetyLevel = InteractionSafetyLevel.OBSERVED,
    val actionParameter: String? = null,
    val description: String = ""
)

data class DiscoveredTransition(
    val transitionId: String,
    val packageName: String,
    val sourceStateSignature: String,
    val actionType: ActionType,
    val targetSurfaceId: String,
    val targetStateSignature: String,
    val isStateChanged: Boolean,
    val verificationStatus: GoalVerificationStatus = GoalVerificationStatus.UNKNOWN,
    val confidence: Double = 0.5,
    val timestamp: Long = System.currentTimeMillis()
)

data class InteractionFlowGraph(
    val packageName: String,
    val knownStates: List<String> = emptyList(),
    val transitions: List<DiscoveredTransition> = emptyList(),
    val totalExplorations: Int = 0,
    val confidenceScore: Double = 0.5
)

data class AppInteractionProfile(
    val packageName: String,
    val appVersionName: String? = null,
    val launchComponent: String? = null,
    val observedStates: List<String> = emptyList(),
    val interactionCapabilities: List<InteractionSurfaceType> = emptyList(),
    val observedTransitions: List<DiscoveredTransition> = emptyList(),
    val flowGraph: InteractionFlowGraph = InteractionFlowGraph(packageName),
    val successfulActionCount: Int = 0,
    val failedActionCount: Int = 0,
    val overallConfidence: Double = 0.5,
    val lastObservedTimestamp: Long = System.currentTimeMillis(),
    val lastValidatedTimestamp: Long = System.currentTimeMillis()
)
