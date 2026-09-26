# PHASE 3–5 DEEP HARDENING REPORT

**Device Specification Baseline:**
```text
TECNO IN6
Android 8.1
API 27
~4 GB RAM
~64 GB storage
```

---

## 1. INITIAL ARCHITECTURE AUDIT & ROOT CAUSES DISCOVERED

### Initial Audit Summary
A comprehensive audit of the execution call graph was conducted and documented in `docs/LAYER_3_5_EXECUTION_PATH_AUDIT.md`.

### Root Causes Identified
1. **Implicit State Representation:** The system previously relied on coarse boolean status fields (`success = true`), masking intermediate state transitions between target discovery, freshness evaluation, action dispatch, and state verification.
2. **Container-Unaware Scroll Fallback:** In previous implementations, gesture swipe fallbacks for scrolling defaulted to display metrics percentages (`0.75` -> `0.25` of screen height), which could accidentally swipe outside the actual scroll container or trigger system gesture bars.
3. **Target Freshness Drift:** Stale `AccessibilityNodeInfo` references could cause dispatch errors if screen state transitioned between candidate selection and execution.

---

## 2. HARDENING CHANGES BY LAYER

### Layer 3: Interaction Surface Discovery (`ActionResolver.kt`)
- Added `discoverInteractionSurfaces(snapshot)` extracting structured `InteractionSurface` objects bound directly to `UiSnapshot` timestamps and state signatures (`StateSignatureGenerator`).
- Enforced strict contextual evidence ranking (View ID > exact text > content description > role/properties).
- Maintained the read-only invariant (`actionDispatched = false`).

### Layer 4: Touch Execution (`DeviceActionExecutor.kt` & `LayerValidationController.kt`)
- Enforced pre-dispatch observation refresh and target freshness check against `RetainedTarget` state signatures before dispatching clicks.
- Resolved closest clickable ancestor in `performClickText()` if target node itself is non-clickable.
- Explicitly separated dispatch result (`SUCCESS`/`FAILED`) from goal verification (`CONFIRMED`/`NOT_CONFIRMED`).

### Layer 5: Scroll Execution (`DeviceActionExecutor.kt` & `LayerValidationController.kt`)
- Hardened gesture swipe fallback in `performScroll()` to derive start/end touch points strictly bounded within the observed scroll container (`boundsRect`) rather than raw display metrics percentages.
- Supported `ScrollRequest` with explicit scroll direction (`DOWN`/`UP`) and region identification.
- Classified results into `DISPATCH_FAILED`, `DISPATCH_SUCCESS_BUT_NO_CHANGE`, and `SCROLL_CONFIRMED`.

---

## 3. CORE STATE MODEL & FAILURE TAXONOMY

### ValidationState Enum (`AutomationModels.kt`)
`NO_OBSERVATION`, `OBSERVING`, `OBSERVATION_READY`, `TARGET_NOT_FOUND`, `TARGET_AMBIGUOUS`, `TARGET_FOUND`, `TARGET_SELECTED`, `TARGET_STALE`, `ACTION_READY`, `ACTION_DISPATCHING`, `ACTION_DISPATCHED`, `VERIFYING`, `CONFIRMED`, `NOT_CONFIRMED`, `FAILED`, `CANCELLED`.

### ValidationFailureReason Taxonomy (`AutomationModels.kt`)
`NONE`, `SERVICE_UNAVAILABLE`, `ROOT_UNAVAILABLE`, `OBSERVATION_STALE`, `OBSERVATION_TIMEOUT`, `TARGET_NOT_FOUND`, `TARGET_AMBIGUOUS`, `TARGET_STALE`, `TARGET_DISABLED`, `TARGET_NOT_ACTIONABLE`, `INVALID_BOUNDS`, `CLICK_DISPATCH_FAILED`, `CLICK_NOT_CONFIRMED`, `SCROLL_REGION_NOT_FOUND`, `SCROLL_REGION_AMBIGUOUS`, `SCROLL_REGION_STALE`, `SCROLL_ACTION_UNAVAILABLE`, `SCROLL_DISPATCH_FAILED`, `SCROLL_NOT_CONFIRMED`, `OVERLAY_INTERFERENCE`, `CANCELLED`, `UNKNOWN`.

---

## 4. VERIFICATION & TEST SUMMARY

- **Unit Tests:** 185 unit tests passing 100% cleanly (`./gradlew test`), including `LayerValidationTest.kt` verifying state transitions, failure reasons, and interaction surface extraction.
- **Build Verification:** Debug APK compiles cleanly (`./gradlew assembleDebug`).
- **Physical Verification Status:** Explicitly marked **PENDING USER PHYSICAL TESTING** on physical TECNO IN6 device.
