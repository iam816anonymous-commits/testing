# PHASE 3E.7 MECHANISM DECISION MATRIX

| Operation | Primary Mechanism | Fallback Mechanism | Target Resolution | Verification Strategy |
| :--- | :--- | :--- | :--- | :--- |
| **Touch / Click** | Accessibility `ACTION_CLICK` on node or clickable parent | Bounds-derived `dispatchGestureTap(centerX, centerY)` | 4-Tier Priority Cascade (`ActionResolver.resolveTargetWithAmbiguity`) | Post-click UI snapshot comparison (`beforeStateSig != afterStateSig`) |
| **Scroll Down / Up** | Accessibility `ACTION_SCROLL_FORWARD` / `ACTION_SCROLL_BACKWARD` | Dynamic `GestureDescription` swipe on scrollable region bounds | `snapshot.scrollableNodes.firstOrNull()` or region bounds | UI snapshot comparison + state signature shift |
| **Type Text** | Accessibility `ACTION_SET_TEXT` with `ACTION_FOCUS` | Editable field focus + IME injection | `ActionResolver.resolveEditableTarget` | Post-input snapshot check verifying typed text string appears in node |
| **Submit Input** | Container search button (magnifying glass icon in input bar) | Semantic Search/Go/Submit button or gesture tap fallback | Multi-tier submit candidate matching (`performSubmitInput`) | Post-submit UI snapshot transition check |
| **Global HOME** | `GLOBAL_ACTION_HOME` via `AutomationAccessibilityService` | N/A (Native Global Action) | System Global | Post-action active package check verifying launcher active |
| **Global BACK** | `GLOBAL_ACTION_BACK` via `AutomationAccessibilityService` | N/A (Native Global Action) | System Global | Post-action UI state signature comparison |
| **Global RECENTS** | `GLOBAL_ACTION_RECENTS` via `AutomationAccessibilityService` | N/A (Native Global Action) | System Global | Post-action UI state signature comparison |
| **Read Visible UI** | `ActionType.READ_VISIBLE_UI` via `DeviceActionExecutor` | Screen observation capture | Active UI tree snapshot (`UiSnapshot`) | Payload check (`OBSERVATION_AVAILABLE: Captured N UI nodes`) |
| **Flashlight Torch** | Native `CameraManager.setTorchMode` (API 23+) | N/A | `HardwareActuatorRegistry.findActuatorForGoal` | `TorchCallback.onTorchModeChanged` state match |
| **Visual Perception** | MediaProjection capture session | Visual region detection (`VisualRegionDetector`) + OCR | Perception fusion engine (`PerceptionFusionEngine`) | Visual change signature comparison (`compareSignatures`) |
