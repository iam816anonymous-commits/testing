# LAYER 4 TOUCH HARDENING

**Target Device:** TECNO IN6 / Android 8.1.0 / API 27 / ~4 GB RAM / ~64 GB Storage
**Objective:** Document Layer 4 Touch execution hardening in `DeviceActionExecutor.kt` and `LayerValidationController.kt`.

---

## 1. PRE-DISPATCH FRESHNESS & SAFETY AUDIT

Before dispatching `ActionType.CLICK_TEXT`:
1. Re-observes active window immediately prior to target resolution to obtain fresh `UiSnapshot`.
2. Validates target freshness against retained `StateSignature`.
3. Verifies target `isEnabled == true` and `isVisibleToUser == true`.
4. If target node itself is non-clickable (`isClickable == false`), climbs the parent tree to resolve the closest clickable ancestor node.

---

## 2. SINGLE DISPATCH & DEBOUNCING

- **Single Dispatch Invariant:** Tapping `TEST CLICK` dispatches at most ONE physical action attempt (`ACTION_CLICK` or bounds-derived gesture tap fallback).
- **Debouncing:** Disables test button during `ACTION_DISPATCHING` state to prevent duplicate dispatch races.
- **No Blind Retries:** Never retries automatically upon verification timeout.

---

## 3. SEPARATION OF DISPATCH AND VERIFICATION

- **Dispatch Status (`ActionResultStatus`):** `SUCCESS` or `FAILED` (indicates whether Android accepted the accessibility click/gesture).
- **Verification Status (`GoalResult` / `LayerValidationTrace`):** `CONFIRMED` or `NOT_CONFIRMED` (determined by post-action state signature comparison and visible UI node set transitions).
