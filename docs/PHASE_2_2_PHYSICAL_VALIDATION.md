# PHASE 2.2 PHYSICAL DEVICE VALIDATION

## Overview
Phase 2.2 establishes the physical Android device as the single source of truth for runtime hardware capabilities, sensor/actuator inventories, permissions, and UI automation verification.

---

## Architectural Principles
1. **Physical Discovery over Hardcoding**: Zero assumption of hardware capability. All capabilities, sensors, and actuators are dynamically enumerated at runtime via Android system services.
2. **Non-Collapsing States**: States (`PRESENT`, `AVAILABLE`, `PERMISSION_GRANTED`, `USABLE`, `UNSUPPORTED`, `BLOCKED`, `UNKNOWN`) are tracked distinctly without loss of fidelity.
3. **Safety First**: Destructive or side-effect actions are flagged `requiresUserApproval` and assigned status `BLOCKED_REQUIRES_USER_APPROVAL` when unapproved.
4. **API 27 Compatibility**: Local storage reports written safely inside application sandbox directories (`getExternalFilesDir("physical_validation")`).

---

## Execution Flow
```text
Device Discovery Scanner
        ↓
Device Profile & Inventory Generation
        ↓
Physical Test Suite Execution
   (Observe -> Resolve -> Act -> Wait -> Observe -> Verify -> Record)
        ↓
Export Reports (JSON & Markdown)
        ↓
Jetpack Compose Diagnostic UI Display
```
