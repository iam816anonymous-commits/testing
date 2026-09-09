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
         */
        fun captureSnapshot(root: AccessibilityNodeInfo?, fallbackPackageName: String = ""): UiSnapshot {
            if (root == null) {
                return UiSnapshot(packageName = fallbackPackageName)
            }

            val packageName = root.packageName?.toString() ?: fallbackPackageName
            val visibleTexts = mutableListOf<String>()
            val contentDescriptions = mutableListOf<String>()
            val viewIds = mutableListOf<String>()
            val clickableNodes = mutableListOf<UiNodeInfo>()
            val scrollableNodes = mutableListOf<UiNodeInfo>()
            val editableNodes = mutableListOf<UiNodeInfo>()
            val focusedNodes = mutableListOf<UiNodeInfo>()
            val allNodes = mutableListOf<UiNodeInfo>()

            fun traverse(node: AccessibilityNodeInfo?) {
                if (node == null) return

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
                    isScrollable = node.isScrollable,
                    isEditable = isEditable,
                    isFocused = isFocused,
                    isFocusable = isFocusable,
                    isVisibleToUser = node.isVisibleToUser,
                    isEnabled = node.isEnabled,
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
                    traverse(node.getChild(i))
                }
            }

            traverse(root)

            val snapshot = UiSnapshot(
                packageName = packageName,
                timestamp = System.currentTimeMillis(),
                visibleTexts = visibleTexts.distinct(),
                contentDescriptions = contentDescriptions.distinct(),
                viewIds = viewIds.distinct(),
                clickableNodes = clickableNodes,
                scrollableNodes = scrollableNodes,
                editableNodes = editableNodes,
                focusedNodes = focusedNodes,
                allNodes = allNodes
            )

            Log.d(TAG, "UI_SNAPSHOT_CREATED: pkg=$packageName, totalNodes=${snapshot.totalNodeCount}, visibleTexts=${snapshot.visibleTexts.size}, editables=${snapshot.editableNodes.size}")
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
            it.viewIdResourceName != null && it.viewIdResourceName.endsWith(trimmedTarget, ignoreCase = true)
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

    /**
     * Resolves an editable target input node (e.g. search bar or text field).
     * Prefers currently focused editable node, then exact match on hint/text/contentDesc, then any editable node.
     */
    fun resolveEditableTarget(snapshot: UiSnapshot, hintOrLabel: String? = null): TargetResolutionResult {
        // 1. If currently focused editable node exists
        val focusedEditable = snapshot.focusedNodes.firstOrNull { it.isEditable && it.isEnabled }
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
        val activeEditables = snapshot.editableNodes.filter { it.isEnabled }
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
