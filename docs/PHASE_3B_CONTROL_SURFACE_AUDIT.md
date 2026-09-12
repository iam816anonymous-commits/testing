# PHASE 3B CONTROL SURFACE AUDIT & RESTORATION PLAN

## 1. Executive Summary

During Phase 3B architectural implementation, extensive general-purpose automation capabilities (Hardware Actuators, Generic Submit Pipeline, `ApplicationWorldState`, `ActionGraph`, API 27 `TYPE_ACCESSIBILITY_OVERLAY`, and `GoalVerifier.verifyHardwareGoal`) were created and tested in unit tests. However, the interactive user control surface on `MainActivity.kt` was temporarily showing diagnostic-only tabs ("Overview", "Tests", "Sensors", "Actuators") without an integrated operator control surface or natural-language command input field.

This audit documents the current state of production automation components, verifies their linkage, and outlines the restoration of a unified **Agent Control Workbench** on `MainActivity.kt`.

---

## 2. Component Call Graph Audit

| Component | Instantiation / Lifecycle | Caller | Inputs | Outputs | Verification Method |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`AgentRuntimeManager`** | Singleton / Context-bound in `MainActivity` | UI / Operator Workbench | `taskDescription: String`, `globalAutonomousEnabled` | `AgentStepResult`, `AgentSessionRecord` | Room DB `AgentSessionRecord` checkpoint |
| **`AgentCore`** | Member of `AgentRuntimeManager` | `AgentRuntimeManager.startOrResumeTaskSession` | `taskDescription`, `trigger` | `AgentStepResult` | `GoalVerifier.verifyGoal` / `verifyHardwareGoal` |
| **`TaskResolver`** | Created in `WorkflowEngine` | `WorkflowEngine.resolveAndExecuteTask` | `taskDescription: String`, `UiSnapshot` | `TaskResolution` (`localWorkflow`, `reasoningPlan`) | `WorkflowStep` validation |
| **`DeviceActionExecutor`** | Member of `WorkflowEngine` | `WorkflowEngine.executeActionStep` | `workflowId`, `AutomationAction`, `AutomationAccessibilityService` | `ActionResult` | Post-action state signature comparison |
| **`HardwareActuatorRegistry`** | Static registry in `HardwareActuator.kt` | `TaskResolver` & `DeviceActionExecutor` | `taskDescription` / `HardwareCommand` | `HardwareActionResult` | Native API state observation (`TorchCallback`, etc.) |
| **`GoalVerifier`** | Instantiated in `AgentCore` | `AgentCore.executeTaskStep` | `taskDescription`, `context`, `UiSnapshot` | `GoalVerificationResult` | Physical hardware/UI state confirmation |
| **`AgentOverlayController`** | Managed by `AutomationAccessibilityService` | `AutomationOverlayState` collector | `AutomationVisualizationState` | Floating `TYPE_ACCESSIBILITY_OVERLAY` | Main UI thread WindowManager view rendering |

---

## 3. Disconnected vs. Restored Components

1. **Natural Language Command Input**:
   - **Status**: Disconnected from `MainActivity.kt`.
   - **Restoration**: Added prominent text input field with **RUN** and **STOP** buttons on the primary screen connected directly to `AgentRuntimeManager.startOrResumeTaskSession` and `AgentRuntimeManager.cancelActiveSession`.

2. **Live Execution Status Panel**:
   - **Status**: Partially hidden in logs tab.
   - **Restoration**: Prominently displayed at the top of the command center showing live `AgentState`, current goal, observation summary, target bounds, candidate count, execution mechanism, wait state, and specific pause/blocked/failure reasons.

3. **Quick Navigation & Perception Controls**:
   - **Status**: Unavailable on main screen.
   - **Restoration**: Added quick action buttons (**HOME**, **BACK**, **RECENTS**, **READ SCREEN**, **SCROLL DOWN**, **SCROLL UP**, **REFRESH OBSERVATION**) that route directly through `AgentRuntimeManager.startOrResumeTaskSession`. Note: **READ SCREEN** is observation-only and returns `OBSERVATION_AVAILABLE`.

4. **Direct Hardware Controls**:
   - **Status**: Information rendered in static table only.
   - **Restoration**: Added interactive buttons (**FLASHLIGHT ON**, **FLASHLIGHT OFF**, **HAPTIC TEST**, **AUDIO TEST**) routing to production `HardwareActuatorRegistry` via `AgentRuntimeManager`.

5. **Dynamic App Launcher**:
   - **Status**: Missing.
   - **Restoration**: Added dropdown launcher populated with dynamically discovered launchable applications via `AppResolver`.

6. **Startup Permissions Interlock**:
   - **Status**: On-demand only.
   - **Restoration**: Automatically request runtime permissions (Camera, Vibrator, MediaProjection authorization, and Accessibility Settings shortcut) on activity launch.

---

## 4. Production Architecture Flow

```
USER COMMAND (Natural Language or Quick Button)
       │
       ▼
AgentRuntimeManager.startOrResumeTaskSession()
       │
       ▼
AgentCore.executeTaskStep()
       │
       ├─► 1. OBSERVING: Capture Multi-Source Observation (Accessibility -> Screen -> Camera)
       │                 Build ApplicationWorldState & ActionGraph
       │                 Update AutomationOverlayState (OBSERVING)
       │
       ├─► 2. RESOLVING: TaskResolver.resolveTask()
       │                 Check HardwareActuatorRegistry
       │                 Update AutomationOverlayState (TARGET_FOUND)
       │
       ├─► 3. EXECUTING: WorkflowEngine -> DeviceActionExecutor
       │                 Perform Click / Type / Submit / Toggle Hardware
       │                 Update AutomationOverlayState (CLICKING / TYPING with TargetBounds)
       │
       ├─► 4. VERIFYING: GoalVerifier.verifyGoal() / verifyHardwareGoal()
       │                 Update AutomationOverlayState (VERIFYING -> SUCCESS / FAILED)
       │
       └─► 5. COMPLETED: Checkpoint AgentSessionRecord in Room DB
                         Auto-hide overlay post-completion
```

No secondary execution engines, app-specific scripts, or mock shortcuts are used. All UI controls invoke the same production pipeline.
