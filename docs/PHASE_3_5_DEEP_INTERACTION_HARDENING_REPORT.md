# PHASE 3.5 DEEP INTERACTION HARDENING REPORT

**Device Specification Baseline:**
```text
TECNO IN6
Android 8.1
API 27
~4 GB RAM
~64 GB storage
```

---

## 1. ROOT CAUSE ANALYSES & RESOLUTIONS

### Problem 1: Safe Physical Tests Unexpectedly Navigated Device to Home
- **Problem:** Tapping "Run Safe Physical Tests" displaced the user's active application and navigated the phone to the Android Home launcher.
- **Root Cause:** `PhysicalTestRegistry.buildSafeValidationSuite()` included `TEST-PHY-001` (`GO_HOME`) which directly invoked `AndroidAutomationCompat.performGlobalHome()`.
- **Change & Fix:** Re-categorized `TEST-PHY-001` and `TEST-PHY-002` under `PhysicalTestCategory.NAVIGATION`. Made `TEST-PHY-001` audit accessibility capability non-destructively without executing global Home navigation. Enforced `ExecutionPolicy` (`READ_ONLY`, `INTERACTION`, `NAVIGATION`) so non-navigational test runs never displace the foreground application.

### Problem 2: Floating Overlay Was Not Movable/Draggable
- **Problem:** The floating overlay icon was fixed on screen and could not be dragged by the user.
- **Root Cause:** The overlay view lacked pointer movement tracking and distance threshold evaluations.
- **Change & Fix:** Implemented a touch gesture state machine in `AutomationAccessibilityService.kt` (`ACTION_DOWN` -> `ACTION_MOVE` with `touchSlop` distance threshold -> `ACTION_UP`). Small taps toggle menu expansion; drags smoothly move the overlay icon using `WindowManager.updateViewLayout` with position clamping inside screen bounds.

### Problem 3: Layer 3 Target Discovery Hardcoded "Search"
- **Problem:** Layer 3 target discovery in the overlay relied on a hardcoded "Search" text string.
- **Root Cause:** The overlay static view fallback rendered "Search" rather than pulling live interaction surfaces from the active `UiSnapshot`.
- **Change & Fix:** Updated `ActionResolver.kt` and `AutomationAccessibilityService.kt` to extract dynamic `InteractionSurface` items from the current foreground application (`YouTube`, `Chrome`, `Settings`). Surfaces render dynamically in `OverlayMenuView.L3_TARGET`.

### Problem 4: Layer 5 Scroll Execution Was Intermittent
- **Problem:** Layer 5 scrolling failed or produced unpredictable movements across applications.
- **Root Cause:** Gesture swipe fallbacks used generic display metrics percentages (`0.75` -> `0.25` screen height) without respecting container bounds.
- **Change & Fix:** Updated `DeviceActionExecutor.performScroll()` to derive start/end touch coordinates strictly within the selected scroll container's observed `boundsRect` (leaving 20% safe margins from top/bottom/edges).

---

## 2. HARDENING SUMMARY BY LAYER

| Component | Root Cause | Fix Applied | Verification Method |
|---|---|---|---|
| **Safe Test Runner** | `TEST-PHY-001` executed `performGlobalHome()` | Made capability check non-destructive & categorized as `NAVIGATION` | Unit test in `PhysicalTestRunnerTest.kt` |
| **Floating Overlay** | Static click listener only | Implemented touch slop gesture tracking for drag & tap | Unit test in `LayerValidationTest.kt` |
| **Layer 3 Discovery** | Hardcoded "Search" text | Extracts dynamic `InteractionSurface` items from live snapshot | Unit test in `LayerValidationTest.kt` |
| **Layer 4 Touch** | Pre-dispatch signature drift | Re-observes active window & checks target freshness | Unit test in `LayerValidationTest.kt` |
| **Layer 5 Scroll** | Display percentage swipe fallback | Container-bounded gesture coordinates with 20% safe padding | Unit test in `LayerValidationTest.kt` |

---

## 3. VERIFICATION STATUS

- **Unit Tests:** 185 unit tests passing 100% cleanly (`./gradlew test`).
- **Build Verification:** Debug APK compiles cleanly without errors (`./gradlew assembleDebug`).
- **Physical Validation:** Explicitly marked **PENDING USER PHYSICAL TESTING** on physical TECNO IN6 device.
