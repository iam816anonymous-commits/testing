# PHYSICAL EXECUTION AUDIT & CALL GRAPH

## 1. CALL GRAPH TRACE

```
User Command (AgentCommandScreen)
  ↓
AgentRuntimeManager.startOrResumeTaskSession(taskDescription)
  ↓
AgentSessionRecord (Inserted/Updated in Room DB)
  ↓
AgentCore.executeTaskStep(taskDescription)
  ↓
[OBSERVE BEFORE] AccessibilityObservationProvider.captureObservation() → UiSnapshot
  ↓
TaskResolver.resolveTask(taskDescription, snapshot)
  ↓
GoalModel.parse(taskDescription) → GoalModel (Decomposed Subgoals)
  ↓
DeviceActionExecutor.executeAndAudit(...)
  ↓
ActionResolver.resolveTargetWithAmbiguity(snapshot, target)
  ↓
AutomationAccessibilityService.performGlobalAction / performAction / dispatchGestureTap
  ↓
[WAIT] WaitEngine.waitUntil(...)
  ↓
[OBSERVE AFTER] Fresh AccessibilityObservationProvider.captureObservation()
  ↓
GoalVerifier.verifyTaskGoal(taskDescription, postObs.snapshot, result)
  ↓
GoalResult.CONFIRMED → AgentState.COMPLETED
```

## 2. TEST CLASSIFICATIONS

- **CODE TEST:** Unit test in JVM or Robolectric verifying Kotlin class methods in isolation.
- **SIMULATION TEST:** Synthetic snapshot testing using mock UI node trees.
- **DEVICE-SERVICE TEST:** Android AccessibilityService API invocation test confirming service response.
- **PHYSICAL UI TEST:** Real user command entered through `AgentCommandScreen` on physical TECNO IN6 / Android 8.1 hardware resulting in visible screen state changes confirmed by fresh post-action UI observation.

## 3. COMPLETION INVARIANTS

`AgentState.COMPLETED` strictly requires:
1. Fresh post-action UI snapshot capture.
2. `GoalVerifier.verifyTaskGoal(...)` returning `GoalResult.CONFIRMED`.
3. Absence of same-action, same-state, or same-target execution loops.
