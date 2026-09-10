package com.creator.automation

enum class WorldStateFreshness {
    OBSERVED,
    UNKNOWN,
    STALE,
    UNAVAILABLE
}

/**
 * WorldState represents a compact, state-based snapshot of current reality on the Android device.
 *
 * Field Taxonomy:
 * - AUTHORITATIVE: Live system metadata (packageName, screenSignature, uiTreeSignature, isAccessibilityAvailable, timestamp, freshness).
 * - DERIVED: Dynamic properties extracted from snapshot (visibleTexts, clickableTargets, editableTargets, focusedTarget, scrollableContainers, controls counts).
 * - CACHED: Snapshot reference and previous signature (snapshot, previousStateSignature).
 * - STALE-PRONE: Temporal execution metadata (recentActionType, recentActionResult, recentFailureReason).
 */
data class WorldState(
    // AUTHORITATIVE FIELDS
    val packageName: String = "unknown",
    val activeWindow: String? = null,
    val screenSignature: String = "acc_disabled",
    val uiTreeSignature: String = "",
    val isAccessibilityAvailable: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val freshness: WorldStateFreshness = WorldStateFreshness.UNKNOWN,

    // DERIVED FIELDS
    val visibleTexts: List<String> = emptyList(),
    val contentDescriptions: List<String> = emptyList(),
    val viewIds: List<String> = emptyList(),
    val clickableTargets: List<UiNodeInfo> = emptyList(),
    val editableTargets: List<UiNodeInfo> = emptyList(),
    val focusedTarget: UiNodeInfo? = null,
    val scrollableContainers: List<UiNodeInfo> = emptyList(),
    val enabledControlsCount: Int = 0,
    val disabledControlsCount: Int = 0,
    val isKeyboardVisible: Boolean = false,
    val hasDialogOrPopup: Boolean = false,

    // CACHED & TEMPORAL STALE-PRONE FIELDS
    val previousStateSignature: String? = null,
    val recentActionType: String? = null,
    val recentActionResult: ActionResultStatus? = null,
    val recentFailureReason: String? = null,
    val snapshot: UiSnapshot? = null
) {
    companion object {
        fun fromSnapshot(
            snapshot: UiSnapshot,
            isAccessibilityAvailable: Boolean = true,
            previousStateSig: String? = null,
            lastActionType: String? = null,
            lastActionResult: ActionResultStatus? = null,
            lastFailureReason: String? = null
        ): WorldState {
            val treeSig = StateSignatureGenerator.generateSignature(snapshot)
            val focused = snapshot.focusedNodes.firstOrNull()
            val hasKeyboard = snapshot.allNodes.any {
                it.className?.contains("InputMethod", ignoreCase = true) == true ||
                        it.className?.contains("Keyboard", ignoreCase = true) == true
            }
            val hasDialog = snapshot.allNodes.any {
                it.className?.contains("Dialog", ignoreCase = true) == true ||
                        it.className?.contains("PopupWindow", ignoreCase = true) == true
            }

            val clickables = if (snapshot.clickableNodes.isNotEmpty()) snapshot.clickableNodes else snapshot.allNodes.filter { it.isClickable }
            val editables = if (snapshot.editableNodes.isNotEmpty()) snapshot.editableNodes else snapshot.allNodes.filter { it.isEditable }
            val scrollables = if (snapshot.scrollableNodes.isNotEmpty()) snapshot.scrollableNodes else snapshot.allNodes.filter { it.isScrollable }

            return WorldState(
                packageName = snapshot.packageName,
                activeWindow = snapshot.packageName,
                screenSignature = treeSig,
                uiTreeSignature = treeSig,
                visibleTexts = snapshot.visibleTexts,
                contentDescriptions = snapshot.contentDescriptions,
                viewIds = snapshot.viewIds,
                clickableTargets = clickables,
                editableTargets = editables,
                focusedTarget = focused,
                scrollableContainers = scrollables,
                enabledControlsCount = snapshot.allNodes.count { it.isEnabled },
                disabledControlsCount = snapshot.allNodes.count { !it.isEnabled },
                isKeyboardVisible = hasKeyboard,
                hasDialogOrPopup = hasDialog,
                isAccessibilityAvailable = isAccessibilityAvailable,
                timestamp = snapshot.timestamp,
                freshness = if (isAccessibilityAvailable) WorldStateFreshness.OBSERVED else WorldStateFreshness.UNAVAILABLE,
                previousStateSignature = previousStateSig,
                recentActionType = lastActionType,
                recentActionResult = lastActionResult,
                recentFailureReason = lastFailureReason,
                snapshot = snapshot
            )
        }
    }
}
