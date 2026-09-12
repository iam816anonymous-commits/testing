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
    val editableNodes: List<UiNodeInfo> = emptyList(),
    val focusedNodes: List<UiNodeInfo> = emptyList(),
    val allNodes: List<UiNodeInfo> = emptyList()
) {
    val totalNodeCount: Int get() = allNodes.size
    val visibleNodeCount: Int get() = allNodes.count { it.isVisibleToUser }
    val clickableNodeCount: Int get() = clickableNodes.size
    val scrollableNodeCount: Int get() = scrollableNodes.size
    val editableNodeCount: Int get() = editableNodes.size
}

data class UiNodeInfo(
    val text: String? = null,
    val contentDescription: String? = null,
    val viewIdResourceName: String? = null,
    val className: String? = null,
    val isClickable: Boolean = false,
    val isLongClickable: Boolean = false,
    val isScrollable: Boolean = false,
    val isEditable: Boolean = false,
    val isFocused: Boolean = false,
    val isFocusable: Boolean = false,
    val isVisibleToUser: Boolean = true,
    val isEnabled: Boolean = true,
    val isChecked: Boolean = false,
    val isSelected: Boolean = false,
    val parentClassName: String? = null,
    val parentText: String? = null,
    val boundsInScreen: String? = null,
    val nodeRef: Any? = null
)

data class TargetBounds(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

enum class VisualizationActionState {
    OBSERVING,
    TARGET_FOUND,
    CLICKING,
    TYPING,
    WAITING,
    VERIFYING,
    SUCCESS,
    FAILED,
    RECOVERING
}

data class AutomationVisualizationState(
    val actionState: VisualizationActionState = VisualizationActionState.OBSERVING,
    val targetText: String? = null,
    val targetViewId: String? = null,
    val targetClassName: String? = null,
    val targetBounds: TargetBounds? = null,
    val cursorX: Int? = targetBounds?.centerX,
    val cursorY: Int? = targetBounds?.centerY,
    val packageName: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class ActionType {
    LAUNCH_APP,
    OPEN_URL,
    WAIT,
    WAIT_FOR_TEXT,
    CLICK_TEXT,
    LONG_CLICK,
    TYPE_TEXT,
    CLEAR_TEXT,
    SCROLL,
    SCROLL_UP,
    SCROLL_DOWN,
    SWIPE,
    GO_BACK,
    PRESS_HOME,
    PRESS_RECENTS,
    PRESS_ENTER,
    SUBMIT_INPUT,
    TOGGLE_HARDWARE,
    CAPTURE_SCREEN,
    READ_VISIBLE_UI,
    REFRESH_OBSERVATION,
    VERIFY_TEXT,
    CHECK_AUTH_STATE,
    EXECUTE_LEARNED_DECISION,
    END
}

enum class ActionSemantics {
    READ_ONLY,
    IDEMPOTENT,
    REPEATABLE,
    NON_IDEMPOTENT,
    HIGH_RISK
}

enum class PreconditionType {
    PACKAGE_MATCH,
    TEXT_PRESENT,
    VIEW_ID_PRESENT,
    EDITABLE_PRESENT,
    SCROLLABLE_PRESENT,
    AUTH_AUTHENTICATED
}

data class ActionPrecondition(
    val type: PreconditionType,
    val expectedValue: String? = null
)

enum class WaitConditionType {
    WAIT_FOR_TEXT,
    WAIT_FOR_VIEW_ID,
    WAIT_FOR_PACKAGE,
    WAIT_FOR_STATE_CHANGE,
    WAIT_FOR_VISUAL_CHANGE,
    WAIT_FOR_STATE_SIGNATURE,
    NODE_APPEARS,
    NODE_DISAPPEARS,
    TEXT_DISAPPEARS,
    TARGET_BECOMES_ENABLED,
    EXPECTED_SAME_SCREEN_PROGRESS
}

data class WaitCondition(
    val type: WaitConditionType,
    val expectedValue: String? = null,
    val timeoutMs: Long = 10000L,
    val pollIntervalMs: Long = 500L
)

data class WaitResult(
    val success: Boolean,
    val durationMs: Long,
    val matchedValue: String? = null,
    val failureReason: String? = null
)

data class AutomationAction(
    val type: ActionType,
    val targetValue: String? = null,
    val inputData: String? = null,
    val timeoutMs: Long = 10000L,
    val semantics: ActionSemantics = ActionSemantics.REPEATABLE,
    val preconditions: List<ActionPrecondition> = emptyList(),
    val waitCondition: WaitCondition? = null
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
    VERIFICATION_FAILED,
    LEARNING_REQUIRED,
    PRECONDITION_FAILED,
    AMBIGUOUS_TARGET,
    STUCK,
    USER_REQUIRED,
    STALE_OBSERVATION,
    APP_NOT_INSTALLED,
    AMBIGUOUS_APPLICATION,
    UNSUPPORTED_SUBMISSION_MECHANISM
}

enum class ExecutionTrigger {
    MANUAL,
    SCHEDULED
}

enum class ExecutionState {
    IDLE,
    SCHEDULED,
    RUNNING,
    SUCCESS,
    FAILED,
    BLOCKED,
    CANCELLED,
    NEEDS_USER_INPUT
}

enum class AuthState {
    AUTHENTICATED,
    LOGIN_REQUIRED,
    UNKNOWN
}

data class ActionResult(
    val status: ActionResultStatus,
    val reason: ExecutionReason = ExecutionReason.NONE,
    val trigger: ExecutionTrigger = ExecutionTrigger.MANUAL,
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
    val verificationAction: AutomationAction? = null,
    val conditionBranch: BranchCondition? = null
)

data class BranchCondition(
    val ifConditionType: WaitConditionType,
    val ifExpectedValue: String,
    val thenStepId: String,
    val elseStepId: String? = null
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
