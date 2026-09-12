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
data class WorldStateDiff(
    val beforeStateSignature: String,
    val afterStateSignature: String,
    val packageChanged: Boolean,
    val beforePackage: String,
    val afterPackage: String,
    val newTextsAppeared: List<String>,
    val textsDisappeared: List<String>,
    val editableStateShifted: Boolean,
    val focusShifted: Boolean,
    val keyboardToggled: Boolean,
    val dialogToggled: Boolean,
    val isProgressObserved: Boolean,
    val summary: String
) {
    companion object {
        fun calculateDiff(before: WorldState, after: WorldState): WorldStateDiff {
            val pkgChanged = before.packageName != after.packageName
            val newTexts = after.visibleTexts.filter { !before.visibleTexts.contains(it) }
            val removedTexts = before.visibleTexts.filter { !after.visibleTexts.contains(it) }
            val editableShift = before.editableTargets.size != after.editableTargets.size ||
                    before.focusedTarget?.text != after.focusedTarget?.text
            val focusShift = before.focusedTarget?.text != after.focusedTarget?.text ||
                    before.focusedTarget?.viewIdResourceName != after.focusedTarget?.viewIdResourceName
            val kbToggled = before.isKeyboardVisible != after.isKeyboardVisible
            val dialogToggled = before.hasDialogOrPopup != after.hasDialogOrPopup

            val hasProgress = pkgChanged || newTexts.isNotEmpty() || editableShift || focusShift || kbToggled || dialogToggled

            val summaryText = when {
                pkgChanged -> "Application package changed: ${before.packageName} -> ${after.packageName}"
                newTexts.isNotEmpty() -> "${newTexts.size} new UI text elements appeared (e.g. '${newTexts.first()}')"
                editableShift -> "Editable input field state updated"
                focusShift -> "Focused UI node shifted"
                kbToggled -> "Keyboard visibility state changed (keyboardVisible=${after.isKeyboardVisible})"
                dialogToggled -> "Dialog/Popup overlay toggled"
                else -> "No observable UI state change (signatures identical)"
            }

            return WorldStateDiff(
                beforeStateSignature = before.uiTreeSignature,
                afterStateSignature = after.uiTreeSignature,
                packageChanged = pkgChanged,
                beforePackage = before.packageName,
                afterPackage = after.packageName,
                newTextsAppeared = newTexts,
                textsDisappeared = removedTexts,
                editableStateShifted = editableShift,
                focusShifted = focusShift,
                keyboardToggled = kbToggled,
                dialogToggled = dialogToggled,
                isProgressObserved = hasProgress,
                summary = summaryText
            )
        }
    }
}

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
