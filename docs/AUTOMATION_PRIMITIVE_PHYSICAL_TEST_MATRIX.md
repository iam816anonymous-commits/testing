# AUTOMATION PRIMITIVE PHYSICAL TEST MATRIX

**Date:** March 2025
**Target Device:** Physical Android 8.1 / API 27 Test Phone
**Scope:** 20-Primitive Physical Automation Verification Matrix

---

## PHYSICAL TEST MATRIX

| # | Automation Primitive | Mechanism / API | Unit Test Status | API 27 Physical Status | Evidence / Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1** | `LAUNCH_APP` | `PackageManager.getLaunchIntentForPackage()` | PASSED | PENDING PHYSICAL RUN | Resolves app names dynamically via `AppResolver` |
| **2** | `OBSERVE` | `getRootInActiveWindow()` + `UiSnapshot` | PASSED | PENDING PHYSICAL RUN | Captures node tree, clickable, editable, scrollable |
| **3** | `READ_VISIBLE_UI` | `snapshot.visibleTexts` | PASSED | PENDING PHYSICAL RUN | Returns list of all visible text labels |
| **4** | `RESOLVE_CLICK_TARGET` | `ActionResolver.resolveTargetWithAmbiguity` | PASSED | PENDING PHYSICAL RUN | View ID > Exact Text > Content Desc > Partial |
| **5** | `CLICK` | `AccessibilityNodeInfo.performAction(ACTION_CLICK)` | PASSED | PENDING PHYSICAL RUN | Parent node climbing implemented |
| **6** | `VERIFY_CLICK` | `beforeStateSignature` vs `afterStateSignature` | PASSED | PENDING PHYSICAL RUN | Confirms UI or package state change |
| **7** | `TYPE_TEXT` | `ACTION_SET_TEXT` | PASSED | PENDING PHYSICAL RUN | Injects CharSequence into editable field |
| **8** | `VERIFY_TYPED_TEXT` | Post-type snapshot editable text verification | PASSED | PENDING PHYSICAL RUN | Confirms typed string is reflected in view |
| **9** | `CLEAR_TEXT` | `ACTION_SET_TEXT` with empty string `""` | PASSED | PENDING PHYSICAL RUN | Clears editable text field |
| **10** | `SCROLL_DOWN` | `ACTION_SCROLL_FORWARD` | PASSED | PENDING PHYSICAL RUN | Scrollable container detection |
| **11** | `VERIFY_SCROLL_PROGRESS` | Post-scroll state signature comparison | PASSED | PENDING PHYSICAL RUN | Detects `NO_PROGRESS` / stuck execution |
| **12** | `SCROLL_UP` | `ACTION_SCROLL_BACKWARD` | PASSED | PENDING PHYSICAL RUN | Scrolls container backward |
| **13** | `GO_BACK` | `performGlobalAction(GLOBAL_ACTION_BACK)` | PASSED | PENDING PHYSICAL RUN | Navigates back |
| **14** | `VERIFY_BACK` | Post-back snapshot evaluation | PASSED | PENDING PHYSICAL RUN | Confirms window or state signature change |
| **15** | `WAIT_FOR_TEXT` | Polling `ActionResolver` with timeout | PASSED | PENDING PHYSICAL RUN | Bounded suspending wait |
| **16** | `WAIT_FOR_STATE_CHANGE` | Polling state signature with timeout | PASSED | PENDING PHYSICAL RUN | Bounded signature wait |
| **17** | `SUBMIT / ENTER` | `SEMANTIC_SUBMIT_CONTROL` | PASSED | PENDING PHYSICAL RUN | Resolves search/submit controls |
| **18** | `VERIFY_SUBMISSION` | Post-submit snapshot navigation evaluation | PASSED | PENDING PHYSICAL RUN | Confirms submission state transition |
| **19** | `REFRESH_OBSERVATION` | Capture fresh `UiSnapshot` | PASSED | PENDING PHYSICAL RUN | Refreshes UI snapshot state |
| **20** | `GO_HOME` | `performGlobalAction(GLOBAL_ACTION_HOME)` | PASSED | PENDING PHYSICAL RUN | Returns to Android Home screen |

---

## VERIFICATION SUMMARY

* **Unit Test Pass Rate:** 100% (68 / 68 Tests Passed)
* **Physical Device Pass Rate:** Pending physical run on connected API 27 hardware
