# Automation Engine 2.0 Architectural Specification

## Overview

Automation Engine 2.0 transitions CreatorAutomation from fragile fixed-delay automation to a deterministic, state-driven, resilient automation runtime on Android. It addresses non-deterministic network delays, UI animation timing, target ambiguity, stuck loops, and multi-step verification.

---

## Key Components

### 1. Bounded Wait Engine (`WaitEngine.kt`)
Replaces fixed `Thread.sleep()` with condition-driven polling using exponential/linear backoff.

Supported Wait Conditions:
- `WAIT_FOR_TEXT`: Polls until specific text appears in the snapshot tree.
- `WAIT_FOR_VIEW_ID`: Polls until a view ID is present.
- `WAIT_FOR_PACKAGE`: Polls until the foreground application package matches.
- `WAIT_FOR_STATE_SIGNATURE`: Polls until the dynamic state signature matches expected.
- `WAIT_FOR_STATE_CHANGE`: Polls until the state signature differs from an initial signature.
- `WAIT_FOR_VISUAL_CHANGE`: Polls until visual luminance grid perception indicates change.

---

### 2. Action Preconditions & Action Semantics (`AutomationModels.kt`)
Every action declares preconditions evaluated prior to execution:
- `PACKAGE_MATCH`: Foreground package must match expected.
- `TEXT_PRESENT`: Target text must exist in snapshot tree.
- `VIEW_ID_PRESENT`: Target view ID must exist in snapshot tree.
- `EDITABLE_PRESENT`: An editable text field must be present.
- `SCROLLABLE_PRESENT`: A scrollable container must be present.
- `AUTH_AUTHENTICATED`: User must be authenticated (`AuthState != LOGIN_REQUIRED`).

Every action declares semantic safety guarantees:
- `READ_ONLY`: Pure observation, safe to retry endlessly.
- `IDEMPOTENT`: Repeating produces identical state.
- `REPEATABLE`: Safe to repeat up to step limit.
- `NON_IDEMPOTENT`: State-altering (e.g. submit button); must not re-execute blindly.
- `HIGH_RISK`: Requires elevated confirmation or user intervention.

---

### 3. Target Resolution & Ambiguity Detection (`ActionResolver.kt`)
When matching UI elements by text, content description, or view ID, `resolveTargetWithAmbiguity` returns a `TargetResolutionResult`:
- `candidateCount`: Number of matching UI nodes.
- `isAmbiguous`: Set to `true` when multiple matching nodes are found.
- If ambiguous, action execution logs a warning or pauses with `AMBIGUOUS_TARGET` to prevent accidental clicks.

---

### 4. Deterministic Branching Engine (`BranchingEngine.kt`)
Enables non-linear workflow step execution based on runtime conditions (`IF`, `ELSE_IF`, `ELSE` jumps):
- `IF_TEXT_PRESENT`: Jump to target step index if text exists.
- `IF_PACKAGE_MATCH`: Jump if foreground package matches.
- `IF_STATE_SIGNATURE_MATCH`: Jump if state signature matches.
- `IF_AUTH_STATE_MATCH`: Jump if auth state matches.

---

### 5. Goal & Multi-Step Verifier (`GoalVerifier.kt`)
Distinguishes single-action verification from multi-step task/goal completion:
- Evaluates target state signatures, text presence, view ID presence, and auth state.
- Returns `GoalVerificationStatus` (`GOAL_MET`, `GOAL_NOT_MET`, `GOAL_BLOCKED`, `PARTIAL_PROGRESS`).

---

### 6. Sliding-Window Stuck Detector (`StuckDetector.kt`)
Monitors the execution history buffer (default size: 10) to catch infinite loops and non-responsive states:
- `REPEATED_STATE_NO_PROGRESS`: Detects 3+ consecutive identical state signatures without progress.
- `REPEATED_ACTION_LOOP`: Detects alternating A $\rightarrow$ B $\rightarrow$ A $\rightarrow$ B action cycles.
- `MAX_STEPS_EXCEEDED`: Hard step budget enforcement.

---

### 7. Multi-Level Recovery & User Intervention (`RecoveryManager.kt` & `AgentModels.kt`)
Categorizes failures into structured recovery levels:
1. `LOCAL_REPAIR` $\rightarrow$ Immediate step retry.
2. `STRATEGY_REPAIR` $\rightarrow$ Pause for strategy re-evaluation / fallback.
3. `USER_INTERVENTION` $\rightarrow$ Transition agent to `NEEDS_USER_INPUT` state.
4. `FAILURE` $\rightarrow$ Mark task failed.

`NEEDS_USER_INPUT` prompts the user via Compose UI to resolve logins, CAPTCHAs, permissions, or ambiguity before resuming execution safely.
