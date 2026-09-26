# SAFE PHYSICAL TEST NAVIGATION AUDIT

**Target Device:** TECNO IN6 / Android 8.1.0 / API 27 / ~4 GB RAM / ~64 GB Storage
**Audit Objective:** Analyze every test in the safe physical test suite to guarantee that running physical diagnostics NEVER displaces the foreground application to Home, Back, or another Activity.

---

## 1. CALL GRAPH & TRIGGER AUDIT

```text
User Taps [ Run Safe Physical Tests ] in MainActivity
        ↓
PhysicalTestRegistry.buildSafeValidationSuite()
        ↓
PhysicalTestRunner.executeSuite()
        ↓
Execution Block for each PhysicalTestCase
        ↓
DeviceActionExecutor / ActionResolver / GoalVerifier
```

---

## 2. TEST CASE NAVIGATION & INTERACTION AUDIT

| Test ID | Test Name | Category | Execution Policy | Can Change Foreground App? | Can Invoke Global Home/Back? | Risk | Required Fix / Hardening |
|---|---|---|---|---|---|---|---|
| **TEST-PHY-001** | System Home Navigation (GO_HOME) | NAVIGATION | `NAVIGATION` | NO (Audit mode) | NO | ZERO | Converted to non-destructive capability audit; rejects execution when running `READ_ONLY` or `INTERACTION` suites. |
| **TEST-PHY-002** | System Back Navigation (GO_BACK) | NAVIGATION | `NAVIGATION` | NO (Audit mode) | NO | ZERO | Re-categorized under `NAVIGATION`; skipped in safe `READ_ONLY` runs. |
| **TEST-PHY-003** | Launch Application - YouTube | INPUT_INTERACTION | `INTERACTION` | NO | NO | ZERO | Resolves PackageManager package intent without calling `startActivity()`. |
| **TEST-PHY-004** | Launch Application - Chrome | INPUT_INTERACTION | `INTERACTION` | NO | NO | ZERO | Resolves PackageManager package intent without calling `startActivity()`. |
| **TEST-PHY-005** | Chrome Address Bar Type Text Resolution | INPUT_INTERACTION | `READ_ONLY` | NO | NO | ZERO | Read-only target resolution against current snapshot. |
| **TEST-PHY-006** | YouTube Search Input Field Resolution | RESOLUTION | `READ_ONLY` | NO | NO | ZERO | Read-only target resolution against current snapshot. |
| **TEST-PHY-007** | YouTube Search Submit / Press Enter | INPUT_INTERACTION | `READ_ONLY` | NO | NO | ZERO | Checks capability non-destructively without dispatching IME enter. |
| **TEST-PHY-008** | Generic Text Editor Type Text | INPUT_INTERACTION | `READ_ONLY` | NO | NO | ZERO | Checks capability non-destructively. |
| **TEST-PHY-009** | Generic Scroll Action Resolution | INPUT_INTERACTION | `READ_ONLY` | NO | NO | ZERO | Inspects scrollable containers without dispatching scroll. |
| **TEST-PHY-010** | Read Visible Screen Content | OBSERVATION | `READ_ONLY` | NO | NO | ZERO | Pure observation tree extraction. |
| **TEST-PHY-011** | Flashlight Torch State Verification | VERIFICATION | `READ_ONLY` | NO | NO | ZERO | CameraManager capability verification. |
| **TEST-PHY-012** | Home Screen Launcher App Discovery | OBSERVATION | `READ_ONLY` | NO | NO | ZERO | PackageManager launcher query. |
| **TEST-PHY-013** | Home Screen Generic Scroll & App Discovery | OBSERVATION | `READ_ONLY` | NO | NO | ZERO | Read-only UI snapshot inspection. |
| **TEST-PHY-014** | Enter Target App & Enumerate Interactive Controls | OBSERVATION | `READ_ONLY` | NO | NO | ZERO | Read-only control enumeration. |
| **TEST-PHY-015** | Safe In-App Exploration | INPUT_INTERACTION | `READ_ONLY` | NO | NO | ZERO | Safe exploration gate evaluation. |
| **TEST-RES-001** | Action Candidate Target Resolution | RESOLUTION | `READ_ONLY` | NO | NO | ZERO | Synthetic mock snapshot resolution. |
| **TEST-INP-001** | System Home Button Dispatch | INPUT_INTERACTION | `READ_ONLY` | NO | NO | ZERO | Capability check only. |
| **TEST-VER-001** | WaitEngine Timeout Validation | VERIFICATION | `READ_ONLY` | NO | NO | ZERO | 200ms non-existent text wait. |
| **TEST-REC-001** | RecoveryManager Evaluation Boundaries | RECOVERY | `READ_ONLY` | NO | NO | ZERO | Rule boundary check. |

---

## 3. FOREGROUND PACKAGE INVARIANCE GUARANTEE

Before running any test in `PhysicalTestRunner.executeSuite()`, the active package is recorded:
```kotlin
val foregroundPackageBefore = service.resolveCurrentForegroundPackage(service.getRootNode())
```
After executing each test block, the active package is verified:
```kotlin
val foregroundPackageAfter = service.resolveCurrentForegroundPackage(service.getRootNode())
require(foregroundPackageBefore == foregroundPackageAfter)
```
If `foregroundPackageAfter != foregroundPackageBefore` for a `READ_ONLY` test, the test runner immediately fails the test with `ValidationFailureReason.FOREGROUND_CHANGED_UNEXPECTEDLY` and DOES NOT press Home or Back as a recovery side effect.
