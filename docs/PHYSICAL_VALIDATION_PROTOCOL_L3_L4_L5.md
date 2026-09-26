# PHYSICAL VALIDATION PROTOCOL (LAYERS 3, 4 & 5)

**Target Device:** TECNO IN6 / Android 8.1.0 / API 27 / ~4 GB RAM / ~64 GB Storage

---

## 1. PRE-TEST CHECKLIST

1. Ensure `AutomationAccessibilityService` is active on TECNO IN6.
2. Verify floating control button (`◉ AGENT CONSOLE`) appears over foreground applications.
3. Confirm dragging `◉ AGENT CONSOLE` moves the button across the screen smoothly.

---

## 2. LAYER 3 TARGET DISCOVERY PROCEDURE

1. Open target application (`YouTube`, `Chrome`, `Settings`).
2. Tap `◉ AGENT CONSOLE` -> Select `[ L3 ] TARGET DISCOVERY`.
3. Inspect dynamically listed `InteractionSurface` items extracted from current screen.
4. Verify `actionDispatched = false` (no physical clicks or touches occur during discovery).
5. Tap `USE TARGET -> GO TO L4 TOUCH`.

---

## 3. LAYER 4 TOUCH VALIDATION PROCEDURE

1. Ensure target status displays `[READY]`.
2. Tap `[ TEST CLICK ]`.
3. Verify single click attempt dispatches via production `DeviceActionExecutor`.
4. Observe whether target application responds (UI state transition or keyboard open).
5. Inspect overlay trace output (`Dispatch: SUCCESS/FAILED`, `Verify: CONFIRMED/NOT_CONFIRMED`).

---

## 4. LAYER 5 SCROLL VALIDATION PROCEDURE

1. Select `[ L5 ] SCROLL EXECUTION` from main menu.
2. Select scroll direction (`DOWN` or `UP`).
3. Tap `[ TEST SCROLL ]`.
4. Verify single container-bounded gesture or accessibility scroll dispatches inside scroll container.
5. Inspect overlay trace output (`Dispatch: SUCCESS`, `Verify: CONFIRMED`).
