# CreatorAutomation — Phase 3 Layer 3 Stabilization Audit

## Overview

This audit establishes the pre-implementation baseline for **Layer 3: Target Discovery & Read-Only Diagnostic Harness** on the target TECNO IN6 hardware (Android 8.1 / API 27).

---

## Codebase Audit Findings

1. **AutomationAccessibilityService (`AutomationAccessibilityService.kt`)**
   - Active AccessibilityService binding providing live `rootInActiveWindow` access.
   - `CrossAppObservationState` tracks foreground package, node statistics, and event counts.
   - Diagnostic floating overlay attached via `TYPE_ACCESSIBILITY_OVERLAY` with `FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE or FLAG_LAYOUT_IN_SCREEN or FLAG_LAYOUT_NO_LIMITS`.
   - Ignored accessibility events with `packageName == "com.creator.automation"` to break layout update feedback loops.

2. **Screen Snapshot Capture & Perception (`ActionResolver.kt`, `AutomationModels.kt`)**
   - Depth-limited safe recursive traversal (`MAX_TRAVERSAL_DEPTH = 30`).
   - `UiSnapshot` records total node count, text count, clickable count, editable count, scrollable count, focused count, traversal duration, and `isRootAvailable`.
   - `ActionResolver.captureSnapshot()` excludes `com.creator.automation` overlay nodes when observing external applications (zero-pollution guarantee).

3. **Layer 3 Target Resolution (`ActionResolver.kt`)**
   - Priority hierarchy: 1. View ID -> 2. Exact Visible Text -> 3. Content Description -> 4. Role/Class -> 5. Partial Text.
   - Read-only `discoverTarget()` evaluates requests without dispatching physical touch actions (`actionDispatched = false`).
   - Ambiguity detection returns `AMBIGUOUS` when multiple candidates match equal criteria.

4. **Diagnostic & Control Center UI (`MainActivity.kt`)**
   - Displays Layer 1 Accessibility diagnostics, Layer 2 screen observation, Layer 2 cross-app observation controls, and Layer 3 read-only target discovery inputs.

---

## Upgrade Requirements for Layer 3 Stabilization

1. **Safe Candidate Ranking**: Class or role alone (e.g. `android.widget.TextView`) must NOT produce a unique match.
2. **Interaction Map**: Generate structured list of interactable UI elements on current screen.
3. **Mechanism Availability Report**: Report available execution mechanisms (Accessibility click, parent click, gesture fallback, focus, text injection) without executing them.
4. **Auto-Detect Mode**: Report screen capabilities, interactive element counts, and visual fallback state on demand.
5. **Floating Diagnostic Panel & Layer Selector**: Expand overlay float view with Layer 1/2/3 diagnostics, Layer 4-9 locked status, Auto Detect, and Refresh.
6. **Strict Read-Only Invariant**: Enforce `actionDispatched = false` across all Layer 3 target discovery operations.
