package com.creator.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class DeviceActionExecutor(
    private val context: Context,
    private val actionResolver: ActionResolver = ActionResolver(),
    private val auditDao: ActionAuditDao = AppDatabase.getDatabase(context).actionAuditDao(),
    private val waitEngine: WaitEngine = WaitEngine(actionResolver),
    private val appResolver: AppResolver = AppResolver(context)
) {

    companion object {
        private const val TAG = "DeviceActionExecutor"

        fun redactSensitiveText(targetValue: String?): String? {
            if (targetValue == null) return null
            val lower = targetValue.lowercase()
            if (lower.contains("password") || lower.contains("secret") || lower.contains("token") || lower.contains("pin")) {
                return "[REDACTED]"
            }
            return targetValue
        }
    }

    suspend fun executeAndAudit(
        workflowId: String,
        action: AutomationAction,
        service: AutomationAccessibilityService,
        trigger: ExecutionTrigger = ExecutionTrigger.MANUAL,
        verificationAction: AutomationAction? = null,
        screenObservationProvider: ObservationProvider? = null
    ): ActionResult {
        val startTime = System.currentTimeMillis()

        // 1. Capture Before State (Always capture fresh state)
        val beforeRoot = service.getRootNode()
        val beforeSnapshot = ActionResolver.captureSnapshot(beforeRoot, service.packageName ?: "")
        val beforeStateSig = StateSignatureGenerator.generateSignature(beforeSnapshot)

        val redactedTarget = redactSensitiveText(action.targetValue)
        val redactedInput = redactSensitiveText(action.inputData)

        Log.i(TAG, "EXECUTING_ACTION: Type=${action.type}, Target='$redactedTarget', Input='$redactedInput', Semantics=${action.semantics}, Pkg='${beforeSnapshot.packageName}'")

        // 2. Check Action Preconditions (Evaluated against fresh snapshot before dispatch)
        val preconditionResult = checkPreconditions(action.preconditions, beforeSnapshot)
        if (!preconditionResult.success) {
            Log.w(TAG, "PRECONDITION_FAILED: ${preconditionResult.failureReason}")
            val auditRecord = ActionAuditRecord(
                timestamp = System.currentTimeMillis(),
                workflowId = workflowId,
                trigger = trigger.name,
                activePackage = beforeSnapshot.packageName,
                beforeStateSignature = beforeStateSig,
                actionType = action.type.name,
                targetIdentifier = redactedTarget,
                actionParametersSummary = "Semantics=${action.semantics}, Preconditions=${action.preconditions.size}",
                reason = ExecutionReason.PRECONDITION_FAILED.name,
                dispatchResult = ActionResultStatus.BLOCKED.name,
                verificationStatus = VerificationStatus.FAILED.name,
                success = false,
                failureReason = preconditionResult.failureReason
            )
            auditDao.insertAuditRecord(auditRecord)
            return ActionResult(
                status = ActionResultStatus.BLOCKED,
                reason = ExecutionReason.PRECONDITION_FAILED,
                trigger = trigger,
                message = preconditionResult.failureReason,
                snapshot = beforeSnapshot
            )
        }

        // 3. Dispatch Action via Generic Accessibility Engine
        val dispatchResult = when (action.type) {
            ActionType.LAUNCH_APP -> {
                val launchRes = performLaunchApp(action.targetValue)
                if (launchRes.status == ActionResultStatus.SUCCESS && action.targetValue != null) {
                    val appRes = appResolver.resolveApplication(action.targetValue)
                    if (appRes.packageName != null && action.waitCondition?.type == WaitConditionType.WAIT_FOR_PACKAGE) {
                        Log.i(TAG, "LAUNCH_APP_DYNAMIC_PACKAGE_PROPAGATION: Propagating resolved package '${appRes.packageName}' to waitCondition")
                        val updatedWait = action.waitCondition.copy(expectedValue = appRes.packageName)
                        val waitRes = waitEngine.waitUntil(updatedWait, service, screenObservationProvider, beforeStateSig)
                        if (!waitRes.success) {
                            return launchRes.copy(status = ActionResultStatus.TIMEOUT, reason = ExecutionReason.TIMEOUT, message = waitRes.failureReason)
                        }
                    }
                }
                launchRes
            }
            ActionType.OPEN_URL -> performOpenUrl(action.targetValue)
            ActionType.CHECK_AUTH_STATE -> performAuthCheck(beforeSnapshot)
            ActionType.WAIT -> performWait(action.targetValue)
            ActionType.WAIT_FOR_TEXT, ActionType.VERIFY_TEXT -> performWaitForText(action.targetValue, action.timeoutMs, service)
            ActionType.CLICK_TEXT -> performClickText(action.targetValue, service, beforeSnapshot, beforeStateSig)
            ActionType.LONG_CLICK -> performLongClick(action.targetValue, beforeSnapshot)
            ActionType.TYPE_TEXT -> performTypeText(action.targetValue, action.inputData, service, beforeSnapshot)
            ActionType.CLEAR_TEXT -> performClearText(action.targetValue, beforeSnapshot)
            ActionType.PRESS_ENTER, ActionType.SUBMIT_INPUT -> performSubmitInput(service, beforeSnapshot)
            ActionType.SCROLL, ActionType.SCROLL_DOWN -> performScroll(service, beforeSnapshot, forward = true, beforeStateSig = beforeStateSig)
            ActionType.SCROLL_UP -> performScroll(service, beforeSnapshot, forward = false, beforeStateSig = beforeStateSig)
            ActionType.GO_BACK -> performGoBack(service)
            ActionType.PRESS_HOME -> performGlobalHome(service)
            ActionType.PRESS_RECENTS -> performGlobalRecents(service)
            ActionType.CAPTURE_SCREEN -> performCaptureScreen(service, screenObservationProvider)
            ActionType.READ_VISIBLE_UI, ActionType.REFRESH_OBSERVATION -> ActionResult(status = ActionResultStatus.SUCCESS, snapshot = beforeSnapshot)
            else -> ActionResult(status = ActionResultStatus.SUCCESS, snapshot = beforeSnapshot)
        }

        // 4. Evaluate WaitCondition if attached to action
        var finalDispatchResult = dispatchResult
        if (dispatchResult.status == ActionResultStatus.SUCCESS && action.waitCondition != null) {
            val waitRes = waitEngine.waitUntil(
                condition = action.waitCondition,
                service = service,
                screenProvider = screenObservationProvider,
                initialSignature = beforeStateSig
            )
            if (!waitRes.success) {
                Log.w(TAG, "ACTION_WAIT_CONDITION_FAILED: ${waitRes.failureReason}")
                finalDispatchResult = ActionResult(
                    status = ActionResultStatus.TIMEOUT,
                    reason = ExecutionReason.TIMEOUT,
                    message = waitRes.failureReason ?: "Wait condition timed out"
                )
            }
        }

        val durationMs = System.currentTimeMillis() - startTime

        // 5. Capture After State & Compare
        val afterRoot = service.getRootNode()
        val afterSnapshot = ActionResolver.captureSnapshot(afterRoot, service.packageName ?: "")
        val afterStateSig = StateSignatureGenerator.generateSignature(afterSnapshot)

        val stateChange = compareStates(beforeStateSig, afterStateSig, verificationAction != null)

        // 6. Verify Result
        val verificationStatus = if (finalDispatchResult.status == ActionResultStatus.SUCCESS) {
            if (verificationAction != null) {
                val verifyRes = performWaitForText(verificationAction.targetValue, verificationAction.timeoutMs, service)
                if (verifyRes.status == ActionResultStatus.SUCCESS) VerificationStatus.SUCCESSFULLY_VERIFIED else VerificationStatus.FAILED
            } else if (stateChange != StateChangeResult.NO_CHANGE) {
                VerificationStatus.SUCCESSFULLY_VERIFIED
            } else {
                VerificationStatus.DISPATCHED
            }
        } else {
            VerificationStatus.FAILED
        }

        val isOverallSuccess = finalDispatchResult.status == ActionResultStatus.SUCCESS && verificationStatus != VerificationStatus.FAILED
        val effectiveReason = if (finalDispatchResult.status != ActionResultStatus.SUCCESS) finalDispatchResult.reason else if (verificationStatus == VerificationStatus.FAILED) ExecutionReason.VERIFICATION_FAILED else ExecutionReason.NONE

        // 7. Persist Audit Record
        val auditRecord = ActionAuditRecord(
            timestamp = System.currentTimeMillis(),
            workflowId = workflowId,
            trigger = trigger.name,
            activePackage = beforeSnapshot.packageName,
            beforeStateSignature = beforeStateSig,
            actionType = action.type.name,
            targetIdentifier = redactedTarget,
            actionParametersSummary = "Type=${action.type}, Timeout=${action.timeoutMs}ms, Semantics=${action.semantics}",
            dispatchResult = finalDispatchResult.status.name,
            afterStateSignature = afterStateSig,
            stateChangeResult = stateChange.name,
            verificationStatus = verificationStatus.name,
            reason = effectiveReason.name,
            success = isOverallSuccess,
            failureReason = if (!isOverallSuccess) finalDispatchResult.message ?: "Verification failed" else null,
            durationMs = durationMs
        )

        try {
            auditDao.insertAuditRecord(auditRecord)
            Log.i(TAG, "AUDIT_LOGGED: Action ${action.type} -> Success=$isOverallSuccess, Verification=$verificationStatus, Reason=$effectiveReason")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing action audit record", e)
        }

        return finalDispatchResult.copy(
            snapshot = afterSnapshot,
            reason = effectiveReason,
            message = if (!isOverallSuccess) finalDispatchResult.message ?: "Verification failed" else finalDispatchResult.message
        )
    }

    fun checkPreconditions(
        preconditions: List<ActionPrecondition>,
        snapshot: UiSnapshot
    ): PreconditionCheckResult {
        for (pre in preconditions) {
            val expected = pre.expectedValue ?: ""
            val satisfied = when (pre.type) {
                PreconditionType.PACKAGE_MATCH -> snapshot.packageName.equals(expected, ignoreCase = true)
                PreconditionType.TEXT_PRESENT -> snapshot.visibleTexts.any { it.contains(expected, ignoreCase = true) }
                PreconditionType.VIEW_ID_PRESENT -> snapshot.viewIds.any { it.endsWith(expected, ignoreCase = true) }
                PreconditionType.EDITABLE_PRESENT -> snapshot.editableNodes.isNotEmpty() || snapshot.allNodes.any { it.className?.contains("EditText", ignoreCase = true) == true }
                PreconditionType.SCROLLABLE_PRESENT -> snapshot.scrollableNodes.isNotEmpty()
                PreconditionType.AUTH_AUTHENTICATED -> actionResolver.detectAuthState(snapshot) == AuthState.AUTHENTICATED
            }

            if (!satisfied) {
                return PreconditionCheckResult(
                    success = false,
                    failureReason = "Precondition '${pre.type}' unsatisfied on package '${snapshot.packageName}' (Expected '$expected')"
                )
            }
        }
        return PreconditionCheckResult(success = true)
    }

    fun compareStates(beforeSig: String, afterSig: String, isVerificationExpected: Boolean): StateChangeResult {
        return when {
            beforeSig == afterSig -> StateChangeResult.NO_CHANGE
            isVerificationExpected -> StateChangeResult.EXPECTED_STATE_REACHED
            else -> StateChangeResult.STATE_CHANGED
        }
    }

    private fun performLaunchApp(appQuery: String?): ActionResult {
        if (appQuery.isNullOrBlank()) return ActionResult(status = ActionResultStatus.FAILED, message = "LAUNCH_APP requires target app name or package")
        val appRes = appResolver.resolveApplication(appQuery)

        return when (appRes.status) {
            AppResolutionStatus.SUCCESS -> {
                try {
                    val intent = appRes.launchIntent
                        ?: context.packageManager.getLaunchIntentForPackage(appRes.packageName!!)
                        ?.apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }

                    if (intent != null) {
                        context.startActivity(intent)
                        Log.i(TAG, "LAUNCHED_APP: Launched '${appRes.appLabel}' (${appRes.packageName})")
                        ActionResult(status = ActionResultStatus.SUCCESS)
                    } else {
                        ActionResult(status = ActionResultStatus.FAILED, reason = ExecutionReason.APP_NOT_INSTALLED, message = "No launch activity intent found for '${appRes.packageName}'")
                    }
                } catch (e: Exception) {
                    ActionResult(status = ActionResultStatus.FAILED, message = "Failed to launch '${appRes.packageName}': ${e.message}")
                }
            }
            AppResolutionStatus.APP_NOT_INSTALLED -> {
                ActionResult(status = ActionResultStatus.BLOCKED, reason = ExecutionReason.APP_NOT_INSTALLED, message = appRes.explanation)
            }
            AppResolutionStatus.AMBIGUOUS_APPLICATION -> {
                ActionResult(status = ActionResultStatus.BLOCKED, reason = ExecutionReason.AMBIGUOUS_APPLICATION, message = appRes.explanation)
            }
            else -> {
                ActionResult(status = ActionResultStatus.FAILED, message = appRes.explanation)
            }
        }
    }

    private fun performOpenUrl(url: String?): ActionResult {
        if (url.isNullOrBlank()) return ActionResult(status = ActionResultStatus.FAILED, message = "OPEN_URL requires target URL")
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(status = ActionResultStatus.SUCCESS)
        } catch (e: Exception) {
            ActionResult(status = ActionResultStatus.FAILED, message = e.message)
        }
    }

    private fun performAuthCheck(snapshot: UiSnapshot): ActionResult {
        val authState = actionResolver.detectAuthState(snapshot)
        return if (authState == AuthState.LOGIN_REQUIRED) {
            ActionResult(status = ActionResultStatus.BLOCKED, reason = ExecutionReason.LOGIN_REQUIRED, authState = AuthState.LOGIN_REQUIRED)
        } else {
            ActionResult(status = ActionResultStatus.SUCCESS, authState = AuthState.AUTHENTICATED)
        }
    }

    private suspend fun performWait(durationStr: String?): ActionResult {
        val duration = durationStr?.toLongOrNull() ?: 2000L
        kotlinx.coroutines.delay(duration)
        return ActionResult(status = ActionResultStatus.SUCCESS)
    }

    private suspend fun performWaitForText(targetText: String?, timeoutMs: Long, service: AutomationAccessibilityService): ActionResult {
        if (targetText.isNullOrBlank()) return ActionResult(status = ActionResultStatus.FAILED, message = "Target text required")
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val root = service.getRootNode()
            val snap = ActionResolver.captureSnapshot(root, service.packageName ?: "")
            if (actionResolver.detectAuthState(snap) == AuthState.LOGIN_REQUIRED) {
                return ActionResult(status = ActionResultStatus.BLOCKED, reason = ExecutionReason.LOGIN_REQUIRED, authState = AuthState.LOGIN_REQUIRED)
            }
            val res = actionResolver.resolveTargetWithAmbiguity(snap, targetText)
            if (res.isAmbiguous) {
                val redacted = redactSensitiveText(targetText)
                Log.w(TAG, "AMBIGUOUS_TARGET_BLOCKED: Target '$redacted' matched ${res.candidateCount} nodes during wait. Aborting dispatch.")
                return ActionResult(
                    status = ActionResultStatus.BLOCKED,
                    reason = ExecutionReason.AMBIGUOUS_TARGET,
                    message = "Target '$redacted' matched ${res.candidateCount} ambiguous UI candidates"
                )
            }
            if (res.match != null) {
                return ActionResult(status = ActionResultStatus.SUCCESS, matchedNode = res.match.node, matchMethod = res.match.matchMethod)
            }
            kotlinx.coroutines.delay(500L)
        }
        return ActionResult(status = ActionResultStatus.TIMEOUT, reason = ExecutionReason.TIMEOUT, message = "Timeout waiting for '$targetText'")
    }

    private suspend fun performClickText(
        targetText: String?,
        service: AutomationAccessibilityService,
        snapshot: UiSnapshot,
        beforeStateSig: String
    ): ActionResult {
        if (targetText.isNullOrBlank()) return ActionResult(status = ActionResultStatus.FAILED, message = "CLICK_TEXT requires target text")

        // Target Freshness & Stale Protection: Re-observe active window immediately prior to target resolution & dispatch
        val freshRoot = service.getRootNode()
        val freshSnapshot = if (freshRoot != null) ActionResolver.captureSnapshot(freshRoot, service.packageName ?: "") else snapshot
        val freshStateSig = StateSignatureGenerator.generateSignature(freshSnapshot)

        var res = actionResolver.resolveTargetWithAmbiguity(freshSnapshot, targetText)

        // If target was stale from prior signature, attempt re-resolution on fresh snapshot
        if (res.match == null && beforeStateSig != freshStateSig) {
            Log.w(TAG, "STALE_TARGET_DETECTED: State signature shifted ($beforeStateSig -> $freshStateSig). Re-observing and re-resolving target '${redactSensitiveText(targetText)}'")
            res = actionResolver.resolveTargetWithAmbiguity(freshSnapshot, targetText)
        }

        val match = res.match

        if (match == null) {
            Log.w(TAG, "CLICK_DIAGNOSTIC: package=${freshSnapshot.packageName}, target=${redactSensitiveText(targetText)}, result=TARGET_NOT_FOUND")
            return ActionResult(status = ActionResultStatus.NOT_FOUND, reason = ExecutionReason.UI_NOT_FOUND, message = "TARGET_NOT_FOUND: Text '$targetText' not found in active window")
        }

        if (res.isAmbiguous) {
            val redacted = redactSensitiveText(targetText)
            Log.w(TAG, "CLICK_DIAGNOSTIC: package=${snapshot.packageName}, target=$redacted, candidateCount=${res.candidateCount}, result=AMBIGUOUS_TARGET")
            return ActionResult(
                status = ActionResultStatus.BLOCKED,
                reason = ExecutionReason.AMBIGUOUS_TARGET,
                message = "AMBIGUOUS_TARGET: Target '$redacted' matched ${res.candidateCount} candidate UI nodes"
            )
        }

        if (!match.node.isEnabled) {
            Log.w(TAG, "CLICK_DIAGNOSTIC: package=${snapshot.packageName}, target=${redactSensitiveText(targetText)}, result=TARGET_DISABLED")
            return ActionResult(status = ActionResultStatus.BLOCKED, reason = ExecutionReason.PRECONDITION_FAILED, message = "TARGET_DISABLED: Target '$targetText' is disabled")
        }

        val nodeRef = match.node.nodeRef as? AccessibilityNodeInfo
            ?: return ActionResult(status = ActionResultStatus.FAILED, reason = ExecutionReason.UI_NOT_FOUND, message = "Target nodeRef is missing")

        var targetNode: AccessibilityNodeInfo? = nodeRef
        while (targetNode != null && !targetNode.isClickable) {
            targetNode = targetNode.parent
        }

        val boundsRect = android.graphics.Rect()
        nodeRef.getBoundsInScreen(boundsRect)
        val targetBounds = TargetBounds(boundsRect.left, boundsRect.top, boundsRect.right, boundsRect.bottom)

        // Update live visualization overlay
        AutomationOverlayState.updateState(
            actionState = VisualizationActionState.CLICKING,
            targetText = redactSensitiveText(targetText),
            targetViewId = match.node.viewIdResourceName,
            targetClassName = match.node.className,
            targetBounds = targetBounds,
            packageName = snapshot.packageName
        )

        var dispatchAttempt = "ACTION_CLICK"
        var dispatchResult = targetNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true

        // Generic Gesture Fallback: If performAction(ACTION_CLICK) fails or no clickable parent exists, use dispatchGestureTap() at bounds center
        if (!dispatchResult && targetBounds.width > 0 && targetBounds.height > 0) {
            Log.i(TAG, "ACTION_CLICK_FAILED: Attempting bounds-derived gesture tap fallback at (${targetBounds.centerX}, ${targetBounds.centerY})")
            dispatchAttempt = "GESTURE_TAP_FALLBACK"
            dispatchResult = service.dispatchGestureTap(targetBounds.centerX.toFloat(), targetBounds.centerY.toFloat())
        }

        if (!dispatchResult) {
            AutomationOverlayState.updateState(VisualizationActionState.FAILED, targetText = redactSensitiveText(targetText))
            Log.w(TAG, "CLICK_DIAGNOSTIC: package=${snapshot.packageName}, target=${redactSensitiveText(targetText)}, result=DISPATCH_FAILED")
            return ActionResult(status = ActionResultStatus.FAILED, reason = ExecutionReason.UI_NOT_FOUND, message = "DISPATCH_FAILED: Click action and gesture fallback failed for '$targetText'")
        }

        // Post-click Verification
        kotlinx.coroutines.delay(400L)
        val afterRoot = service.getRootNode()
        val afterSnapshot = ActionResolver.captureSnapshot(afterRoot, service.packageName ?: "")
        val afterStateSig = StateSignatureGenerator.generateSignature(afterSnapshot)

        val uiChanged = beforeStateSig != afterStateSig
        val packageChanged = snapshot.packageName != afterSnapshot.packageName
        val targetStillPresent = actionResolver.resolveTargetWithAmbiguity(afterSnapshot, targetText).match != null

        val finalVerification = if (packageChanged || uiChanged) "VERIFIED_SUCCESS" else "DISPATCH_SUCCEEDED_UNVERIFIED"

        Log.i(
            TAG,
            "CLICK_DIAGNOSTIC:\n" +
                    "  package=${snapshot.packageName}\n" +
                    "  target=${redactSensitiveText(targetText)}\n" +
                    "  text=${match.node.text}\n" +
                    "  contentDescription=${match.node.contentDescription}\n" +
                    "  viewId=${match.node.viewIdResourceName}\n" +
                    "  className=${match.node.className}\n" +
                    "  clickable=${match.node.isClickable}\n" +
                    "  enabled=${match.node.isEnabled}\n" +
                    "  visible=${match.node.isVisibleToUser}\n" +
                    "  focusable=${match.node.isFocusable}\n" +
                    "  focused=${match.node.isFocused}\n" +
                    "  bounds=${match.node.boundsInScreen}\n" +
                    "  resolvedBy=${match.matchMethod}\n" +
                    "  candidateCount=${res.candidateCount}\n" +
                    "  dispatchAttempt=ACTION_CLICK\n" +
                    "  dispatchResult=$dispatchResult\n" +
                    "  beforeStateSignature=$beforeStateSig\n" +
                    "  afterStateSignature=$afterStateSig\n" +
                    "  targetStillPresent=$targetStillPresent\n" +
                    "  uiChanged=$uiChanged\n" +
                    "  packageChanged=$packageChanged\n" +
                    "  finalVerification=$finalVerification"
        )

        return ActionResult(
            status = ActionResultStatus.SUCCESS,
            matchedNode = match.node,
            matchMethod = match.matchMethod,
            message = "$finalVerification: Click dispatched (uiChanged=$uiChanged, packageChanged=$packageChanged)"
        )
    }

    private fun performLongClick(targetText: String?, snapshot: UiSnapshot): ActionResult {
        if (targetText.isNullOrBlank()) return ActionResult(status = ActionResultStatus.FAILED, message = "LONG_CLICK requires target text")
        val res = actionResolver.resolveTargetWithAmbiguity(snapshot, targetText)

        if (res.match == null) {
            return ActionResult(status = ActionResultStatus.NOT_FOUND, reason = ExecutionReason.UI_NOT_FOUND, message = "Text '$targetText' not found")
        }

        if (res.isAmbiguous) {
            val redacted = redactSensitiveText(targetText)
            return ActionResult(
                status = ActionResultStatus.BLOCKED,
                reason = ExecutionReason.AMBIGUOUS_TARGET,
                message = "Long click target '$redacted' is ambiguous"
            )
        }

        val match = res.match
        if (!match.node.isEnabled) {
            return ActionResult(status = ActionResultStatus.BLOCKED, reason = ExecutionReason.PRECONDITION_FAILED, message = "Target '$targetText' is disabled")
        }

        val nodeRef = match.node.nodeRef as? AccessibilityNodeInfo
        if (nodeRef != null) {
            if (nodeRef.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
                return ActionResult(status = ActionResultStatus.SUCCESS, matchedNode = match.node, matchMethod = match.matchMethod)
            }
        }
        return ActionResult(status = ActionResultStatus.FAILED, reason = ExecutionReason.UI_NOT_FOUND, message = "Long click failed on target '$targetText'")
    }

    private suspend fun performTypeText(
        targetLabel: String?,
        inputData: String?,
        service: AutomationAccessibilityService,
        snapshot: UiSnapshot
    ): ActionResult {
        val textToType = inputData ?: targetLabel
        if (textToType.isNullOrBlank()) {
            return ActionResult(status = ActionResultStatus.FAILED, message = "TYPE_TEXT requires input text string")
        }

        val redactedText = redactSensitiveText(textToType)

        // Stage 1: Observe & Resolve Editable Target on Fresh Window
        val freshRoot = service.getRootNode()
        var freshSnapshot = if (freshRoot != null) ActionResolver.captureSnapshot(freshRoot, service.packageName ?: "") else snapshot

        var res = actionResolver.resolveEditableTarget(freshSnapshot, targetLabel)
        var match = res.match

        // Generic Search Overlay Auto-Opening Fallback:
        // If no editable field is open, check if target query resolves to a clickable non-editable control (e.g. Search icon/button).
        if (match == null && !targetLabel.isNullOrBlank()) {
            val nonEditableCandidate = actionResolver.resolveTargetWithAmbiguity(freshSnapshot, targetLabel)
            if (nonEditableCandidate.match != null && !nonEditableCandidate.isAmbiguous) {
                val searchBtnNode = nonEditableCandidate.match.node.nodeRef as? AccessibilityNodeInfo
                if (searchBtnNode != null) {
                    Log.i(TAG, "TYPE_TEXT_SEARCH_OVERLAY_FALLBACK: Found non-editable search control '${targetLabel}'. Clicking to open search overlay...")
                    var targetBtn: AccessibilityNodeInfo? = searchBtnNode
                    while (targetBtn != null && !targetBtn.isClickable) {
                        targetBtn = targetBtn.parent
                    }
                    val clicked = targetBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                    if (clicked) {
                        kotlinx.coroutines.delay(400L)
                        val postClickRoot = service.getRootNode()
                        freshSnapshot = ActionResolver.captureSnapshot(postClickRoot, service.packageName ?: "")
                        res = actionResolver.resolveEditableTarget(freshSnapshot, null)
                        match = res.match
                    }
                }
            }
        }

        if (match == null) {
            Log.w(TAG, "TYPE_TEXT_DIAGNOSTIC: Stage 1 failed. No editable field found in package '${freshSnapshot.packageName}' for query '$targetLabel'")
            return ActionResult(status = ActionResultStatus.NOT_FOUND, reason = ExecutionReason.UI_NOT_FOUND, message = "No editable field found for TYPE_TEXT")
        }

        val targetNodeInfo = match.node
        if (!targetNodeInfo.isEnabled) {
            Log.w(TAG, "TYPE_TEXT_DIAGNOSTIC: Stage 2 failed. Editable field is disabled.")
            return ActionResult(status = ActionResultStatus.BLOCKED, reason = ExecutionReason.PRECONDITION_FAILED, message = "Editable input field is disabled")
        }

        val editableNode = targetNodeInfo.nodeRef as? AccessibilityNodeInfo
            ?: return ActionResult(status = ActionResultStatus.FAILED, reason = ExecutionReason.UI_NOT_FOUND, message = "Editable node reference missing")

        // Stage 2: Focus & Click
        var focusDispatched = false
        if (!editableNode.isFocused) {
            focusDispatched = editableNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            editableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            kotlinx.coroutines.delay(200L)
        }

        // Stage 3: Inject Text via ACTION_SET_TEXT
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
        }

        var success = editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        // Stage 4: Post-Injection Observation & Verification
        kotlinx.coroutines.delay(300L)
        val afterRoot = service.getRootNode()
        val afterSnapshot = ActionResolver.captureSnapshot(afterRoot, service.packageName ?: "")

        val normExpected = textToType.trim().lowercase()
        val isVerified = afterSnapshot.editableNodes.any { node ->
            val nodeText = node.text?.trim()?.lowercase() ?: ""
            nodeText.contains(normExpected) || (redactedText == "[REDACTED]" && nodeText.isNotBlank())
        } || afterSnapshot.allNodes.any { node ->
            val nodeText = node.text?.trim()?.lowercase() ?: ""
            nodeText.contains(normExpected)
        }

        // Stage 5: Retry Fallback if unverified
        if (!success || !isVerified) {
            Log.w(TAG, "TYPE_TEXT_RETRY: Initial injection unverified ($success, isVerified=$isVerified). Attempting focused editable retry.")
            val reResolved = actionResolver.resolveEditableTarget(afterSnapshot, null)
            val reEditable = reResolved.match?.node?.nodeRef as? AccessibilityNodeInfo
            if (reEditable != null) {
                reEditable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                success = reEditable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                kotlinx.coroutines.delay(300L)
            }
        }

        val finalRoot = service.getRootNode()
        val finalSnapshot = ActionResolver.captureSnapshot(finalRoot, service.packageName ?: "")
        val finalVerified = finalSnapshot.editableNodes.any { node ->
            val nodeText = node.text?.trim()?.lowercase() ?: ""
            nodeText.contains(normExpected) || (redactedText == "[REDACTED]" && nodeText.isNotBlank())
        }

        val verificationStatusMessage = if (finalVerified) {
            "VERIFIED_SUCCESS: Text '$redactedText' confirmed in editable target"
        } else if (success) {
            "DISPATCH_SUCCEEDED_UNVERIFIED: SET_TEXT returned true for '$redactedText' but text unconfirmed in post-observation"
        } else {
            "DISPATCH_FAILED: SET_TEXT failed for '$redactedText'"
        }

        Log.i(
            TAG,
            "TYPE_TEXT_DIAGNOSTIC:\n" +
                    "  pkg=${freshSnapshot.packageName}\n" +
                    "  targetLabel=${targetLabel ?: "focused"}\n" +
                    "  redactedInput=$redactedText\n" +
                    "  isFocused=${editableNode.isFocused}\n" +
                    "  focusDispatched=$focusDispatched\n" +
                    "  setTextSuccess=$success\n" +
                    "  verified=$finalVerified\n" +
                    "  outcome=$verificationStatusMessage"
        )

        return ActionResult(
            status = if (success || finalVerified) ActionResultStatus.SUCCESS else ActionResultStatus.FAILED,
            matchedNode = targetNodeInfo,
            matchMethod = match.matchMethod,
            message = verificationStatusMessage
        )
    }

    private fun performClearText(targetLabel: String?, snapshot: UiSnapshot): ActionResult {
        val res = actionResolver.resolveEditableTarget(snapshot, targetLabel)
        if (res.match == null) {
            return ActionResult(status = ActionResultStatus.NOT_FOUND, reason = ExecutionReason.UI_NOT_FOUND, message = "No editable field found for CLEAR_TEXT")
        }

        val editableNode = res.match.node.nodeRef as? AccessibilityNodeInfo
            ?: return ActionResult(status = ActionResultStatus.FAILED, reason = ExecutionReason.UI_NOT_FOUND, message = "Editable node reference missing")

        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }

        val success = editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        return if (success) {
            ActionResult(status = ActionResultStatus.SUCCESS, matchedNode = res.match.node, matchMethod = res.match.matchMethod)
        } else {
            ActionResult(status = ActionResultStatus.FAILED, message = "Failed to clear text on editable field")
        }
    }

    private fun formatNodeActions(nodeRef: AccessibilityNodeInfo?): String {
        if (nodeRef == null) return "[]"
        val actions = nodeRef.actionList ?: return "[]"
        return actions.joinToString(", ") { action ->
            when (action.id) {
                AccessibilityNodeInfo.ACTION_FOCUS -> "ACTION_FOCUS"
                AccessibilityNodeInfo.ACTION_CLEAR_FOCUS -> "ACTION_CLEAR_FOCUS"
                AccessibilityNodeInfo.ACTION_SELECT -> "ACTION_SELECT"
                AccessibilityNodeInfo.ACTION_CLEAR_SELECTION -> "ACTION_CLEAR_SELECTION"
                AccessibilityNodeInfo.ACTION_CLICK -> "ACTION_CLICK"
                AccessibilityNodeInfo.ACTION_LONG_CLICK -> "ACTION_LONG_CLICK"
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> "ACTION_ACCESSIBILITY_FOCUS"
                AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> "ACTION_CLEAR_ACCESSIBILITY_FOCUS"
                AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY -> "ACTION_NEXT_AT_MOVEMENT_GRANULARITY"
                AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY -> "ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY"
                AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT -> "ACTION_NEXT_HTML_ELEMENT"
                AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT -> "ACTION_PREVIOUS_HTML_ELEMENT"
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> "ACTION_SCROLL_FORWARD"
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> "ACTION_SCROLL_BACKWARD"
                AccessibilityNodeInfo.ACTION_COPY -> "ACTION_COPY"
                AccessibilityNodeInfo.ACTION_PASTE -> "ACTION_PASTE"
                AccessibilityNodeInfo.ACTION_CUT -> "ACTION_CUT"
                AccessibilityNodeInfo.ACTION_SET_SELECTION -> "ACTION_SET_SELECTION"
                AccessibilityNodeInfo.ACTION_EXPAND -> "ACTION_EXPAND"
                AccessibilityNodeInfo.ACTION_COLLAPSE -> "ACTION_COLLAPSE"
                AccessibilityNodeInfo.ACTION_SET_TEXT -> "ACTION_SET_TEXT"
                else -> action.label?.toString() ?: "ACTION_${action.id}"
            }
        }
    }

    private fun performSubmitInput(service: AutomationAccessibilityService, snapshot: UiSnapshot): ActionResult {
        // Structured, privacy-safe diagnostic logging
        val focusedEditable = snapshot.focusedNodes.firstOrNull { it.isEditable }
            ?: snapshot.editableNodes.firstOrNull()

        if (focusedEditable != null) {
            val nodeRef = focusedEditable.nodeRef as? AccessibilityNodeInfo
            val actionNames = formatNodeActions(nodeRef)
            val isMultiLine = nodeRef?.isMultiLine ?: false
            val maxTextLength = nodeRef?.maxTextLength ?: -1
            val hintText = nodeRef?.hintText?.toString() ?: ""
            val extrasKeys = nodeRef?.extras?.keySet()?.joinToString(", ") ?: "none"

            Log.i(TAG, "SUBMIT_INPUT_TARGET_DIAGNOSTICS: pkg=${snapshot.packageName}, class=${focusedEditable.className}, viewId=${focusedEditable.viewIdResourceName}, isFocused=${focusedEditable.isFocused}, isEditable=${focusedEditable.isEditable}, isClickable=${focusedEditable.isClickable}, isEnabled=${focusedEditable.isEnabled}, isVisible=${focusedEditable.isVisibleToUser}, parentClass=${focusedEditable.parentClassName}, isMultiLine=$isMultiLine, maxTextLength=$maxTextLength, hintTextPresent=${hintText.isNotBlank()}, extrasKeys=[$extrasKeys], actions=[$actionNames]")
        } else {
            Log.w(TAG, "SUBMIT_INPUT_TARGET_DIAGNOSTICS: No focused or editable node found in snapshot pkg=${snapshot.packageName}")
        }

        // 1. Mechanism 1: SEMANTIC SUBMIT CONTROL (Resolve explicit search/submit/enter controls in UI)
        val searchCandidates = listOf("Search", "Go", "Enter", "Submit", "Search or type web address")
        val matchingNodes = mutableListOf<UiNodeInfo>()

        for (btnText in searchCandidates) {
            val searchButtonRes = actionResolver.resolveTargetWithAmbiguity(snapshot, btnText)
            if (searchButtonRes.match != null && !searchButtonRes.match.node.isEditable) {
                matchingNodes.add(searchButtonRes.match.node)
            }
        }

        val distinctMatches = matchingNodes.distinctBy { it.boundsInScreen ?: it.text }
        if (distinctMatches.size > 1) {
            Log.w(TAG, "SUBMIT_INPUT_AMBIGUOUS: Found ${distinctMatches.size} search/submit candidates. Execution blocked for safety.")
            return ActionResult(
                status = ActionResultStatus.BLOCKED,
                reason = ExecutionReason.AMBIGUOUS_TARGET,
                message = "Multiple candidate search/submit controls (${distinctMatches.size}) found on screen"
            )
        }

        if (distinctMatches.size == 1) {
            val best = distinctMatches.first()
            val nodeRef = best.nodeRef as? AccessibilityNodeInfo
            if (nodeRef != null) {
                var targetNode: AccessibilityNodeInfo? = nodeRef
                while (targetNode != null && !targetNode.isClickable) {
                    targetNode = targetNode.parent
                }
                if (targetNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                    Log.i(TAG, "SUBMIT_INPUT_SUBMITTED: Clicked search/submit control '${best.text ?: best.contentDescription}'")
                    return ActionResult(status = ActionResultStatus.SUCCESS, matchedNode = best, matchMethod = "SEMANTIC_SUBMIT_CONTROL")
                }
            }
        }

        // 2. Mechanism 2: IME / Editor Action or Hardware Key Event
        // On unprivileged API 27, hardware key event injection requires INJECT_EVENTS permission (signature/privileged) or UiAutomation shell commands.
        // AccessibilityService on API 27 has no IME dispatch API or InputConnection access.
        Log.w(TAG, "SUBMIT_INPUT_UNSUPPORTED_MECHANISM: No semantic submit control found and direct IME/hardware enter injection is unsupported on unprivileged API 27 for package '${snapshot.packageName}'")
        return ActionResult(
            status = ActionResultStatus.FAILED,
            reason = ExecutionReason.UNSUPPORTED_SUBMISSION_MECHANISM,
            message = "No supported submission mechanism (semantic submit control) available on current API 27 UI"
        )
    }

    private suspend fun performScroll(service: AutomationAccessibilityService, snapshot: UiSnapshot, forward: Boolean, beforeStateSig: String): ActionResult {
        val freshRoot = service.getRootNode()
        val freshSnapshot = if (freshRoot != null) ActionResolver.captureSnapshot(freshRoot, service.packageName ?: "") else snapshot

        val scrollableInfo = freshSnapshot.scrollableNodes.firstOrNull()
        val scrollableNode = scrollableInfo?.nodeRef as? AccessibilityNodeInfo

        var dispatchAttempt = "ACCESSIBILITY_ACTION_SCROLL"
        var scrollDispatched = false

        // Tier 1 & 2: Accessibility Action Scroll
        if (scrollableNode != null) {
            val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            scrollDispatched = scrollableNode.performAction(action)
        }

        // Tier 3: Dynamic Gesture Swipe Fallback
        if (!scrollDispatched) {
            dispatchAttempt = "DYNAMIC_GESTURE_SWIPE_FALLBACK"
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels.toFloat()
            val screenHeight = displayMetrics.heightPixels.toFloat()

            val startX = screenWidth / 2f
            val (startY, endY) = if (forward) {
                Pair(screenHeight * 0.75f, screenHeight * 0.25f) // Swipe UP to scroll DOWN
            } else {
                Pair(screenHeight * 0.25f, screenHeight * 0.75f) // Swipe DOWN to scroll UP
            }

            Log.i(TAG, "SCROLL_GESTURE_FALLBACK: Attempting gesture swipe ($startX, $startY) -> ($startX, $endY)")
            scrollDispatched = AndroidAutomationCompat.dispatchSwipe(service, startX, startY, startX, endY)
        }

        if (!scrollDispatched) {
            Log.w(TAG, "SCROLL_FAILED: Both accessibility scroll and gesture swipe fallback failed.")
            return ActionResult(status = ActionResultStatus.FAILED, reason = ExecutionReason.UI_NOT_FOUND, message = "No scrollable container or gesture dispatch path available")
        }

        // Post-Scroll Verification: Observe UI state change
        kotlinx.coroutines.delay(500L)
        val afterRoot = service.getRootNode()
        val afterSnapshot = ActionResolver.captureSnapshot(afterRoot, service.packageName ?: "")
        val afterStateSig = StateSignatureGenerator.generateSignature(afterSnapshot)

        val visibleTextChanged = (freshSnapshot.visibleTexts.toSet() != afterSnapshot.visibleTexts.toSet())
        val stateSigChanged = (beforeStateSig != afterStateSig)
        val isVerified = stateSigChanged || visibleTextChanged

        val verificationMessage = if (isVerified) {
            "VERIFIED_SUCCESS: Scroll executed ($dispatchAttempt) and UI state changed"
        } else {
            "DISPATCH_SUCCEEDED_UNVERIFIED: Scroll dispatched ($dispatchAttempt) but no UI state change detected"
        }

        Log.i(TAG, "SCROLL_DIAGNOSTIC: $verificationMessage (stateSigChanged=$stateSigChanged, textChanged=$visibleTextChanged)")

        return ActionResult(
            status = if (isVerified) ActionResultStatus.SUCCESS else ActionResultStatus.FAILED,
            reason = if (isVerified) ExecutionReason.NONE else ExecutionReason.STUCK,
            message = verificationMessage
        )
    }

    private fun performGoBack(service: AutomationAccessibilityService): ActionResult {
        return if (AndroidAutomationCompat.performGlobalBack(service)) ActionResult(status = ActionResultStatus.SUCCESS)
        else ActionResult(status = ActionResultStatus.FAILED, message = "GO_BACK failed")
    }

    private fun performGlobalHome(service: AutomationAccessibilityService): ActionResult {
        return if (AndroidAutomationCompat.performGlobalHome(service)) ActionResult(status = ActionResultStatus.SUCCESS, message = "Global action HOME executed")
        else ActionResult(status = ActionResultStatus.FAILED, message = "Global action HOME failed")
    }

    private fun performGlobalRecents(service: AutomationAccessibilityService): ActionResult {
        return if (AndroidAutomationCompat.performGlobalRecents(service)) ActionResult(status = ActionResultStatus.SUCCESS, message = "Global action RECENTS executed")
        else ActionResult(status = ActionResultStatus.FAILED, message = "Global action RECENTS failed")
    }

    private suspend fun performCaptureScreen(
        service: AutomationAccessibilityService,
        screenProvider: ObservationProvider?
    ): ActionResult {
        return AndroidAutomationCompat.captureScreenshotCompat(service, context, screenProvider)
    }
}

data class PreconditionCheckResult(
    val success: Boolean,
    val failureReason: String? = null
)
