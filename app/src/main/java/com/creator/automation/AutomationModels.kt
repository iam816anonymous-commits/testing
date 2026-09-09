package com.creator.automation

import java.util.UUID

data class UiSnapshot(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val visibleTexts: List<String> = emptyList(),
    val contentDescriptions: List<String> = emptyList(),
    val viewIds: List<String> = emptyList(),
    val clickableNodes: List<UiNodeInfo> = emptyList(),
    val scrollableNodes: List<UiNodeInfo> = emptyList(),
    val allNodes: List<UiNodeInfo> = emptyList()
) {
    val totalNodeCount: Int get() = allNodes.size
    val visibleNodeCount: Int get() = allNodes.count { it.isVisibleToUser }
    val clickableNodeCount: Int get() = clickableNodes.size
    val scrollableNodeCount: Int get() = scrollableNodes.size
}

data class UiNodeInfo(
    val text: String? = null,
    val contentDescription: String? = null,
    val viewIdResourceName: String? = null,
    val className: String? = null,
    val isClickable: Boolean = false,
    val isScrollable: Boolean = false,
    val isVisibleToUser: Boolean = true,
    val isEnabled: Boolean = true,
    val boundsInScreen: String? = null,
    val nodeRef: Any? = null
)

enum class ActionType {
    OPEN_URL,
    WAIT,
    WAIT_FOR_TEXT,
    CLICK_TEXT,
    SCROLL,
    GO_BACK,
    CAPTURE_SCREEN,
    READ_VISIBLE_UI,
    VERIFY_TEXT,
    CHECK_AUTH_STATE,
    END
}

data class AutomationAction(
    val type: ActionType,
    val targetValue: String? = null,
    val timeoutMs: Long = 10000L
)

enum class ActionResultStatus {
    SUCCESS,
    FAILED,
    TIMEOUT,
    NOT_FOUND,
    BLOCKED
}

enum class ExecutionReason {
    NONE,
    LOGIN_REQUIRED,
    ACCESSIBILITY_DISABLED,
    UI_NOT_FOUND,
    SCREENSHOT_FAILED,
    UNSUPPORTED_ANDROID_VERSION,
    TIMEOUT,
    VERIFICATION_FAILED
}

enum class AuthState {
    AUTHENTICATED,
    LOGIN_REQUIRED,
    UNKNOWN
}

data class ActionResult(
    val status: ActionResultStatus,
    val reason: ExecutionReason = ExecutionReason.NONE,
    val message: String? = null,
    val matchedNode: UiNodeInfo? = null,
    val matchMethod: String? = null,
    val screenshotPath: String? = null,
    val snapshot: UiSnapshot? = null,
    val authState: AuthState = AuthState.UNKNOWN
)

data class RetryPolicy(
    val maxRetries: Int = 2,
    val retryDelayMs: Long = 2000L
)

data class WorkflowStep(
    val id: String,
    val action: AutomationAction,
    val verificationAction: AutomationAction? = null
)

data class Workflow(
    val id: String,
    val name: String,
    val targetPackage: String? = null,
    val enabled: Boolean = true,
    val steps: List<WorkflowStep>,
    val timeoutMs: Long = 60000L,
    val retryPolicy: RetryPolicy = RetryPolicy()
)
