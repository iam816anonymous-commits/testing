# PHASE 2.2 INITIAL ARCHITECTURE AUDIT

## Overview
This audit establishes the baseline for **Phase 2.2: Physical Device Validation, Capability Discovery & Diagnostic UI**.
The primary objective is to make the physical Android device (Android 8.1, API 27, ~4 GB RAM, ~64 GB Storage) the authoritative source of truth for runtime capabilities, sensor/actuator inventories, permission status, and UI automation verification.

---

## Existing Production Capabilities & Reused APIs

### 1. Perception & Observation
- **AutomationAccessibilityService.kt**: Collects root accessibility node hierarchies (`getRootNode()`), active package tracking (`activePackageName`), global back/home actions (`performGoBack()`), and bounds-based gesture tap dispatch (`dispatchGestureTap(x, y)`).
- **ScreenObservationProvider.kt**: MediaProjection screen snapshot capture and texture extraction.
- **CameraObservationProvider.kt**: CameraX surface frame capture for lightweight ambient/visual observation.
- **WorldState.kt**: Categorizes perception into Authoritative, Derived, Cached, and Stale-Prone metadata tiers with hash-based change detection.

### 2. Resolution & Execution
- **ActionResolver.kt**: 4-tier target candidate resolution (`VIEW_ID` -> `EXACT_TEXT` -> `CONTENT_DESCRIPTION` -> `ACCESSIBILITY_PROPERTIES`) with `captureSnapshot(root, pkg)` and candidate resolution via `resolveTargetWithAmbiguity(snapshot, target)`.
- **DeviceActionExecutor.kt**: Closed-loop action dispatcher (`OBSERVE -> RESOLVE -> PRECONDITION -> ACT -> WAIT -> OBSERVE -> VERIFY`). Ensures fresh pre-dispatch observations to eliminate `STALE_OBSERVATION`.
- **WaitEngine.kt**: Condition-based waiting (`waitUntil(condition, service)`) supporting `WAIT_FOR_TEXT`, `WAIT_FOR_PACKAGE`, `WAIT_FOR_STATE_CHANGE`, and timeout propagation.
- **GoalVerifier.kt**: Evaluates UI state changes to determine `GOAL_REACHED`, `PROGRESSING`, or `BLOCKED`.
- **RecoveryManager.kt**: Evaluates recovery decisions (`evaluateRecovery(failedStepCount, lastActionResult, actionSemantics)`) returning `RETRY`, `FAIL`, or `PAUSE`.

### 3. Agent & Decision Runtime
- **AgentCore.kt & AgentModels.kt**: Agent lifecycle states (`IDLE`, `EXECUTING`, `PAUSED`, `NEEDS_USER_INPUT`).
- **TaskDecisionEngine.kt**: Deterministic state-based local decision engine.
- **GoalModel.kt**: User goal parsing with strict command decomposition ("Type", "Press Enter", "Open App").
- **CapabilityRegistry.kt**: Dynamic system feature & capability tracking.

---

## Phase 2.2 Component Integrations

1. **Physical Device Discovery Engine**:
   - `DeviceCapabilityScanner.kt`: Queries Android system services (`SensorManager`, `Vibrator`, `CameraManager`, `PowerManager`, `AudioManager`, `PackageManager`, `ActivityManager`) without state collapsing (`PRESENT`, `AVAILABLE`, `PERMISSION_GRANTED`, `USABLE`, `UNSUPPORTED`, `BLOCKED`, `UNKNOWN`).

2. **Physical Test Framework**:
   - `PhysicalTestCase.kt`, `PhysicalTestRunner.kt`, `PhysicalTestRegistry.kt`: Standardized execution loop integrating directly with production `ActionResolver`, `WaitEngine`, and `RecoveryManager`.
   - `TestResult.kt`: Structured result model supporting `PASS`, `FAIL`, `BLOCKED`, `UNSUPPORTED`, `SKIPPED`, `ERROR`.

3. **Reporting & Evidence Export Engine**:
   - `ReportGenerator.kt`: Produces local JSON and Markdown reports (`DEVICE_PROFILE.json`, `CAPABILITIES.md`, `SENSOR_INVENTORY.json`, `ACTUATOR_INVENTORY.json`, `TEST_RESULTS.json`, `TEST_REPORT.md`, `FAILURES.md`) inside the application external files directory (`context.getExternalFilesDir(null)`).

4. **Technical Diagnostic UI Redesign**:
   - Diagnostic & Control Center in Jetpack Compose (`MainActivity.kt`) featuring `OverviewScreen`, `TestsScreen`, `CapabilitiesScreen`, `SensorsScreen`, `ActuatorsScreen`, `PermissionsScreen`, `FailuresScreen`, `EvidenceScreen`, and `LogsScreen`.

---

## API 27 Constraints & Build Verification
- **Compilation**: Both `compileDebugKotlin` and `compileReleaseKotlin` targets pass 100% cleanly.
- **Storage**: Sandbox-compliant file export using `getExternalFilesDir("physical_validation")`.
- **Safety**: Unapproved side-effect actions flagged `requiresUserApproval` return `BLOCKED_REQUIRES_USER_APPROVAL`.
