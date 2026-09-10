# ACCESSIBILITY SERVICE STATE DIAGNOSTIC REPORT

**Date:** March 2025
**Target Application:** CreatorAutomation (`com.creator.automation`)
**Android Platform Target:** Android 8.1 / API 27 Physical Test Device

---

## 1. ROOT CAUSE ANALYSIS OF `sig: acc_disabled`

### Observed Symptom
When triggering automated tasks (such as opening Google or Chrome), the state observation signature reported:
```text
sig: acc_disabled
```

### Root Cause Audit
Investigation of `ObservationProvider.kt` revealed a stale initialization bug:

```kotlin
// BEFORE (BUG):
class AccessibilityObservationProvider(
    private val service: AutomationAccessibilityService? = AutomationAccessibilityService.instance
) : ObservationProvider { ... }
```

In Kotlin, default parameter values assigned to `private val service` are evaluated **once at object instantiation time**. When `AccessibilityObservationProvider` was initialized at application startup (before `AutomationAccessibilityService` completed its binding callback `onServiceConnected()`), `service` captured `null`.

Even after `AutomationAccessibilityService` successfully connected and populated `AutomationAccessibilityService.instance = this`, the existing `AccessibilityObservationProvider` instance retained its stale `null` reference, forever returning `stateSignature = "acc_disabled"` on every `captureObservation()` call!

### Fixed Implementation
`AccessibilityObservationProvider` was updated to evaluate `AutomationAccessibilityService.instance` dynamically on every invocation using a functional supplier lambda:

```kotlin
// AFTER (FIXED):
class AccessibilityObservationProvider(
    private val serviceProvider: () -> AutomationAccessibilityService? = { AutomationAccessibilityService.instance }
) : ObservationProvider {
    override suspend fun captureObservation(): CurrentObservation {
        val service = serviceProvider()
        if (service == null) {
            return CurrentObservation(
                source = ObservationSource.ACCESSIBILITY,
                packageName = "unknown",
                stateSignature = "acc_disabled",
                summary = "AccessibilityService:\nconnected = false\nserviceInstance = null...",
                confidence = 0.0
            )
        }
        ...
    }
}
```

---

## 2. RUNTIME ACCESSIBILITYSERVICE STATE DIAGNOSTIC SCHEMA

`AutomationAccessibilityService` now instruments `getDiagnosticsSummary()`, providing a structured, privacy-safe diagnostic representation of actual platform accessibility health:

```text
AccessibilityService:
connected = true
serviceInstance = AutomationAccessibilityService
canRetrieveWindowContent = true
canPerformGestures = true
activeWindow = true
package = com.android.settings
lastAccessibilityEvent = TYPE_WINDOW_STATE_CHANGED (com.android.settings)
lastEventTime = 1741520000000
```

---

## 3. EVIDENCE & VERIFICATION

1. **AccessibilityManager.isEnabled:** Returns `true` when enabled in Android System Settings under Accessibility -> Creator Automation.
2. **Service Process Lifecycle:** `AutomationAccessibilityService.onServiceConnected()` sets `instance = this` and updates `_isServiceEnabled.value = true`.
3. **Window Content Retrieval:** `rootInActiveWindow` retrieves the active `AccessibilityNodeInfo` tree when foreground window transitions occur.
