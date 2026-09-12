# WaitEngine Package Wait Diagnostic & Bug Root Cause Analysis

## 1. Root Cause Summary
- **Bug:** Execution timed out with error `Timeout waiting for WAIT_FOR_PACKAGE("")`.
- **Root Cause Location:** `TaskResolver.kt` `generateGenericWorkflowForTask()`.
- **Root Cause Trigger:** When resolving generic app launch tasks (e.g., "Open YouTube and search..."), if `AppResolver` returned `targetPackage = null` or if the package was not pre-resolved at task generation time, `TaskResolver` created a `WaitCondition(WaitConditionType.WAIT_FOR_PACKAGE, expectedValue = "")`.
- **Failure Mechanism:** In `WaitEngine.kt`, checking `snap.packageName.equals("", ignoreCase = true)` always evaluated to `false` because empty strings were guarded or did not match active app packages, causing `WaitEngine` to spin for 10,000ms until timeout, aborting subsequent typing and searching steps.

---

## 2. Implemented Fixes
1. **Immediate `INVALID_WAIT_CONDITION` Rejection in `WaitEngine.kt`:**
   - Any `WAIT_FOR_PACKAGE`, `WAIT_FOR_TEXT`, `WAIT_FOR_VIEW_ID`, or `WAIT_FOR_STATE_SIGNATURE` created with a null or blank `expectedValue` is immediately rejected with duration 0ms and `INVALID_WAIT_CONDITION` failure reason rather than polling until timeout.
2. **Package Wait Sanitization in `TaskResolver.kt`:**
   - If `targetPackage` is missing at task generation time, `TaskResolver` creates a `WAIT_FOR_STATE_CHANGE` wait condition instead of `WAIT_FOR_PACKAGE("")`.
3. **Dynamic Package Propagation in `DeviceActionExecutor.kt`:**
   - When `performLaunchApp` resolves the actual package name dynamically via `AppResolver`, it updates the active `WaitCondition` with `appRes.packageName`.
