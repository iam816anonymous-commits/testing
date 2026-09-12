# PHASE 3E.7 RUNTIME MECHANISM AUDIT

**Target Baseline:** TECNO IN6 (Android 8.1 / API 27, ~4 GB RAM, ~64 GB Storage)
**Execution Contract:** Generic, App-Agnostic, Accessibility-First, Low-RAM, Zero LLM/VLM Dependency

---

## 1. COMPONENT CALL GRAPH & RUNTIME EXECUTION PATH

```
USER TASK COMMAND
       ↓
AgentCommandScreen / Workbench (MainActivity.kt)
       ↓
AgentRuntimeManager.startOrResumeTaskSession(task)
       ↓
AgentCore.executeTaskStep(taskDescription)
       ↓
ObservationProvider (AccessibilityObservationProvider / ScreenObservationProvider / CameraObservationProvider)
       ↓
TaskResolver.resolveTask(taskDescription, currentSnapshot)
       ↓
WorkflowEngine.resolveAndExecuteTask(...)
       ↓
DeviceActionExecutor.executeAndAudit(...)
       ↓
AccessibilityService / Gesture / HardwareActuator / MediaProjection
       ↓
WaitEngine.waitUntil(...)
       ↓
GoalVerifier.verifyGoal(...) / verifyHardwareGoal(...)
       ↓
TransitionObservationEngine.observeTransition(...)
       ↓
AgentState Transition (COMPLETED / PAUSED / RECOVERING / FAILED)
```

---

## 2. DETAILED MECHANISM MAP

### A. Touch / Click
- **Primary Mechanism**: `AccessibilityNodeInfo.performAction(ACTION_CLICK)`. Traverses up parent hierarchy until `isClickable == true`.
- **Fallback Mechanism**: `dispatchGestureTap(centerX, centerY)` on target bounds `TargetBounds(left, top, right, bottom)`.
- **Visual Fallback**: `ActionResolver.resolveFusedTarget` using `PerceptionFusionEngine` fused elements when accessibility nodes are missing.
- **Verification**: Re-observes active window post-click; verifies `beforeStateSig != afterStateSig` or package transition.

### B. Scrolling
- **Primary Mechanism**: `AccessibilityNodeInfo.performAction(ACTION_SCROLL_FORWARD)` (down) or `ACTION_SCROLL_BACKWARD` (up) on `snapshot.scrollableNodes.firstOrNull()`.
- **Fallback Mechanism**: `GestureDescription.StrokeDescription` swipe stroke calculated from container bounds (`startY = height * 0.7f` to `endY = height * 0.3f`).
- **Verification**: Re-observes active window post-scroll; fails with `ExecutionReason.STUCK` if `beforeStateSig == afterStateSig`.

### C. Text Input / Typing
- **Primary Mechanism**: Resolves target field via `ActionResolver.resolveEditableTarget`. Focuses field via `ACTION_FOCUS`, then injects text via `ACTION_SET_TEXT`.
- **Query Cleaning**: `TaskResolver.generateGenericWorkflowForTask` strips filler prefixes ("search for", "about", "on", "in") to isolate intended search query.
- **Verification**: Re-observes active window after 300ms delay and confirms that an editable node contains the typed input string.

### D. Search / Submit
- **Mechanism 1**: Click container search button (e.g. magnifying glass button in input bar).
- **Mechanism 2**: Click semantic submit control ("Search", "Go", "Enter", "Submit").
- **Verification**: Re-observes UI transition post-submit.

### E. Navigation Controls
- **HOME**: `performGlobalAction(GLOBAL_ACTION_HOME)`. Verification checks active launcher package.
- **BACK**: `performGlobalAction(GLOBAL_ACTION_BACK)`. Verification checks UI state signature transition.
- **RECENTS**: `performGlobalAction(GLOBAL_ACTION_RECENTS)`. Verification checks overview UI transition.

### F. Hardware Control
- **Flashlight**: Native `CameraManager.setTorchMode` with `TorchCallback` registered on Main Looper for state verification.
- **Haptics**: `Vibrator` service actuation.
- **Audio**: `AudioManager.ringerMode` mute/unmute control.
- **Display**: `PowerManager.isInteractive` wake check.
