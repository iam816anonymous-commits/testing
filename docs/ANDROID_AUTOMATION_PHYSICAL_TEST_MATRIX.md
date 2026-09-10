# ANDROID AUTOMATION PHYSICAL TEST MATRIX

**Date:** March 2025
**Target Platform:** Physical Android 8.1 / API 27 Test Phone
**Scope:** 20-Primitive Automation & Perception Verification Matrix
**Status Classification Legend:**
* **IMPLEMENTED:** Code exists and compiles in production.
* **UNIT TESTED:** Covered by passing unit tests (93/93 passed).
* **PHYSICALLY VERIFIED:** Dispatched on physical Android 8.1 hardware with observed UI state change.
* **BLOCKED (ADB):** Physical execution paused due to missing ADB hardware device connection.

---

## PHYSICAL TEST MATRIX

| # | Automation Primitive | Mechanism / API | Unit Test Status | API 27 Physical Status | Status Category | Evidence / Notes |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **1** | `LAUNCH_APP` | `PackageManager.getLaunchIntentForPackage()` | PASSED | BLOCKED (ADB) | IMPLEMENTED / UNIT TESTED | Resolves app names dynamically via `AppResolver` |
| **2** | `OBSERVE` | `getRootInActiveWindow()` + `UiSnapshot` | PASSED | BLOCKED (ADB) | IMPLEMENTED / UNIT TESTED | Captures node tree, clickable, editable, scrollable |
| **3** | `READ_VISIBLE_UI` | `snapshot.visibleTexts` | PASSED | BLOCKED (ADB) | IMPLEMENTED / UNIT TESTED | Returns list of all visible text labels |
| **4** | `RESOLVE_CLICK_TARGET` | `ActionResolver.resolveTargetWithAmbiguity` | PASSED | BLOCKED (ADB) | IMPLEMENTED / UNIT TESTED | View ID > Exact Text > Content Desc > Partial |
| **5** | `CLICK` | `AccessibilityNodeInfo.performAction(ACTION_CLICK)` | PASSED | BLOCKED (ADB) | IMPLEMENTED / UNIT TESTED | Parent node climbing + bounds gesture fallback |
| **6** | `VERIFY_CLICK` | `beforeStateSignature` vs `afterStateSignature` | PASSED | BLOCKED (ADB) | IMPLEMENTED / UNIT TESTED | Confirms UI or package state change |
| **7** | `TYPE_TEXT` | `ACTION_SET_TEXT` | PASSED | BLOCKED (ADB) | IMPLEMENTED / UNIT TESTED | Injects CharSequence into editable field |
| **8** | `VERIFY_TYPED_TEXT` | Post-type snapshot editable text verification | PASSED | BLOCKED (ADB) | IMPLEMENTED / UNIT TESTED | Confirms typed string is reflected in view |
| **9** | `CLEAR_TEXT` | `ACTION_SET_TEXT` with empty string `""` | PASSED | BLOCKED (ADB) | IMPLEMENTED / UNIT TESTED | Clears editable text field |
| **10** | `SCROLL_DOWN` | `ACTION_SCROLL_FORWARD` | PASSED | BLOCKED (ADB) | IMPLEMENTED / UNIT TESTED | Scrollable container detection |
| **11** | `VERIFY_SCROLL_PROGRESS` | Post-scroll state signature comparison | PASSED | BLOCKED (ADB) | IMPLEMENTED / UNIT TESTED | Detects `NO_PROGRESS` / stuck execution |
| **12** | `SCROLL_UP` | `ACTION_SCROLL_BACKWARD` | PASSED | BLOCKED (ADB) | IMPLEMENTED / UNIT TESTED | Scrolls container backward |
| **13** | `GO_BACK` | `performGlobalAction(GLOBAL_ACTION_BACK)` | PASSED | BLOCKED (ADB) | IMPLEMENTED / UNIT TESTED | Navigates back |
| **14** | `VERIFY_BACK` | Post-back snapshot evaluation | PASSED | BLOCKED (ADB) | IMPLEMENTED / UNIT TESTED | Confirms window or state signature change |
| **15** | `WAIT_FOR_TEXT` | Polling `ActionResolver` with timeout | PASSED | BLOCKED (ADB) | IMPLEMENTED / UNIT TESTED | Bounded suspending wait |
| **16** | `WAIT_FOR_STATE_CHANGE` | Polling state signature with timeout | PASSED | BLOCKED (ADB) | IMPLEMENTED / UNIT TESTED | Bounded signature wait |
| **17** | `SUBMIT / ENTER` | `SEMANTIC_SUBMIT_CONTROL` | PASSED | BLOCKED (ADB) | IMPLEMENTED / UNIT TESTED | Resolves search/submit controls |
| **18** | `VERIFY_SUBMISSION` | Post-submit snapshot navigation evaluation | PASSED | BLOCKED (ADB) | IMPLEMENTED / UNIT TESTED | Confirms submission state transition |
| **19** | `REFRESH_OBSERVATION` | Capture fresh `UiSnapshot` | PASSED | BLOCKED (ADB) | IMPLEMENTED / UNIT TESTED | Refreshes UI snapshot state |
| **20** | `GO_HOME` | `performGlobalAction(GLOBAL_ACTION_HOME)` | PASSED | BLOCKED (ADB) | IMPLEMENTED / UNIT TESTED | Returns to Android Home screen |

---

## VERIFICATION SUMMARY

* **Unit Test Pass Rate:** 100% (93 / 93 Tests Passed)
* **Physical Device Pass Rate:** Blocked pending physical ADB connection to Android 8.1 hardware
