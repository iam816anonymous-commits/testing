# AUTOMATION ENGINE 2.1 PHYSICAL API 27 DEVICE TEST PLAN

**Date:** March 2025
**Target Device:** Android 8.1 / API 27 Physical Phone
**Package:** `com.creator.automation`
**Current Status:** **NOT YET PHYSICALLY VERIFIED** (API 27 test phone currently unavailable)

---

## 1. PURPOSE

This document outlines the step-by-step physical device test protocol to validate **Automation Engine 2.1** on real Android 8.1 / API 27 hardware once the test device is available.

---

## 2. PREREQUISITES & PREPARATION

1. Build & install debug APK on API 27 phone:
   ```bash
   ./gradlew installDebug
   ```
2. Open **Settings -> Accessibility** on phone.
3. Locate **Creator Automation** service and toggle it **ON**.
4. Grant required permissions (Camera, MediaProjection if prompted).

---

## 3. PHYSICAL TEST SUITE 1 — UNSEEN APP AUTOMATION (CHROME)

### Objective
Prove that the generic engine can launch Chrome, search for a query, and handle state transitions **without any pre-recorded Chrome workflow**.

### Test Execution Steps
1. Launch `CreatorAutomation` app.
2. Select **Agent Runtime** tab or enter task request:
   `"Open Chrome and search for new Telugu movies"`
3. Tap **Start Persistent Task Session**.
4. Observe physical device behavior:
   * **Step 1:** Engine resolves app label `"Chrome"` via `AppResolver`, launches `com.android.chrome`, and waits for `WAIT_FOR_PACKAGE`.
   * **Step 2:** Engine captures fresh `UiSnapshot`, resolves search/address bar, focuses, and injects text `"new Telugu movies"` via Accessibility API (`TYPE_TEXT`).
   * **Step 3:** Engine executes `PRESS_ENTER`, clicking the search button or IME search key.
   * **Step 4:** Engine waits for state signature transition (`WAIT_FOR_STATE_CHANGE`), observes search result UI, and logs `ActionAuditRecord`.
5. Tap **Back** button on physical phone.
6. Verify engine observes package return and logs completed state.

---

## 4. PHYSICAL TEST SUITE 2 — AMBIGUITY & USER INTERVENTION

### Objective
Verify that target ambiguity blocks execution and triggers `NEEDS_USER_INPUT` UI alert.

### Test Execution Steps
1. Open an application presenting multiple identical buttons (e.g. Settings dialogs with multiple "OK" or "Cancel" buttons).
2. Request action on ambiguous text: `"Click OK"`.
3. Verify engine detects `res.candidateCount > 1`, blocks action dispatch with `ExecutionReason.AMBIGUOUS_TARGET`, and displays amber **USER INTERVENTION REQUIRED** alert on `MainActivity`.
4. Manually resolve button tap on phone screen.
5. Tap **Resume Agent** on `MainActivity`.
6. Verify engine captures fresh device observation and resumes execution.

---

## 5. PHYSICAL TEST SUITE 3 — PROCESS-DEATH RECOVERY (CASE D)

### Objective
Verify process-death safety when an unverified `NON_IDEMPOTENT` action is interrupted by app/process kill.

### Test Execution Steps
1. Initiate a multi-step task involving a form submit action.
2. Force-stop `CreatorAutomation` via Android Settings or `adb shell am force-stop com.creator.automation` mid-execution.
3. Relaunch `CreatorAutomation`.
4. Tap **Recover Interrupted** on **Agent Runtime** tab.
5. Verify `AgentRuntimeManager` observes live device state first, detects unverified `NON_IDEMPOTENT` audit record, halts re-execution, and transitions session to `NEEDS_USER_INPUT`.

---

## 6. PHYSICAL TEST VERIFICATION MATRIX

| Physical Test | Status | Evidence / Observation |
| :--- | :---: | :--- |
| **1. Generic App Launch & Search (Chrome)** | `NOT YET PHYSICALLY VERIFIED` | Awaiting API 27 phone availability |
| **2. Target Ambiguity Blocking** | `NOT YET PHYSICALLY VERIFIED` | Awaiting API 27 phone availability |
| **3. Process-Death Safety Recovery** | `NOT YET PHYSICALLY VERIFIED` | Awaiting API 27 phone availability |
| **4. CameraX Luminance Perception** | `NOT YET PHYSICALLY VERIFIED` | Awaiting API 27 phone availability |
| **5. MediaProjection Screen Perception** | `NOT YET PHYSICALLY VERIFIED` | Awaiting API 27 phone availability |
