# LAYER 3 TARGET DISCOVERY HARDENING

**Target Device:** TECNO IN6 / Android 8.1.0 / API 27 / ~4 GB RAM / ~64 GB Storage
**Objective:** Document the read-only, generic target discovery engine in `ActionResolver.kt` and `LayerValidationController.kt`.

---

## 1. READ-ONLY INVARIANT & DYNAMIC DISCOVERY

- **No Action Dispatches:** Layer 3 target discovery methods (`discoverTarget`, `discoverInteractionSurfaces`, `autoDetectScreen`, `reportMechanismAvailability`) strictly return `actionDispatched = false`.
- **Dynamic Surface Discovery:** Discovers interaction surfaces directly from the current foreground application (`YouTube`, `Chrome`, `Settings`). Re-evaluates automatically when application transitions occur.
- **No Hardcoded App Rules:** Eliminates hardcoded "Search" strings or package-specific logic.

---

## 2. STALE TARGET DETECTION (`TARGET_STALE`)

Before executing Layer 4 or 5 actions against a retained target:
1. Re-captures fresh `UiSnapshot`.
2. Compares active `StateSignature` with retention signature.
3. If signature shifted and the candidate node is no longer visible/present, updates target status to `STALE` and blocks dispatch with `ValidationFailureReason.TARGET_STALE`.

---

## 3. AMBIGUITY HANDLING

When multiple candidates match with equivalent confidence (e.g. multiple "More options" buttons):
- Returns status `TargetResolutionStatus.AMBIGUOUS`.
- Supplies candidate list in `TargetResolutionResult`.
- Safely blocks execution rather than guessing an arbitrary target.
