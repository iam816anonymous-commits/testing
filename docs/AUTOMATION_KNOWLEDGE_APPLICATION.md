# AUTOMATION KNOWLEDGE APPLICATION & DYNAMIC APP DISCOVERY AUDIT

**Date:** March 2025
**Target Platform:** Android 8.1 / API 27 (4 GB RAM, 64 GB Storage)
**Package:** `com.creator.automation`
**Execution Status:** IMPLEMENTED & FIXTURE TESTED (131 Unit Tests Passed) | PHYSICAL CHROME VALIDATION PENDING OWNER RUN

---

## 1. DYNAMIC APPLICATION DISCOVERY ARCHITECTURE (`AppResolver.kt`)

### 1.1 Removal of Hardcoded Package Maps
To satisfy the generic phone agent requirement that **nothing about installed applications is hardcoded**, the `WELL_KNOWN_APP_PACKAGES` map was completely removed from `AppResolver.kt`.
Similarly, hardcoded package mappings were removed from `GoalModel.parse()`. App names and packages are discovered dynamically at runtime as state facts.

### 1.2 Dynamic Discovery Pipeline
When the agent receives a request like `"Open Google"` or `"Open Chrome"`:

```text
USER INTENT ("Open Google")
    │
    ▼
GoalModel.parse() ──> targetAppQuery = "Google"
    │
    ▼
TaskDecisionEngine ──> ActionDecision(LAUNCH_APP, target = "Google")
    │
    ▼
AppResolver.resolveApplication("Google")
    │
    ├─> Direct Package Check: contains(".") -> false
    └─> Dynamic PackageManager Scan: pm.queryIntentActivities(ACTION_MAIN, CATEGORY_LAUNCHER)
            │
            ├─> Discovers all installed launcher activities on device
            ├─> Filters matching app labels/packages containing "Google"
            ├─> If exact label match exists (e.g. "Google Search") -> SUCCESS
            ├─> If multiple candidate launcher apps match without exact label -> AMBIGUOUS_APPLICATION
            └─> If 0 candidate launcher apps match -> APP_NOT_INSTALLED
```

---

## 2. INTENT SEPARATION STANDARDS

The engine distinguishes intent categories without collapsing them into hardcoded shortcuts:

| User Input | Parsed Action Intent | Target / Payload | Resulting Substrate Execution |
| :--- | :--- | :--- | :--- |
| `"Open Google"` | `ActionType.LAUNCH_APP` | `"Google"` | Dynamic `AppResolver` launcher activity search |
| `"Go to google.com"` | `ActionType.OPEN_URL` | `"google.com"` | Android `ACTION_VIEW` URL intent dispatch |
| `"Open Chrome"` | `ActionType.LAUNCH_APP` | `"Chrome"` | Dynamic `AppResolver` launcher search for Chrome |
| `"Search Google for new Telugu movies"` | `ActionType.SUBMIT_INPUT` | `"new Telugu movies"` | Target typing & generic submit control resolution |

---

## 3. REAL-DEVICE OWNER PHYSICAL TEST PROCEDURE & DIAGNOSTIC TRACE LOGGING

When testing on a physical Android 8.1 / API 27 phone:

### Test Procedure for App Launch & Search:
1. `"Open Chrome"`
2. `"Open Google"`
3. `"Open YouTube"`
4. `"Open Maps"`
5. `"Go to google.com"`
6. `"Search Google for new Telugu movies"`

### Diagnostic Trace Logging Format (`DeviceActionExecutor` / `AppResolver`):
```text
APP_RESOLVE_DIAGNOSTIC:
  requestedApp=Google
  normalizedTarget=google
  candidateCount=2
  candidatePackages=[com.google.android.googlequicksearchbox, com.google.android.apps.maps]
  resolutionStatus=AMBIGUOUS_APPLICATION
  selectedPackage=null
  explanation=Multiple applications (2) matched query 'Google'
```

---

## 4. TEST SUITE RESULTS (131 PASSED, 0 FAILED)

- **Total Test Suites:** 29
- **Total Unit Tests:** 131
- **Test Failures:** 0
- **Test Errors:** 0
- **Build Commands Run:**
  - `./gradlew test` -> **BUILD SUCCESSFUL**
  - `./gradlew test assembleDebug` -> **BUILD SUCCESSFUL**

---

## 5. PHYSICAL CHROME VALIDATION STATEMENT

> **PHYSICAL CHROME VALIDATION STATUS:** PENDING OWNER TESTING ON PHYSICAL DEVICE.
> As Jules cannot perform physical runs on the owner's phone (`adb devices` returned 0 connected devices), physical execution on Chrome / API 27 remains **UNRESOLVED / BLOCKED** and must be tested by the phone owner using `app-debug.apk`.
