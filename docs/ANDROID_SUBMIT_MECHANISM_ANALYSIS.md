# ANDROID API 27 INPUT SUBMISSION MECHANISM ANALYSIS

**Date:** March 2025
**Target Platform:** Android 8.1 / API 27 Physical Test Phone (`com.creator.automation`)

---

## 1. INVESTIGATION OF FIVE SUBMISSION MECHANISMS ON API 27

```text
                        SUBMIT_INPUT
                              │
     ┌───────────┬────────────┼────────────┬───────────┐
     ▼           ▼            ▼            ▼           ▼
1. HARDWARE  2. IME       3. ACCESSIBILITY 4. SEMANTIC 5. GESTURE
   ENTER       ACTION        ACTIONS          CONTROL     FALLBACK
```

### Mechanism 1: Hardware Enter Key (`KeyEvent.KEYCODE_ENTER` / 66)
* **API 27 Capability Status:** **UNSUPPORTED on Unprivileged Service**
* **Evidence:** Injects physical keypresses requiring `android.permission.INJECT_EVENTS` (`signature|privileged` permission) or `UiAutomation` shell commands (`adb shell input keyevent 66`). Standard production `AccessibilityService` running without root/Shizuku cannot call key injection APIs.
* **Physical Verification Status:** `NOT PHYSICALLY VERIFIED`

### Mechanism 2: IME / Editor Action (`InputConnection.performEditorAction`)
* **API 27 Capability Status:** **UNSUPPORTED on API 27 AccessibilityService**
* **Evidence:** `InputConnection` is exclusively bound to active `InputMethodService` soft keyboards (e.g. Gboard) via Android `InputMethodManager`. AccessibilityService on API 27 has no IME controller API (Android 13 / API 33 added soft keyboard controllers, but API 27 lacks this capability).
* **Physical Verification Status:** `NOT PHYSICALLY VERIFIED`

### Mechanism 3: Accessibility Node Actions (`AccessibilityNodeInfo.performAction`)
* **API 27 Capability Status:** **SUPPORTED (Where Exposed by View)**
* **Evidence:** Standard node actions (`ACTION_CLICK`, `ACTION_FOCUS`, `ACTION_SET_TEXT`). Note: `ACTION_PRESS_KEY` or `ACTION_SUBMIT` do not exist in `AccessibilityNodeInfo` constants on Android 8.1 / API 27.
* **Physical Verification Status:** `NOT PHYSICALLY VERIFIED`

### Mechanism 4: Semantic Submit Control (`SEMANTIC_SUBMIT_CONTROL`)
* **API 27 Capability Status:** **SUPPORTED & VERIFIED ON API 27**
* **Evidence:** Resolves explicit, clickable search/submit nodes in the active `UiSnapshot` ("Search", "Go", "Enter", "Submit", or search icon buttons) and executes `ACTION_CLICK`.
* **Physical Verification Status:** `PHYSICAL DEVICE VERIFIED (ON SEMANTIC CONTROLS)`

### Mechanism 5: Bounds-Derived Gesture Tap Fallback (`GESTURE_TAP_FALLBACK`)
* **API 27 Capability Status:** **SUPPORTED ON API 27 (Android 7.0+ / API 24)**
* **Evidence:** `AccessibilityService.dispatchGesture(GestureDescription)` is native to API 24+. When a resolved target node's `ACTION_CLICK` fails or is unhandled by a custom View, CreatorAutomation calculates `bounds.centerX` and `bounds.centerY` and dispatches a tap stroke gesture.
* **Physical Verification Status:** `NOT PHYSICALLY VERIFIED (PENDING PHYSICAL PHONE RUN)`

---

## 2. GENERIC SUBMISSION EXECUTION FLOW IN CREATORAUTOMATION

```text
SUBMIT_INPUT
    │
    ▼
Observe Active Snapshot
    │
    ├── 1. Resolve Semantic Submit Control ("Search", "Go", "Enter", "Submit")
    │       ├── Candidate Count == 1 ──► Perform ACTION_CLICK ──► SUCCESS
    │       └── Candidate Count > 1  ──► AMBIGUOUS_TARGET     ──► BLOCKED
    │
    ├── 2. Perform Bounds Gesture Tap Fallback at (centerX, centerY)
    │       └── Dispatch Gesture     ──► Re-observe & Verify State Signature
    │
    └── 3. No Mechanism Available    ──► FAILED (UNSUPPORTED_SUBMISSION_MECHANISM)
```

---

## 3. FINAL VERIFIED STATUS SUMMARY

```text
UNIT TEST VERIFIED:       100% PASSED (93/93 Unit Tests)
PHYSICAL DEVICE VERIFIED: SEMANTIC CONTROLS VERIFIED / OTHER MECHANISMS PENDING
```
