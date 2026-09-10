# AUTOMATION PRIMITIVE OPERATIONAL CONTRACT

**Date:** March 2025
**Target:** CreatorAutomation Android Project (`com.creator.automation`)
**Specification Version:** 2.1.8+

---

## 1. PURPOSE

This document specifies the exact behavioral contract for generic Android automation primitives in `CreatorAutomation`. Every primitive must obey these rules across all Android applications without app-specific hardcoding.

---

## 2. PRIMITIVE OPERATIONAL SPECIFICATION

### 2.1 `LAUNCH_APP(appQuery: String)`
* **Precondition:** App query string must resolve to an installed package via `AppResolver`.
* **Behavior:** Launches package using `PackageManager.getLaunchIntentForPackage()`.
* **Status Returns:** `SUCCESS`, `BLOCKED` (`APP_NOT_INSTALLED` or `AMBIGUOUS_APPLICATION`), or `FAILED`.

### 2.2 `OBSERVE` / `READ_VISIBLE_UI`
* **Precondition:** `AutomationAccessibilityService.instance` must be connected.
* **Behavior:** Traverses active window `AccessibilityNodeInfo` tree and generates a `UiSnapshot`.
* **Diagnostic Log:** Produces `UI_SNAPSHOT_CREATED` with package name, total nodes, visible texts, view IDs, clickable nodes, and editable nodes.

### 2.3 `CLICK_TEXT(targetText: String)`
* **Precondition:** `targetText` must resolve to a non-disabled node (`TargetResolutionStatus.FOUND_UNIQUE`).
* **Ambiguity Safety:** If `candidateCount > 1`, execution is **strictly blocked** (`ExecutionReason.AMBIGUOUS_TARGET`).
* **Parent Climbing:** If matched node is not clickable, climbs parent ancestors until a clickable node is found.
* **Dispatch:** Calls `AccessibilityNodeInfo.performAction(ACTION_CLICK)`.
* **Post-Action Verification:** Re-observes UI state after 400ms delay. Compares `beforeStateSignature` vs `afterStateSignature`.
* **Verification Status:**
  * `VERIFIED_SUCCESS`: UI state or active package changed.
  * `DISPATCH_SUCCEEDED_UNVERIFIED`: Action dispatched, but no state change detected.
  * `FAILED`: Target not found, disabled, or click failed.

### 2.4 `TYPE_TEXT(targetLabel: String, inputData: String)`
* **Precondition:** Editable field must exist on current screen.
* **Behavior:** Focuses field if unfocused, then dispatches `AccessibilityNodeInfo.ACTION_SET_TEXT`.
* **Post-Action Verification:** Re-observes UI state after 300ms delay and confirms that `editableNode.text` contains `inputData`.
* **Redaction:** Sensitive inputs (passwords, PINs, secrets, tokens) are redacted (`[REDACTED]`) in all audit logs.

### 2.5 `SCROLL(forward: Boolean)`
* **Precondition:** At least one scrollable container must be present in snapshot (`snapshot.scrollableNodes.isNotEmpty()`).
* **Behavior:** Dispatches `ACTION_SCROLL_FORWARD` or `ACTION_SCROLL_BACKWARD`.
* **Post-Action Verification:** Re-observes UI state after 500ms delay. If `beforeStateSignature == afterStateSignature`, returns `FAILED` with `ExecutionReason.STUCK` (`NO_PROGRESS`).

### 2.6 `SUBMIT_INPUT` / `PRESS_ENTER`
* **Behavior:** Resolves visible search/submit controls ("Search", "Go", "Enter", "Submit") in UI snapshot and dispatches `ACTION_CLICK`.
* **Ambiguity Safety:** If candidate submit controls > 1, returns `AMBIGUOUS_TARGET`.
* **API 27 Capability Limitation:** If no semantic submit control is present on screen, returns `FAILED` with `ExecutionReason.UNSUPPORTED_SUBMISSION_MECHANISM`.

---

## 3. AUDIT & DIAGNOSTIC CONTRACT

Every primitive execution generates a structured `ActionAuditRecord` saved in Room DB:
* `timestamp`
* `activePackage`
* `beforeStateSignature`
* `actionType`
* `targetIdentifier` (redacted)
* `dispatchResult`
* `afterStateSignature`
* `stateChangeResult`
* `verificationStatus` (`VERIFIED_SUCCESS`, `DISPATCH_SUEDED_UNVERIFIED`, `FAILED`)
* `durationMs`
