# PHASE 2.2 INITIAL ARCHITECTURE AUDIT

## Overview
This audit establishes the baseline for **Phase 2.2: Physical Device Validation, Capability Discovery & Diagnostic UI**.
The primary objective is to make the physical Android device (Android 8.1, API 27, ~4 GB RAM, ~64 GB Storage) the authoritative source of truth for runtime capabilities, sensor/actuator inventories, permission status, and UI automation verification.

---

## Existing Capabilities & Components

### 1. Perception & Observation
- **AutomationAccessibilityService.kt**: Collects root accessibility node hierarchies, focus tracking, bounds retrieval, and dispatches actions (`ACTION_CLICK`, `ACTION_SCROLL_FORWARD/BACKWARD`, `ACTION_SET_TEXT`, bounds-based `dispatchGestureTap`).
- **ScreenObservationProvider.kt**: MediaProjection screen snapshot capture and texture extraction.
- **CameraObservationProvider.kt**: CameraX surface frame capture for lightweight ambient/visual observation.
- **WorldState.kt**: Categorizes perception into Authoritative, Derived, Cached, and Stale-Prone metadata tiers with hash-based change detection.

### 2. Resolution & Execution
- **ActionResolver.kt**: 4-tier target candidate resolution (`VIEW_ID` -> `EXACT_TEXT` -> `CONTENT_DESCRIPTION` -> `ACCESSIBILITY_PROPERTIES`) with candidate scoring, parent node climbing, and strict ambiguity blocking (`AMBIGUOUS_TARGET`).
- **DeviceActionExecutor.kt**: Closed-loop action dispatcher (`OBSERVE -> RESOLVE -> PRECONDITION -> ACT -> WAIT -> OBSERVE -> VERIFY`). Ensures fresh pre-dispatch observations to eliminate `STALE_OBSERVATION`.
- **WaitEngine.kt**: Condition-based waiting for text, package changes, focus, or visual stability with timeout propagation.
- **GoalVerifier.kt**: Evaluates UI state changes to determine `GOAL_REACHED`, `PROGRESSING`, or `BLOCKED`.
- **StuckDetector.kt**: Sliding-window state loop detection.
- **RecoveryManager.kt**: Safe bounded retries and `NEEDS_USER_INPUT` gate for process-death or high-risk action recovery.

### 3. Agent & Decision Runtime
- **TaskDecisionEngine.kt**: Deterministic state-based local decision engine.
- **GoalModel.kt**: User goal parsing with strict command decomposition ("Type", "Press Enter", "Open App").
- **CapabilityRegistry.kt**: Dynamic system feature & capability tracking.
- **SkillRegistry.kt**: Generic re-usable automation skills (`SEARCH_IN_APP`, `INPUT_TEXT_AND_SUBMIT`, etc.).
- **ResourceGuard.kt**: Heap pressure monitoring and bitmap memory cleanup.

---

## Reusable Components for Phase 2.2
- **CapabilityRegistry.kt & CapabilitySnapshot.kt**: Can be extended to hold granular state distinction (`PRESENT`, `AVAILABLE`, `PERMISSION_GRANTED`, `USABLE`, `UNSUPPORTED`, `BLOCKED`, `UNKNOWN`).
- **WorldState.kt & ObservationProvider.kt**: Reusable for pre- and post-test state captures during physical validation suite execution.
- **DeviceActionExecutor.kt & WaitEngine.kt**: Foundation for executing physical test interactions safely.
- **AutomationOverlayState.kt & MainActivity.kt**: Reusable diagnostic overlay & Compose UI infrastructure.

---

## Missing Components to Build for Phase 2.2

1. **Physical Device Discovery Engine**:
   - `DeviceCapabilityScanner.kt`: Multi-stage scanner querying Android system services.
   - `SensorDiscovery.kt`: Real-time hardware enumeration via `SensorManager`.
   - `ActuatorDiscovery.kt`: Real-time hardware actuator enumeration (Vibrator, Camera Flash, Display, Audio, System Inputs).
   - `PermissionInventory.kt`: Real-time permission status check across manifest permissions.
   - `DeviceProfileBuilder.kt`: Aggregates runtime hardware, OS, memory, storage, and screen metadata.

2. **Physical Test Framework**:
   - `PhysicalTestCase.kt`, `PhysicalTestRunner.kt`, `PhysicalTestRegistry.kt`: Standardized execution loop (`Precondition -> Observe -> Resolve -> Act -> Wait -> Observe -> Verify -> Evidence`).
   - `TestResult.kt`: Structured result model supporting `PASS`, `FAIL`, `BLOCKED`, `UNSUPPORTED`, `SKIPPED`, `ERROR` with candidate counts, evidence, and diagnostic traces.

3. **Reporting & Evidence Export Engine**:
   - `ReportGenerator.kt`: Produces local JSON and Markdown reports (`DEVICE_PROFILE.json`, `CAPABILITIES.md`, `SENSOR_INVENTORY.json`, `ACTUATOR_INVENTORY.json`, `TEST_RESULTS.json`, `TEST_REPORT.md`, `FAILURES.md`).

4. **Technical Diagnostic UI Redesign**:
   - Diagnostic & Control Center in Jetpack Compose (`OverviewScreen`, `TestsScreen`, `CapabilitiesScreen`, `SensorsScreen`, `ActuatorsScreen`, `PermissionsScreen`, `FailuresScreen`, `EvidenceScreen`, `LogsScreen`).

---

## API 27 Constraints & Risks

- **Storage Permissions**: API 27 strictly enforces application sandbox rules. Exported reports must be saved in application internal/external files directories (`context.getExternalFilesDir(null)`) or shared via Android Intent to avoid storage permission violations.
- **Hardware Variation**: Sensor/actuator presence varies across physical API 27 devices. States must never be collapsed (`PRESENT` != `PERMISSION_GRANTED` != `USABLE`).
- **Safety**: Test runner must prevent destructive actions on personal physical devices. Unsafe operations must be flagged `BLOCKED_REQUIRES_USER_APPROVAL`.
- **Memory Boundaries**: Keep physical discovery and test suite execution within 4 GB RAM / 64 GB storage constraints (no heavy bitmaps retained in heap; active GC pass between test runs).

---

## Integration Plan
1. Hook `DeviceCapabilityScanner` into `MainActivity` startup and manual refresh triggers.
2. Integrate `PhysicalTestRunner` with existing `DeviceActionExecutor` and `ObservationProvider`.
3. Wire `ReportGenerator` to write to `context.getExternalFilesDir("physical_validation")`.
4. Update Compose UI in `MainActivity.kt` with a side navigation / tabbed diagnostic interface.
