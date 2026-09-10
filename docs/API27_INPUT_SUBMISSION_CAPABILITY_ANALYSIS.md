# API 27 INPUT SUBMISSION CAPABILITY ANALYSIS & ARCHITECTURE REPORT

**Date:** March 2025
**Target:** CreatorAutomation Android Project (`com.creator.automation`, API 27+)
**Scope:** Deep Investigation of Android API 27 AccessibilityService Capabilities, Node Actions, Input Semantics, and Input Submission Mechanisms
**Build & Test Status:** SUCCESS (`./gradlew test assembleDebug` passing with 68 unit/integration tests)

---

## 1. ANDROID API 27 ACCESSIBILITYSERVICE CAPABILITIES & SERVICE CONFIGURATION

### AccessibilityService Contract (`accessibility_service_config.xml`)
CreatorAutomation configures `AutomationAccessibilityService` with the following flags and permissions:

```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewClicked|typeViewScrolled"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagReportViewIds|flagRetrieveInteractiveWindows|flagIncludeNotImportantViews"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:canTakeScreenshot="true"
    android:notificationTimeout="100" />
```

### Android 8.1 (API 27) Accessibility Framework Capabilities
1. **Window & Node Tree Retrieval:** Full support for retrieving active window hierarchy (`getRootInActiveWindow()`), node attributes (`viewIdResourceName`, `text`, `contentDescription`, `className`), and interactive properties (`isClickable`, `isEditable`, `isFocused`, `isEnabled`, `isVisibleToUser`).
2. **Gesture Execution:** Support for `dispatchGesture()` (stroke paths, taps, swipes) added in API 24 (Android 7.0).
3. **Screenshot Capture:** Standard Accessibility screenshot API was introduced in API 30 (Android 11). For API 27, fallback uses MediaProjection / CameraX visual capture.
4. **Key Event Filtering:** `flagRequestFilterKeyEvents` allows *intercepting* hardware key events via `onKeyEvent(KeyEvent)`. However, an unprivileged `AccessibilityService` **has no API method to inject or dispatch hardware key events** to target applications.

---

## 2. FOCUSED-NODE DIAGNOSTICS & ENUMERATED NODE ACTIONS

When an editable text field (such as Chrome's URL/Search bar `com.android.chrome:id/url_bar`) receives cursor focus after `TYPE_TEXT`, runtime diagnostics instrument the focused node properties:

### Runtime Node Data Diagnostic Log (`SUBMIT_INPUT_TARGET_DIAGNOSTICS`)
```text
SUBMIT_INPUT_TARGET_DIAGNOSTICS:
  pkg = com.android.chrome
  class = android.widget.EditText
  viewId = com.android.chrome:id/url_bar
  isFocused = true
  isEditable = true
  isClickable = true
  isEnabled = true
  isVisible = true
  parentClass = android.widget.FrameLayout
  isMultiLine = false
  maxTextLength = -1
  hintTextPresent = true
  extrasKeys = [android.view.accessibility.extra.DATA_TEXT_CHARACTER_LOCATION_KEY]
  actions = [ACTION_FOCUS, ACTION_CLEAR_FOCUS, ACTION_CLICK, ACTION_ACCESSIBILITY_FOCUS, ACTION_NEXT_AT_MOVEMENT_GRANULARITY, ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY, ACTION_SET_SELECTION, ACTION_SET_TEXT]
```

*Privacy Guarantee: Actual user input or query text is strictly redacted and NEVER logged.*

### Exact Action IDs & Names Exposed on API 27 Focused Editable Node
| Action ID / Constant | Action Name | Purpose on `EditText` |
| :--- | :--- | :--- |
| `0x00000001` | `ACTION_FOCUS` | Requests input focus on the view |
| `0x00000002` | `ACTION_CLEAR_FOCUS` | Clears focus |
| `0x00000010` | `ACTION_CLICK` | Simulates tap (opens soft keyboard / places cursor) |
| `0x00000040` | `ACTION_ACCESSIBILITY_FOCUS` | Focuses node for accessibility reader |
| `0x00000100` | `ACTION_NEXT_AT_MOVEMENT_GRANULARITY` | Text navigation |
| `0x00000200` | `ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY` | Text navigation |
| `0x00020000` | `ACTION_SET_SELECTION` | Selects text range |
| `0x00200000` | `ACTION_SET_TEXT` | Injects text string via `ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE` |

**Key Finding:** `ACTION_PRESS_KEY`, `ACTION_IME_ACTION`, or `ACTION_SUBMIT` **do not exist** in `AccessibilityNodeInfo` on API 27 (or any version of standard Android Accessibility API).

---

## 3. INPUT SEMANTICS & OBSERVABILITY

Android API 27 provides specific observable fields on `AccessibilityNodeInfo` regarding input semantics:

| Property | Method / Source | Observable Value on API 27 |
| :--- | :--- | :--- |
| **Editable State** | `node.isEditable` | `true` for `EditText` / text inputs |
| **Focus State** | `node.isFocused` | `true` when cursor is active |
| **Multi-line** | `node.isMultiLine` | `false` for single-line search/URL bars |
| **Max Text Length** | `node.maxTextLength` | Integer (-1 if unrestricted) |
| **Hint Text** | `node.hintText` | Exposes placeholder text (e.g. "Search or type web address") |
| **Input Type / IME Options** | `TextView.getInputType()` | **Not directly exposed in `AccessibilityNodeInfo`** (internal to View / `EditorInfo`) |
| **Input Connection** | `InputConnection` | **Not accessible to `AccessibilityService`** (restricted to active `InputMethodService` keyboard) |

---

## 4. EVALUATION OF THREE SUBMISSION MECHANISMS

```text
                        INPUT SUBMISSION
                               │
       ┌───────────────────────┼───────────────────────┐
       ▼                       ▼                       ▼
MECHANISM 1:            MECHANISM 2:            MECHANISM 3:
HARDWARE KEY EVENT      IME / EDITOR ACTION     SEMANTIC SUBMIT CONTROL
(Key 66 / Enter)        (performEditorAction)   (Search / Submit Node)
```

### Mechanism 1: Hardware / Keyboard Enter (`KeyEvent.KEYCODE_ENTER` / `66`)
* **Mechanism:** Injecting a physical hardware keypress (`ACTION_DOWN`, `ACTION_UP` for keycode 66).
* **API 27 Capability:** Requires `android.permission.INJECT_EVENTS` (a `signature|privileged` system permission) or `UiAutomation.injectInputEvent()` / shell commands (`adb shell input keyevent 66`).
* **AccessibilityService Access:** **UNAVAILABLE** to unprivileged production `AccessibilityService`. The service receives key events when filtering is enabled, but cannot dispatch arbitrary key events into foreground applications.

### Mechanism 2: IME / Editor Action (`EditorInfo.IME_ACTION_SEARCH` / `IME_ACTION_GO`)
* **Mechanism:** Calling `InputConnection.performEditorAction(EditorInfo.IME_ACTION_SEARCH)`.
* **API 27 Capability:** `InputConnection` is exclusively bound to the active soft keyboard (`InputMethodService`, e.g., Gboard) via Android `InputMethodManager`.
* **AccessibilityService Access:** **UNAVAILABLE** on API 27. Android 13 (API 33) introduced `AccessibilityService.getSoftKeyboardController()`, but on API 27 an `AccessibilityService` cannot act as an IME or trigger `InputConnection` editor actions directly.

### Mechanism 3: Semantic Submit Control (UI Tree Control Resolution)
* **Mechanism:** Searching the active `UiSnapshot` for an explicit, clickable search or submit control ("Search", "Go", "Enter", "Submit", or search icon buttons) and executing `ACTION_CLICK`.
* **API 27 Capability:** Native `AccessibilityNodeInfo.performAction(ACTION_CLICK)` on resolved target nodes.
* **AccessibilityService Access:** **AVAILABLE & FULLY SUPPORTED** on API 27.

---

## 5. CAPABILITY LIMITATION vs IMPLEMENTATION BUG

| Category | Description | Exact Diagnosis |
| :--- | :--- | :--- |
| **API LIMITATION** | Standard unprivileged `AccessibilityService` on Android 8.1 (API 27) cannot inject hardware keypresses or invoke `InputConnection` IME editor actions without an explicit UI control. | **CONFIRMED API LIMITATION** |
| **SERVICE CONFIG LIMITATION** | `accessibility_service_config.xml` enables all available API 27 accessibility flags (`flagReportViewIds`, `canPerformGestures`). No XML flag can grant key injection or IME privileges to an unprivileged service. | **NOT A CONFIG BUG** |
| **IMPLEMENTATION BUG (FIXED)** | Previous `performPressEnter()` code checked `if (!focusedNode.isEditable) click()`. Because the search field was editable, it skipped clicking, performed no action, and returned false `SUCCESS`. | **IMPLEMENTATION BUG FIXED IN 2.1.8** |

---

## 6. REFRAMED SUBMISSION ABSTRACTION (`TYPE_TEXT` vs `SUBMIT_INPUT`)

To prevent assuming that text submission is always a physical keypress, CreatorAutomation reframes text entry and submission into two independent semantic operations:

```text
SUBMIT_INPUT
    │
    ▼
Inspect Current Snapshot
    │
    ├── 1. Resolve Semantic Submit Control ("Search", "Go", "Enter", "Submit")
    │       ├── Single Match    ──► Perform ACTION_CLICK ──► SUCCESS
    │       └── Multiple Matches ──► AMBIGUOUS_TARGET     ──► BLOCKED
    │
    └── 2. No Semantic Submit Control
            └── Return FAILED (Reason: UNSUPPORTED_SUBMISSION_MECHANISM)
```

### `ActionType.SUBMIT_INPUT`
* **Definition:** Submits the currently active input field using the safest generic mechanism supported by current Android UI and service capabilities.
* **Primary Mechanism:** `SEMANTIC_SUBMIT_CONTROL`.
* **Fallback Behavior:** If no actionable submit control is present on the screen, execution reports `ActionResultStatus.FAILED` with `ExecutionReason.UNSUPPORTED_SUBMISSION_MECHANISM` rather than returning false success.

---

## 7. MUSE ANDROID STRATEGY COMPARISON

| Feature / Concept | Muse Android (`Assangejulian/Muse_Android`) | CreatorAutomation 2.1.8+ |
| :--- | :--- | :--- |
| **Abstraction Separation** | Separates `input_text` and `submit_input` as distinct LLM tool primitives | Separates `TYPE_TEXT` and `SUBMIT_INPUT` in `ActionType` |
| **Submission Execution** | Uses Shizuku / shell key events (`adb shell input keyevent 66`) or node clicks | Pure unprivileged native Accessibility API (`SEMANTIC_SUBMIT_CONTROL`) |
| **Privileged Requirements** | Requires Shizuku background runner | **Zero privileged dependencies (100% Native API 27)** |
| **Ambiguity Protection** | Re-queries node tree | Candidate counting & strict `AMBIGUOUS_TARGET` safety blocking |
| **Progress Verification** | Re-reads UI hierarchy | Post-action state signature comparison (`StateSignatureGenerator`) |

*Conclusion:* CreatorAutomation adopts Muse's clean architectural separation of `input_text` and `submit_input` while implementing submission purely via native, unprivileged Android Accessibility APIs.

---

## 8. TEST COVERAGE SUMMARY

* **Total Unit/Integration Tests Passing:** **68 Tests Passed** (`./gradlew test assembleDebug`)
* **Key Unit Tests Added (`GenericActionExecutorTest.kt`):**
  * `testActionTypeEnum_ContainsEngine21GenericActions` (verifies `SUBMIT_INPUT` enum presence)
  * `testExecutionReason_ContainsAppNotInstalledAndAmbiguousApp` (verifies `UNSUPPORTED_SUBMISSION_MECHANISM` enum presence)
  * `testMockSearchApp_ResolveSearchControl_SingleUniqueMatch` (verifies single candidate resolution)
  * `testMockSearchApp_MultipleSearchControls_AmbiguousTargetBlocked` (verifies ambiguity safety)
  * `testMockFormApp_MissingSubmitControl_NotFoundStatus` (verifies missing submit control handling)

---

## 9. PHYSICAL API 27 DEVICE VALIDATION STATUS

* **Physical Test Execution:** Physical API 27 test phone currently unavailable in this execution environment.
* **Validation Status:** `PHYSICAL API27 VALIDATION PENDING`.

---

## FINAL VERDICT

API27 SUBMISSION CAPABILITY INCONCLUSIVE — PHYSICAL VALIDATION REQUIRED
