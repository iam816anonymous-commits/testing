# CreatorAutomation — Automation Foundation Baseline (Layer 0)

## Overview

This document establishes the Layer 0 baseline for the **CreatorAutomation** project on the target hardware. It outlines the codebase architecture, recovered automation components, single production execution path, verified capabilities, known physical failures/limitations, and deferred items.

---

## Target Device Specification

* **Device Hardware**: TECNO IN6
* **OS Version**: Android 8.1.0
* **API Level**: 27
* **RAM / Storage**: ~4 GB RAM / ~64 GB Storage
* **Execution Environment**: Credential-free, low-RAM, native Kotlin accessibility automation engine targeting Android API 27+ without root, ADB, or external cloud server dependencies.

---

## Single Production Execution Path

There is **one unified production execution path** in CreatorAutomation:

```text
USER INTENT / TASK
       ↓
TaskResolver / TaskReasoner
       ↓
AgentCore (Observe → Understand → Resolve → Plan → Act → Verify)
       ↓
ActionResolver (Target Candidate Ranking & Ambiguity Check)
       ↓
DeviceActionExecutor (Accessibility Node Action / Fallback Actuator)
       ↓
AutomationAccessibilityService (Android Accessibility API 27)
       ↓
WaitEngine (Condition Waiting & Timeout Diagnostics)
       ↓
GoalVerifier (Fresh Observation Physical Truth Verification)
       ↓
RecoveryManager (Bounded Retry & State Classification)
       ↓
AgentState & Room Session Persistence (AgentRuntimeManager)
```

No parallel click engines, scroll engines, or agent loops exist in the production runtime.

---

## Recovered & Existing Automation Components

1. **Accessibility Connection & Perception (`AutomationAccessibilityService`, `ObservationProvider`, `ScreenObservationProvider`)**
   - Active window retrieval via `getRootInActiveWindow()`.
   - On-demand UI tree snapshot generation (`UiSnapshot`, `UiNodeInfo`).
   - Fallback MediaProjection screen observation for visual hashing on API 27.

2. **Target Discovery & Resolution (`ActionResolver`)**
   - Deterministic 4-tier candidate resolution cascade:
     - Tier 1: View ID (`1.0`)
     - Tier 2: Exact Text (`0.95`)
     - Tier 3: Content Description (`0.90`)
     - Tier 4: Partial Text (`0.75`)
   - Strict ambiguity detection: when multiple candidates score equally (`candidateCount > 1`), status is classified as `AMBIGUOUS` and execution is safely blocked.

3. **Physical Action Execution (`DeviceActionExecutor`)**
   - Primary: Accessibility node actions (`ACTION_CLICK`, `ACTION_FOCUS`, `ACTION_SET_TEXT`, `ACTION_SCROLL_FORWARD`, `ACTION_SCROLL_BACKWARD`, `performGlobalAction`).
   - Generic 5-tier submission hierarchy: IME/editor action → focused node action → semantic submit control → gesture tap fallback.
   - Target bounds-derived `dispatchGesture()` fallback when node actions fail.

4. **Condition Waiting & Diagnostics (`WaitEngine`)**
   - Condition-based waiting for state changes (`WAIT_FOR_PACKAGE`, `WAIT_FOR_TEXT`, `WAIT_FOR_VIEW_ID`, `WAIT_FOR_STATE_SIGNATURE`).
   - Hardened validation: immediate `INVALID_WAIT_CONDITION` failure on null or blank expected values, avoiding 10-second timeout hangs.

5. **Goal Verification (`GoalVerifier`)**
   - Strict separation of `ActionResult` (dispatch result) from `GoalResult` (`CONFIRMED`, `NOT_CONFIRMED`, `VERIFICATION_UNAVAILABLE`, `FAILED`).
   - Mandates fresh post-action UI observation before confirming goal completion.

6. **Safety Interlock & Risk Engine (`AutonomousExecutionGate`, `GoalModel`)**
   - Reversible interaction vs. destructive/consequential operation classification.
   - Side-effect protection requiring explicit user confirmation for high-risk actions.

7. **Hardware Actuators (`HardwareActuator`, `FlashlightActuator`, `HapticActuator`, `AudioActuator`, `DisplayActuator`)**
   - Flashlight actuator using native `CameraManager.setTorchMode` and `TorchCallback` registered on the Main Looper (API 27 compatible).

8. **Runtime Session Management & Recovery (`AgentCore`, `AgentRuntimeManager`, `RecoveryManager`)**
   - Room DB persistence (`AppDatabase` v8, `AgentSessionRecord`, `ActionAuditRecord`).
   - Task pause, cancel, and auto-resumption from `PAUSED`/`CANCELLED` to `IDLE` upon new task submission.

---

## Known Physical Failures & Environment Constraints

Based on physical validation records (`physical_validation/2026-09-11/`) and headless evaluation:

1. **Accessibility Service Disconnected in Headless Environment**
   - Physical interaction tests (GO_HOME, GO_BACK, CHROME_TYPE_TEXT, YOUTUBE_SEARCH_SUBMIT, GENERIC_SCROLL) report `BLOCKED` with `Accessibility Active: false` when run in headless test environments without an active AccessibilityService connection on device.
2. **Package Manager Launcher Discovery in Mock Constraints**
   - Home screen launcher app discovery tests return 0 packages when mock environment package manager query limits are active without full launcher intent permissions.
3. **Safety Interlock Gating (`TEST-SAF-001`)**
   - Side-effect operations correctly enter `BLOCKED` state until explicit user confirmation is supplied.

---

## Verified Capabilities (Unit Test Suite)

All **164 unit tests** across the codebase pass 100% cleanly (`./gradlew test`):
* Target candidate scoring & ambiguity blocking (`ActionResolverTest`, `SubmitMechanismTest`).
* Generic text typing, focus, and scrolling pipelines (`GenericInputScrollingTest`).
* API 27 compatibility layer & global navigation (`AndroidAutomationCompatTest`).
* Hardware actuator TorchCallback registration (`HardwareActuatorTest`).
* Wait condition validation & timeout diagnostics (`WaitEngineTest`).
* Session cancellation & state machine resumption (`AgentRuntimeManagerTest`).
* Interactive discovery & transition logging (`InteractionDiscoveryTest`).
* Physical execution stabilization & recovery boundaries (`PhysicalExecutionStabilizationTest`, `RecoveryAndControlPathAuditTest`).

---

## Layer Progression Strategy

In accordance with CreatorAutomation non-negotiable principles:
1. **Layer 0 (Current)**: Project baseline established, unit tests passing.
2. **Layer 1**: Accessibility Service Connection physical verification gate on TECNO IN6 (`SERVICE_CONNECTED`, `EVENTS_RECEIVED`, `ROOT_WINDOW_AVAILABLE`).
3. **Layer 2**: Screen Observation (`UiSnapshot` validation).
4. **Layer 3**: Target Discovery & Candidate Ranking.
5. **Layer 4**: Touch / Click (`ACTION_CLICK` + Gesture Fallback).
6. **Layer 5–15**: Incremental primitive action, verification, wait, trace, safety, and composed task gates.
7. **Layer 16**: Generic App Interaction Discovery (Deferred until Layers 1–15 are physically confirmed).
