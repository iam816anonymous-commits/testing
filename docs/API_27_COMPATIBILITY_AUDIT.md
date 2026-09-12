# Android API 27 (Android 8.1.0) Automation Compatibility Audit

## 1. Executive Summary
Target Device: **TECNO IN6 (Android 8.1.0 / API 27 / minSdk 26)**
Primary Objective: Establish API 27 as a first-class, fully supported production baseline for general-purpose phone automation, removing false "Requires Android 11+" / API 30+ blocks for core navigation, gesture execution, accessibility perception, and app interaction.

During physical device testing on TECNO IN6, natural-language commands like "Go home" and "Go back" were incorrectly failing with `"Requires Android 11+"`.
This audit identifies the exact multi-layered root cause and provides the architectural remedy (`AndroidAutomationCompat`) and capability classification required to make API 27 fully functional.

---

## 2. Root Cause Analysis

### Layer 1: Missing System Command Routing in `TaskResolver`
* **Defect:** Natural-language system navigation commands ("Go home", "Press home", "Go back", "Recents", "Open Settings", "Read visible screen") were not handled by `TaskResolver.generateGenericWorkflowForTask()`.
* **Behavior:** Because they did not match "open <app>", `TaskResolver` fell back to `ChatGPTReasoningProvider`.
* **Fallback Behavior:** `ChatGPTReasoningProvider` returned a default 2-step plan:
  1. `READ_VISIBLE_UI`
  2. `CAPTURE_SCREEN`
* **Result:** Step 2 attempted `CAPTURE_SCREEN`, triggering Layer 2 failure.

### Layer 2: Hardcoded API 30+ Gate in `performCaptureScreen` & `AutomationAccessibilityService`
* **Defect:** `DeviceActionExecutor.performCaptureScreen()` and `AutomationAccessibilityService.captureScreenshot()` checked `if (SDK_INT < VERSION_CODES.R)` and returned `ActionResult(status = FAILED, reason = UNSUPPORTED_ANDROID_VERSION, message = "Requires Android 11+")`.
* **Remedy:** On API 27, `CAPTURE_SCREEN` must NOT hard-fail with an Android 11+ error. Instead, it must fall back to MediaProjection visual capture (`ScreenObservationProvider`) or return an Accessibility UI snapshot perception payload.

### Layer 3: Capability Classification vs API Level Confusion
* **Defect:** `DeviceCapabilityScanner` and `CapabilityRegistry` evaluated capabilities using monolithic version checks (`SDK_INT >= 30`).
* **Remedy:** Capabilities must be decoupled into four distinct taxonomy fields:
  1. `SUPPORTED_BY_API` (True on API 27 for `GLOBAL_ACTION_HOME`, `GLOBAL_ACTION_BACK`, `GLOBAL_ACTION_RECENTS`, `dispatchGesture`, `ACTION_SET_TEXT`, etc.)
  2. `SERVICE_AVAILABLE` (AccessibilityService running)
  3. `PERMISSION_GRANTED` (Accessibility permission granted)
  4. `RUNTIME_USABLE` (`SUPPORTED_BY_API && SERVICE_AVAILABLE && PERMISSION_GRANTED`)

---

## 3. Comprehensive Repository Audit Table

| Feature / Action | Current API Guard | Root Cause | API-27 Replacement / Strategy | API 27 Status | Version Guard Required? |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Global Home (`GLOBAL_ACTION_HOME`)** | None (supported since API 16) | Failed due to unrouted command falling back to `CAPTURE_SCREEN` | Route directly in `TaskResolver` to `PRESS_HOME` via `performGlobalAction(GLOBAL_ACTION_HOME)` | **SUPPORTED (API 16+)** | No |
| **Global Back (`GLOBAL_ACTION_BACK`)** | None (supported since API 16) | Failed due to unrouted command falling back to `CAPTURE_SCREEN` | Route directly in `TaskResolver` to `GO_BACK` via `performGoBack()` / `performGlobalAction(GLOBAL_ACTION_BACK)` | **SUPPORTED (API 16+)** | No |
| **Global Recents (`GLOBAL_ACTION_RECENTS`)** | None (supported since API 16) | Unrouted command falling back to `CAPTURE_SCREEN` | Route directly in `TaskResolver` to `PRESS_RECENTS` via `performGlobalAction(GLOBAL_ACTION_RECENTS)` | **SUPPORTED (API 16+)** | No |
| **Screenshot Capture (`CAPTURE_SCREEN`)** | `Build.VERSION.SDK_INT >= 30` | `AccessibilityService.takeScreenshot()` introduced in API 30 | On API < 30, fall back to `ScreenObservationProvider` (MediaProjection bitmap) or `captureSnapshot()` UI tree | **PARTIAL (MediaProjection / Snapshot Fallback)** | Yes (`if (SDK_INT >= 30) takeScreenshot else MediaProjection`) |
| **Gesture Tap (`dispatchGestureTap`)** | `Build.VERSION.SDK_INT >= 24` | Checked `SDK_INT >= N` | Fully supported on API 27 (`AccessibilityService.dispatchGesture()`) | **SUPPORTED (API 24+)** | Yes (`if (SDK_INT >= 24)`) |
| **Text Injection (`ACTION_SET_TEXT`)** | None (supported since API 21) | Node `ACTION_SET_TEXT` with `ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE` | Fully supported on API 27 via `node.performAction(ACTION_SET_TEXT, args)` | **SUPPORTED (API 21+)** | No |
| **App Launching (`LAUNCH_APP`)** | None | Uses `PackageManager.getLaunchIntentForPackage` + `Context.startActivity` | Fully supported on API 27 | **SUPPORTED (API 1+)** | No |
| **UI Tree Perception (`captureSnapshot`)** | None | Uses `rootInActiveWindow` and `AccessibilityNodeInfo` tree traversal | Fully supported on API 27 | **SUPPORTED (API 16+)** | No |

---

## 4. Gradle & Dependency Audit

* **compileSdk:** `35` (Allows compiling modern AndroidX libraries)
* **minSdk:** `26` (Covers Android 8.0 & 8.1 / TECNO IN6)
* **targetSdk:** `35`
* **Dependency Check:**
  - `androidx.core:core-ktx:1.13.1` (minSdk 21) -> OK
  - `androidx.compose.ui:ui:1.6.8` (minSdk 21) -> OK
  - `androidx.room:room-runtime:2.6.1` (minSdk 21) -> OK
  - `androidx.work:work-runtime-ktx:2.9.0` (minSdk 14) -> OK
  - `androidx.camera:camera-camera2:1.3.4` (minSdk 21) -> OK

---

## 5. Architectural Remedies

1. **`AndroidAutomationCompat.kt`:** Create compatibility abstraction for global actions, screenshots, and gesture dispatching.
2. **`TaskResolver.kt`:** Implement deterministic system command rules for "Go home", "Go back", "Recents", "Open Settings", "Read screen" so system navigation NEVER falls back to ChatGPT or requests screenshot capture on API 27.
3. **`DeviceActionExecutor.kt`:** Delegate screenshot and global navigation to `AndroidAutomationCompat`.
4. **`DeviceCapabilityScanner.kt`:** Explicitly report API 27 capabilities as `SUPPORTED_BY_API` and `USABLE` when Accessibility service is enabled.
