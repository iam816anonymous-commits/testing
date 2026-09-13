package com.creator.automation

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

enum class TargetResolutionStatus {
    FOUND_UNIQUE,
    NOT_FOUND,
    AMBIGUOUS,
    NOT_ACTIONABLE,
    STALE
}

data class ResolutionMatch(
    val node: UiNodeInfo,
    val matchMethod: String,
    val confidence: Double,
    val reason: String
)

data class TargetResolutionResult(
    val match: ResolutionMatch?,
    val candidateCount: Int,
    val isAmbiguous: Boolean,
    val status: TargetResolutionStatus = if (match == null) TargetResolutionStatus.NOT_FOUND else if (isAmbiguous) TargetResolutionStatus.AMBIGUOUS else TargetResolutionStatus.FOUND_UNIQUE,
    val explanation: String
)

class ActionResolver {

    companion object {
        private const val TAG = "ActionResolver"
        private const val MAX_TRAVERSAL_DEPTH = 30
        private const val AGENT_PACKAGE_NAME = "com.creator.automation"

        private val LOGIN_PROMPT_KEYWORDS = listOf(
            "sign in",
            "log in",
            "choose an account",
            "use your google account",
            "sign-in",
            "login"
        )

        /**
         * Converts a raw Android AccessibilityNodeInfo tree into a lightweight UiSnapshot.
         * When observing a non-agent target application, filters out CreatorAutomation overlay nodes to prevent pollution.
         */
        fun captureSnapshot(root: AccessibilityNodeInfo?, fallbackPackageName: String = ""): UiSnapshot {
            val startTime = System.currentTimeMillis()
            if (root == null) {
                val duration = System.currentTimeMillis() - startTime
                return UiSnapshot(
                    packageName = fallbackPackageName.ifBlank { "unknown" },
                    timestamp = startTime,
                    isRootAvailable = false,
                    traversalDurationMs = duration
                )
            }

            val rawPackageName = root.packageName?.toString() ?: fallbackPackageName.ifBlank { "unknown" }
            val isTargetingAgent = rawPackageName == AGENT_PACKAGE_NAME

            val visibleTexts = mutableListOf<String>()
            val contentDescriptions = mutableListOf<String>()
            val viewIds = mutableListOf<String>()
            val clickableNodes = mutableListOf<UiNodeInfo>()
            val scrollableNodes = mutableListOf<UiNodeInfo>()
            val editableNodes = mutableListOf<UiNodeInfo>()
            val focusedNodes = mutableListOf<UiNodeInfo>()
            val allNodes = mutableListOf<UiNodeInfo>()

            fun traverse(node: AccessibilityNodeInfo?, depth: Int = 0) {
                if (node == null || depth > MAX_TRAVERSAL_DEPTH) return

                val nodePkg = node.packageName?.toString()
                // Zero-pollution check: exclude CreatorAutomation diagnostic overlay nodes when observing external apps
                if (!isTargetingAgent && nodePkg == AGENT_PACKAGE_NAME) {
                    return
                }

                val text = node.text?.toString()?.trim()
                val contentDesc = node.contentDescription?.toString()?.trim()
                val viewId = node.viewIdResourceName
                val className = node.className?.toString()
                val parentNode = node.parent
                val parentClass = parentNode?.className?.toString()
                val parentTxt = parentNode?.text?.toString()?.trim()

                val isEditable = node.isEditable || className?.contains("EditText", ignoreCase = true) == true
                val isFocused = node.isFocused
                val isFocusable = node.isFocusable

                val bounds = Rect()
                node.getBoundsInScreen(bounds)

                val uiNode = UiNodeInfo(
                    text = text,
                    contentDescription = contentDesc,
                    viewIdResourceName = viewId,
                    className = className,
                    isClickable = node.isClickable,
                    isLongClickable = node.isLongClickable,
                    isScrollable = node.isScrollable,
                    isEditable = isEditable,
                    isFocused = isFocused,
                    isFocusable = isFocusable,
                    isVisibleToUser = node.isVisibleToUser,
                    isEnabled = node.isEnabled,
                    isChecked = node.isChecked,
                    isSelected = node.isSelected,
                    parentClassName = parentClass,
                    parentText = parentTxt,
                    boundsInScreen = bounds.toShortString(),
                    nodeRef = node
                )

                allNodes.add(uiNode)

                if (!text.isNullOrBlank()) {
                    visibleTexts.add(text)
                }
                if (!contentDesc.isNullOrBlank()) {
                    contentDescriptions.add(contentDesc)
                }
                if (!viewId.isNullOrBlank()) {
                    viewIds.add(viewId)
                }
                if (node.isClickable) {
                    clickableNodes.add(uiNode)
                }
                if (node.isScrollable) {
                    scrollableNodes.add(uiNode)
                }
                if (isEditable) {
                    editableNodes.add(uiNode)
                }
                if (isFocused) {
                    focusedNodes.add(uiNode)
                }

                for (i in 0 until node.childCount) {
                    val child = try {
                        node.getChild(i)
                    } catch (e: Exception) {
                        null
                    }
                    if (child != null) {
                        traverse(child, depth + 1)
                    }
                }
            }

            traverse(root)

            val duration = System.currentTimeMillis() - startTime
            val snapshot = UiSnapshot(
                packageName = rawPackageName,
                timestamp = startTime,
                isRootAvailable = true,
                traversalDurationMs = duration,
                visibleTexts = visibleTexts.distinct(),
                contentDescriptions = contentDescriptions.distinct(),
                viewIds = viewIds.distinct(),
                clickableNodes = clickableNodes,
                scrollableNodes = scrollableNodes,
                editableNodes = editableNodes,
                focusedNodes = focusedNodes,
                allNodes = allNodes
            )

            Log.d(TAG, "UI_SNAPSHOT_CREATED: pkg=$rawPackageName, totalNodes=${snapshot.totalNodeCount}, visibleTexts=${snapshot.visibleTexts.size}, editables=${snapshot.editableNodes.size}, durationMs=${duration}ms")
            return snapshot
        }
    }

    /**
     * Resolves a target string against a UiSnapshot using strict priority:
     * 1. View ID match
     * 2. Exact visible text match
     * 3. Content description match
     * 4. Accessibility properties / partial text match
     */
    fun resolveTarget(snapshot: UiSnapshot, target: String): ResolutionMatch? {
        return resolveTargetWithAmbiguity(snapshot, target).match
    }

    /**
     * Read-only Layer 3 Target Discovery: resolves a TargetRequest against a UiSnapshot without triggering any physical action.
     */
    fun discoverTarget(snapshot: UiSnapshot, request: TargetRequest): TargetResolutionResult {
        if (!request.requestedViewId.isNullOrBlank()) {
            val res = resolveTargetWithAmbiguity(snapshot, request.requestedViewId)
            if (res.match != null) return res
        }
        if (!request.requestedText.isNullOrBlank()) {
            val res = resolveTargetWithAmbiguity(snapshot, request.requestedText)
            if (res.match != null) return res
        }
        if (!request.requestedContentDescription.isNullOrBlank()) {
            val res = resolveTargetWithAmbiguity(snapshot, request.requestedContentDescription)
            if (res.match != null) return res
        }
        if (!request.requestedRole.isNullOrBlank()) {
            val roleMatches = snapshot.allNodes.filter { it.className?.contains(request.requestedRole, ignoreCase = true) == true }
            if (roleMatches.isNotEmpty()) {
                // Role or class name alone is NOT sufficient for a unique target match
                val isAmbiguous = roleMatches.size > 1
                val best = roleMatches.first()
                return TargetResolutionResult(
                    match = ResolutionMatch(
                        node = best,
                        matchMethod = "ROLE_CLASS",
                        confidence = 0.40,
                        reason = "Matched generic class role '${request.requestedRole}' without specific text/id"
                    ),
                    candidateCount = roleMatches.size,
                    isAmbiguous = isAmbiguous,
                    status = TargetResolutionStatus.AMBIGUOUS,
                    explanation = "Role/Class alone '${request.requestedRole}' is generic (${roleMatches.size} candidates) - classified as AMBIGUOUS"
                )
            }
        }

        return TargetResolutionResult(
            match = null,
            candidateCount = 0,
            isAmbiguous = false,
            status = TargetResolutionStatus.NOT_FOUND,
            explanation = "Target request not found in UI snapshot"
        )
    }

    /**
     * Generates a generic screen interaction map exposing all interactable UI elements on the current screen.
     */
    fun generateInteractionMap(snapshot: UiSnapshot): ScreenInteractionMap {
        val interactiveNodes = snapshot.allNodes.filter {
            it.isClickable || it.isEditable || it.isScrollable || !it.text.isNullOrBlank() || !it.contentDescription.isNullOrBlank()
        }

        val elements = interactiveNodes.take(30).mapIndexed { idx, node ->
            ScreenInteractionElement(
                index = idx + 1,
                text = node.text,
                contentDescription = node.contentDescription,
                viewId = node.viewIdResourceName,
                className = node.className,
                isClickable = node.isClickable,
                isEditable = node.isEditable,
                isScrollable = node.isScrollable,
                bounds = node.boundsInScreen
            )
        }

        return ScreenInteractionMap(
            packageName = snapshot.packageName,
            totalElements = snapshot.totalNodeCount,
            interactiveElementsCount = interactiveNodes.size,
            elements = elements
        )
    }

    /**
     * Reports which generic execution mechanisms appear available for a discovered candidate without executing any action.
     */
    fun reportMechanismAvailability(candidate: UiNodeInfo?): MechanismAvailability {
        if (candidate == null) {
            return MechanismAvailability()
        }

        val hasClick = candidate.isClickable
        val hasParentClick = !candidate.parentClassName.isNullOrBlank()
        val hasBounds = !candidate.boundsInScreen.isNullOrBlank()
        val isEditable = candidate.isEditable
        val isFocusable = candidate.isFocusable

        val preferred = when {
            hasClick -> "Accessibility ACTION_CLICK"
            isEditable -> "Accessibility ACTION_SET_TEXT"
            hasBounds -> "Bounds Gesture Tap Fallback"
            else -> "Inspection Only"
        }

        return MechanismAvailability(
            accessibilityClick = hasClick,
            clickableParent = hasParentClick,
            gestureFallback = hasBounds,
            focusAvailable = isFocusable,
            setTextCompatible = isEditable,
            preferredMechanism = preferred,
            actionDispatched = false
        )
    }

    /**
     * Runs an automated auto-detect inspection of the current screen observation without taking actions.
     */
    fun autoDetectScreen(snapshot: UiSnapshot): AutoDetectResult {
        val interactiveCount = snapshot.allNodes.count { it.isClickable || it.isEditable || it.isScrollable }
        return AutoDetectResult(
            packageName = snapshot.packageName,
            isRootAvailable = snapshot.isRootAvailable,
            totalNodeCount = snapshot.totalNodeCount,
            interactiveCount = interactiveCount,
            editableCount = snapshot.editableNodeCount,
            scrollableCount = snapshot.scrollableNodeCount,
            targetDiscoveryAvailable = snapshot.isRootAvailable && snapshot.totalNodeCount > 0,
            visualFallbackAvailable = true,
            actionDispatched = false
        )
    }

    fun resolveTargetWithAmbiguity(snapshot: UiSnapshot, target: String): TargetResolutionResult {
        if (target.isBlank()) {
            return TargetResolutionResult(
                match = null,
                candidateCount = 0,
                isAmbiguous = false,
                status = TargetResolutionStatus.NOT_FOUND,
                explanation = "Target string is blank"
            )
        }

        val trimmedTarget = target.trim()

        // 1. View ID Matches
        val idMatches = snapshot.allNodes.filter {
            it.viewIdResourceName != null &&
                    (it.viewIdResourceName.endsWith(trimmedTarget, ignoreCase = true) ||
                     it.viewIdResourceName.contains(trimmedTarget, ignoreCase = true))
        }
        if (idMatches.isNotEmpty()) {
            val isAmbiguous = idMatches.size > 1
            val best = idMatches.first()

            val status = if (!best.isEnabled || !best.isVisibleToUser) {
                TargetResolutionStatus.NOT_ACTIONABLE
            } else if (isAmbiguous) {
                TargetResolutionStatus.AMBIGUOUS
            } else {
                TargetResolutionStatus.FOUND_UNIQUE
            }

            return TargetResolutionResult(
                match = ResolutionMatch(
                    node = best,
                    matchMethod = "VIEW_ID",
                    confidence = if (isAmbiguous) 0.70 else 1.0,
                    reason = "Matched View ID resource name: ${best.viewIdResourceName}"
                ),
                candidateCount = idMatches.size,
                isAmbiguous = isAmbiguous,
                status = status,
                explanation = if (isAmbiguous) "Multiple candidates (${idMatches.size}) matched View ID '$target'" else "Uniquely resolved View ID '$target'"
            )
        }

        // 2. Exact Visible Text Matches
        val exactTextMatches = snapshot.allNodes.filter {
            it.text?.equals(trimmedTarget, ignoreCase = true) == true
        }
        if (exactTextMatches.isNotEmpty()) {
            val isAmbiguous = exactTextMatches.size > 1
            val best = exactTextMatches.first()

            val status = if (!best.isEnabled || !best.isVisibleToUser) {
                TargetResolutionStatus.NOT_ACTIONABLE
            } else if (isAmbiguous) {
                TargetResolutionStatus.AMBIGUOUS
            } else {
                TargetResolutionStatus.FOUND_UNIQUE
            }

            return TargetResolutionResult(
                match = ResolutionMatch(
                    node = best,
                    matchMethod = "EXACT_TEXT",
                    confidence = if (isAmbiguous) 0.65 else 0.95,
                    reason = "Matched exact visible text: '${best.text}'"
                ),
                candidateCount = exactTextMatches.size,
                isAmbiguous = isAmbiguous,
                status = status,
                explanation = if (isAmbiguous) "Multiple candidates (${exactTextMatches.size}) matched exact text '$target'" else "Uniquely resolved exact text '$target'"
            )
        }

        // 3. Content Description Matches
        val contentDescMatches = snapshot.allNodes.filter {
            it.contentDescription?.equals(trimmedTarget, ignoreCase = true) == true
        }
        if (contentDescMatches.isNotEmpty()) {
            val isAmbiguous = contentDescMatches.size > 1
            val best = contentDescMatches.first()

            val status = if (!best.isEnabled || !best.isVisibleToUser) {
                TargetResolutionStatus.NOT_ACTIONABLE
            } else if (isAmbiguous) {
                TargetResolutionStatus.AMBIGUOUS
            } else {
                TargetResolutionStatus.FOUND_UNIQUE
            }

            return TargetResolutionResult(
                match = ResolutionMatch(
                    node = best,
                    matchMethod = "CONTENT_DESCRIPTION",
                    confidence = if (isAmbiguous) 0.60 else 0.90,
                    reason = "Matched content description: '${best.contentDescription}'"
                ),
                candidateCount = contentDescMatches.size,
                isAmbiguous = isAmbiguous,
                status = status,
                explanation = if (isAmbiguous) "Multiple candidates (${contentDescMatches.size}) matched content description '$target'" else "Uniquely resolved content description '$target'"
            )
        }

        // 4. Partial text or content description matches
        val partialMatches = snapshot.allNodes.filter {
            (it.text != null && it.text.contains(trimmedTarget, ignoreCase = true)) ||
                    (it.contentDescription != null && it.contentDescription.contains(trimmedTarget, ignoreCase = true))
        }
        if (partialMatches.isNotEmpty()) {
            val isAmbiguous = partialMatches.size > 1
            val best = partialMatches.first()

            val status = if (!best.isEnabled || !best.isVisibleToUser) {
                TargetResolutionStatus.NOT_ACTIONABLE
            } else if (isAmbiguous) {
                TargetResolutionStatus.AMBIGUOUS
            } else {
                TargetResolutionStatus.FOUND_UNIQUE
            }

            return TargetResolutionResult(
                match = ResolutionMatch(
                    node = best,
                    matchMethod = "ACCESSIBILITY_PROPERTIES",
                    confidence = if (isAmbiguous) 0.50 else 0.75,
                    reason = "Matched partial text/description containing '$trimmedTarget'"
                ),
                candidateCount = partialMatches.size,
                isAmbiguous = isAmbiguous,
                status = status,
                explanation = if (isAmbiguous) "Multiple candidates (${partialMatches.size}) matched partial text '$target'" else "Resolved partial text '$target'"
            )
        }

        return TargetResolutionResult(
            match = null,
            candidateCount = 0,
            isAmbiguous = false,
            status = TargetResolutionStatus.NOT_FOUND,
            explanation = "Target '$target' not found in UI snapshot"
        )
    }

    private fun scoreNodeCandidate(node: UiNodeInfo, target: String): Double {
        var score = 0.0
        val lowerTarget = target.lowercase()

        // 1. Match type scores
        if (node.viewIdResourceName?.endsWith(target, ignoreCase = true) == true) {
            score += 0.40
        }
        if (node.text?.equals(target, ignoreCase = true) == true) {
            score += 0.30
        } else if (node.text?.lowercase()?.contains(lowerTarget) == true) {
            score += 0.15
        }
        if (node.contentDescription?.equals(target, ignoreCase = true) == true) {
            score += 0.25
        } else if (node.contentDescription?.lowercase()?.contains(lowerTarget) == true) {
            score += 0.10
        }

        if (score == 0.0) return 0.0

        // 2. Actionability bonuses
        if (node.isClickable || node.isEditable) score += 0.10
        if (node.isEnabled) score += 0.10
        if (node.isFocused) score += 0.05
        if (node.isVisibleToUser) score += 0.05

        return score.coerceAtMost(1.0)
    }

    /**
     * Resolves an editable target input node (e.g. search bar or text field).
     * Prefers currently focused editable node, then exact match on hint/text/contentDesc, then any editable node.
     */
    fun resolveEditableTarget(snapshot: UiSnapshot, hintOrLabel: String? = null): TargetResolutionResult {
        // 1. If currently focused editable node exists
        val allFocused = if (snapshot.focusedNodes.isNotEmpty()) snapshot.focusedNodes else snapshot.allNodes.filter { it.isFocused }
        val focusedEditable = allFocused.firstOrNull { it.isEditable && it.isEnabled }
        if (focusedEditable != null) {
            return TargetResolutionResult(
                match = ResolutionMatch(
                    node = focusedEditable,
                    matchMethod = "FOCUSED_EDITABLE",
                    confidence = 1.0,
                    reason = "Resolved currently focused editable field"
                ),
                candidateCount = 1,
                isAmbiguous = false,
                status = TargetResolutionStatus.FOUND_UNIQUE,
                explanation = "Resolved focused editable input field"
            )
        }

        // 2. If hint/label specified, match editable node by text/contentDesc/viewId
        if (!hintOrLabel.isNullOrBlank()) {
            val res = resolveTargetWithAmbiguity(snapshot, hintOrLabel)
            if (res.match != null && res.match.node.isEditable) {
                return res
            }
        }

        // 3. Fallback: single editable field on screen
        val allEditables = if (snapshot.editableNodes.isNotEmpty()) snapshot.editableNodes else snapshot.allNodes.filter { it.isEditable }
        val activeEditables = allEditables.filter { it.isEnabled }
        if (activeEditables.isNotEmpty()) {
            val isAmbiguous = activeEditables.size > 1
            val best = activeEditables.first()
            return TargetResolutionResult(
                match = ResolutionMatch(
                    node = best,
                    matchMethod = "FIRST_EDITABLE",
                    confidence = if (isAmbiguous) 0.60 else 0.85,
                    reason = "Resolved editable field from screen"
                ),
                candidateCount = activeEditables.size,
                isAmbiguous = isAmbiguous,
                status = if (isAmbiguous) TargetResolutionStatus.AMBIGUOUS else TargetResolutionStatus.FOUND_UNIQUE,
                explanation = if (isAmbiguous) "Multiple editable fields (${activeEditables.size}) present on screen" else "Uniquely resolved single editable field"
            )
        }

        return TargetResolutionResult(
            match = null,
            candidateCount = 0,
            isAmbiguous = false,
            status = TargetResolutionStatus.NOT_FOUND,
            explanation = "No editable input field found on current screen"
        )
    }

    /**
     * Inspects a UiSnapshot to determine if a sign-in or login screen is being presented.
     */
    fun detectAuthState(snapshot: UiSnapshot): AuthState {
        for (text in snapshot.visibleTexts + snapshot.contentDescriptions) {
            val lower = text.lowercase()
            if (LOGIN_PROMPT_KEYWORDS.any { lower.contains(it) }) {
                Log.w(TAG, "AUTH_STATE_DETECTED: LOGIN_REQUIRED due to matched text: '$text'")
                return AuthState.LOGIN_REQUIRED
            }
        }
        return AuthState.AUTHENTICATED
    }
}
