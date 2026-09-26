# LAYER 5 SCROLL HARDENING

**Target Device:** TECNO IN6 / Android 8.1.0 / API 27 / ~4 GB RAM / ~64 GB Storage
**Objective:** Document Layer 5 Scroll execution hardening in `DeviceActionExecutor.kt` and `LayerValidationController.kt`.

---

## 1. SCROLL CONTAINER SELECTION & RANKING

- Discovers all scrollable containers in `UiSnapshot.scrollableNodes`.
- Allows explicit container selection in `OverlayMenuView.L5_SCROLL`.
- Binds `ScrollRequest` (`regionIdentity`, `observationId`, `direction`, `amount`: `SMALL`/`MEDIUM`/`LARGE`).

---

## 2. CONTAINER-BOUNDED GESTURE GEOMETRY

When Tier 1 `ACTION_SCROLL_FORWARD` / `BACKWARD` is unavailable or fails, Tier 2 gesture swipe fallback calculates touch start and end coordinates strictly inside the observed scroll container bounds (`boundsRect`):
- Top Y = `boundsRect.top + (boundsRect.height() * 0.2f)`
- Bottom Y = `boundsRect.bottom - (boundsRect.height() * 0.2f)`
- Center X = `boundsRect.centerX()`
Leaves 20% safe margins from top/bottom container edges, avoiding system navigation bars, edge gestures, and floating overlays.

---

## 3. VERIFICATION & MOVEMENT CONFIRMATION

- Captures fresh post-scroll observation after a 500ms transition delay.
- Compares before and after visible text sets, node sets, and `StateSignature`.
- Classifies result into:
  - `SCROLL_CONFIRMED`: Dispatch succeeded and state signature or visible text set changed.
  - `DISPATCH_ACCEPTED_BUT_NOT_CONFIRMED`: Dispatch returned true but no state change detected.
  - `SCROLL_FAILED`: Both accessibility action and gesture fallback failed.
  - `NO_SCROLL_AVAILABLE`: Selected container is at the boundary and cannot scroll further.
