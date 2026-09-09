package com.creator.automation

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

data class ResolutionMatch(
    val node: UiNodeInfo,
    val matchMethod: String,
    val confidence: Double,
    val reason: String
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
            val allNodes = mutableListOf<UiNodeInfo>()

            fun traverse(node: AccessibilityNodeInfo?) {
                if (node == null) return

                val text = node.text?.toString()?.trim()
                val contentDesc = node.contentDescription?.toString()?.trim()
                val viewId = node.viewIdResourceName
                val bounds = Rect()
                node.getBoundsInScreen(bounds)

                val uiNode = UiNodeInfo(
                    text = text,
                    contentDescription = contentDesc,
                    viewIdResourceName = viewId,
                    className = node.className?.toString(),
                    isClickable = node.isClickable,
                    isScrollable = node.isScrollable,
                    isVisibleToUser = node.isVisibleToUser,
                    isEnabled = node.isEnabled,
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
                allNodes = allNodes
            )

            Log.d(TAG, "UI_SNAPSHOT_CREATED: pkg=$packageName, totalNodes=${snapshot.totalNodeCount}, visibleTexts=${snapshot.visibleTexts.size}")
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
        if (target.isBlank()) return null

        val trimmedTarget = target.trim()

        // 1. View ID Match
        val idMatch = snapshot.allNodes.firstOrNull {
            it.viewIdResourceName != null && it.viewIdResourceName.endsWith(trimmedTarget, ignoreCase = true)
        }
        if (idMatch != null) {
            Log.d(TAG, "NODE_MATCHED: VIEW_ID -> ${idMatch.viewIdResourceName}")
            return ResolutionMatch(
                node = idMatch,
                matchMethod = "VIEW_ID",
                confidence = 1.0,
                reason = "Matched View ID resource name: ${idMatch.viewIdResourceName}"
            )
        }

        // 2. Exact Visible Text Match
        val exactTextMatch = snapshot.allNodes.firstOrNull {
            it.text?.equals(trimmedTarget, ignoreCase = true) == true
        }
        if (exactTextMatch != null) {
            Log.d(TAG, "NODE_MATCHED: EXACT_TEXT -> '${exactTextMatch.text}'")
            return ResolutionMatch(
                node = exactTextMatch,
                matchMethod = "EXACT_TEXT",
                confidence = 0.95,
                reason = "Matched exact visible text: '${exactTextMatch.text}'"
            )
        }

        // 3. Content Description Match
        val contentDescMatch = snapshot.allNodes.firstOrNull {
            it.contentDescription?.equals(trimmedTarget, ignoreCase = true) == true
        }
        if (contentDescMatch != null) {
            Log.d(TAG, "NODE_MATCHED: CONTENT_DESCRIPTION -> '${contentDescMatch.contentDescription}'")
            return ResolutionMatch(
                node = contentDescMatch,
                matchMethod = "CONTENT_DESCRIPTION",
                confidence = 0.90,
                reason = "Matched content description: '${contentDescMatch.contentDescription}'"
            )
        }

        // 4. Partial text or content description match
        val partialTextMatch = snapshot.allNodes.firstOrNull {
            (it.text != null && it.text.contains(trimmedTarget, ignoreCase = true)) ||
                    (it.contentDescription != null && it.contentDescription.contains(trimmedTarget, ignoreCase = true))
        }
        if (partialTextMatch != null) {
            Log.d(TAG, "NODE_MATCHED: ACCESSIBILITY_PROPERTIES -> '${partialTextMatch.text ?: partialTextMatch.contentDescription}'")
            return ResolutionMatch(
                node = partialTextMatch,
                matchMethod = "ACCESSIBILITY_PROPERTIES",
                confidence = 0.75,
                reason = "Matched partial text/description containing '$trimmedTarget'"
            )
        }

        return null
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
