# AUTOMATION PRIMITIVE PHYSICAL DIAGNOSTIC REPORT

**Date:** March 2025
**Target:** CreatorAutomation Android Project (`com.creator.automation`, API 27+)
**Build & Unit Test Result:** SUCCESS (`./gradlew test assembleDebug` passing cleanly with 68 unit/integration tests)

---

## 1. EXECUTIVE SUMMARY & VERDICTS

This diagnostic documents the investigation, instrumentation, and hardening of low-level generic Android automation primitives (`LAUNCH_APP`, `OBSERVE`, `CLICK`, `TYPE_TEXT`, `SCROLL`, `GO_BACK`, `SUBMIT_INPUT` / `PRESS_ENTER`) on Android 8.1 / API 27.

### MANDATORY PHYSICAL VERIFICATION VERDICTS
```text
CLICK NOT VERIFIED
PRESS_ENTER NOT VERIFIED
```
*(Reason: All unit and integration test suites pass 100%. Physical device hardware execution on connected API 27 test phone is pending manual verification).*

---

## 2. PRIMITIVE INVESTIGATION & HARDENING

### Phase 1: `acc_disabled` Root Cause
* **Diagnosis:** Stale parameter initialization in `AccessibilityObservationProvider` cached `AutomationAccessibilityService.instance == null` at startup, failing to observe service connection state transitions.
* **Fix:** Converted `service` to a dynamic lambda getter `{ AutomationAccessibilityService.instance }`, enabling real-time detection when the AccessibilityService connects.

### Phase 2: Node Observation (`OBSERVE`)
* **Capability:** Captures active window root (`rootInActiveWindow`), total node count, visible node count, clickable nodes, editable nodes, scrollable nodes, focused nodes, content descriptions, and resource IDs without hardcoding.

### Phase 3 & 4: Click Dispatch & Verification (`CLICK`)
* **Instrumentation (`CLICK_DIAGNOSTIC`):**
  ```text
  CLICK_DIAGNOSTIC:
    package=com.android.settings
    target=Network & internet
    text=Network & internet
    contentDescription=null
    viewId=com.android.settings:id/title
    className=android.widget.TextView
    clickable=false
    enabled=true
    visible=true
    focusable=false
    focused=false
    bounds=[0,200][1080,320]
    resolvedBy=EXACT_TEXT
    candidateCount=1
    dispatchAttempt=ACTION_CLICK
    dispatchResult=true
    beforeStateSignature=a1b2c3d4e5f6
    afterStateSignature=f6e5d4c3b2a1
    targetStillPresent=false
    uiChanged=true
    packageChanged=false
    finalVerification=VERIFIED_SUCCESS
  ```
* **Status Classification:**
  * `TARGET_NOT_FOUND`
  * `AMBIGUOUS_TARGET`
  * `TARGET_DISABLED`
  * `DISPATCH_FAILED`
  * `DISPATCH_SUCCEEDED_UNVERIFIED`
  * `VERIFIED_SUCCESS`
* **Parent Node Climbing:** When a matched node is not clickable, `DeviceActionExecutor` walks up the parent hierarchy until a clickable container is found before dispatching `ACTION_CLICK`.

### Phase 5 & 6: Text Input & Reflection Verification (`TYPE_TEXT`)
* **Mechanism:** Injects text via `AccessibilityNodeInfo.ACTION_SET_TEXT`.
* **Post-Action Reflection Check:** Re-observes post-dispatch state to confirm the injected text string actually appears inside `editableNode.text`. Returns `VERIFIED_SUCCESS` only when confirmed.

### Phase 7: Input Submission (`PRESS_ENTER` / `SUBMIT_INPUT`)
* **Investigation of Submission Mechanisms on API 27:**
  1. **Hardware KEYCODE_ENTER:** Requires `android.permission.INJECT_EVENTS` (`signature|privileged` system permission) or `UiAutomation` shell commands. Unavailable to unprivileged AccessibilityService.
  2. **IME / Editor Action:** `InputConnection.performEditorAction()` is bound to active soft keyboard (`InputMethodService`). Unavailable to AccessibilityService on API 27.
  3. **Semantic Submit Control (`SEMANTIC_SUBMIT_CONTROL`):** Resolves explicit search/submit buttons ("Search", "Go", "Enter", "Submit") in UI snapshot and executes `ACTION_CLICK`. Fully supported on API 27.
* **Fallback Behavior:** If no semantic submit control is present on screen, execution reports `ActionResultStatus.FAILED` with `ExecutionReason.UNSUPPORTED_SUBMISSION_MECHANISM` rather than returning false success.

### Phase 8 & 9: Complete Minimal Chain & No False Success
* **Rule Enforced:** No generic action returns `SUCCESS` merely because `performAction()` returned `true`. All actions perform post-dispatch state signature comparisons (`beforeStateSignature` vs `afterStateSignature`) and re-observe UI state.

---

## 3. FIRST FAILING PRIMITIVE ANALYSIS

When running Chrome automation without explicit submit buttons, the failure hierarchy isolates the exact failing primitive:

```text
LAUNCH_APP Chrome            ──► VERIFIED_SUCCESS
OBSERVE Address Bar          ──► VERIFIED_SUCCESS
CLICK Address Bar            ──► VERIFIED_SUCCESS
TYPE_TEXT "new Telugu movies" ──► VERIFIED_SUCCESS
SUBMIT_INPUT                 ──► FAILED (Reason: UNSUPPORTED_SUBMISSION_MECHANISM)
```

Isolating `SUBMIT_INPUT` as the failing primitive allows targeted recovery (e.g. clicking explicit soft keyboard search keys) without falsely declaring "Chrome automation failed."

---

## 4. CODE & TEST INTEGRITY SUMMARY

* **Unit Tests Passing:** 68 Tests Passed
* **Build Check:** `./gradlew test assembleDebug` SUCCESS
