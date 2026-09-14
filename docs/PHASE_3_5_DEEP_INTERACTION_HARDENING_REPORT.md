# PHASE 3.5 FINAL INTERACTION HARDENING REPORT

**Target Device:** TECNO IN6 / Android 8.1 / API 27 / ~4 GB RAM / ~64 GB Storage
**Audit Statement:** NO PHYSICAL SUCCESS CLAIM WAS MADE WITHOUT DEVICE EVIDENCE.

---

## 1. ROOT CAUSES AND FIXES

### Problem 1: "Run Safe Physical Tests" Navigated to Home
- **Root Cause:** `PhysicalTestRegistry.buildSafeValidationSuite()` included `TEST-PHY-001` which dispatched `AndroidAutomationCompat.performGlobalHome()`.
- **Fix:** Re-categorized `TEST-PHY-001` as `NAVIGATION` and converted it to a non-destructive capability audit. Enforced `ExecutionPolicy.READ_ONLY` so tests preserve the foreground app.

### Problem 2: Floating Overlay Was Fixed
- **Root Cause:** Lacked gesture movement tracking.
- **Fix:** Implemented pointer tracking using `touchSlop` in `AutomationAccessibilityService.kt`. Dragging moves the icon within screen bounds.

### Problem 3: Layer 3 Hardcoded "Search"
- **Root Cause:** Hardcoded static search text.
- **Fix:** Rebuilt `ActionResolver.kt` to extract dynamic `InteractionSurface` items from live UI snapshots across `YouTube`, `Chrome`, and `Settings`.

### Problem 4: Intermittent Layer 5 Scrolling
- **Root Cause:** Gesture swipe fallbacks used screen height percentages (`0.75` -> `0.25`).
- **Fix:** Bound swipe start/end coordinates strictly within the observed scroll container's `boundsRect` (leaving 20% safe margins).

---

## 2. ARCHITECTURE SUMMARY

- **Layer 3:** Generic `InteractionSurface` model with dynamic role inference (`BUTTON`, `EDITABLE`, `SCROLL_CONTAINER`, `TEXT`).
- **Layer 4:** Target freshness check (`TARGET_STALE`), ancestor clickable node walking, single-click dispatch, debouncing.
- **Layer 5:** Container-bounded gesture swipe geometry, movement confirmation via text set and signature diffing.
- **Overlay:** Non-polluting floating control surface with touch slop dragging and menu navigation.

---

## 3. VERIFICATION & UNIT TESTS

- **Unit Tests:** 185 tests passing 100% cleanly (`./gradlew test`).
- **Build Verification:** Debug APK compiles cleanly (`./gradlew assembleDebug`).
- **Physical Verification Status:** Pending manual device verification on TECNO IN6.
