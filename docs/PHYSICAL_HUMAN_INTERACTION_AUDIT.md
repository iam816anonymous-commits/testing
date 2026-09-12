# PHYSICAL HUMAN INTERACTION AUDIT & MECHANISM MAP

**Target Device:** TECNO IN6 (Android 8.1 / API 27 baseline, ~4 GB RAM, ~64 GB Storage)
**Execution Contract:** Generic, App-Agnostic, Accessibility-First, Low-RAM, Zero LLM Dependency

---

## 1. MECHANISM MAP SUMMARY

| Interaction Category | Primary Mechanism | Fallback Mechanism | Target Resolver | Verification Strategy |
| :--- | :--- | :--- | :--- | :--- |
| **Touch / Click** | Accessibility `ACTION_CLICK` on node or clickable parent | Bounds-derived `dispatchGestureTap` at bounds center | 4-Tier Cascade (`ActionResolver.resolveTargetWithAmbiguity`) | Post-click UI snapshot comparison (`beforeStateSig != afterStateSig`) |
| **Scroll Down / Up** | Accessibility `ACTION_SCROLL_FORWARD` / `ACTION_SCROLL_BACKWARD` | Dynamic `GestureDescription` swipe on scroll-region bounds | `snapshot.scrollableNodes.firstOrNull()` or region bounds | UI snapshot comparison + state signature shift |
| **Typing / Text Input** | Accessibility `ACTION_SET_TEXT` with `ACTION_FOCUS` | Editable field focus + IME injection | `ActionResolver.resolveEditableTarget` | Post-input snapshot check verifying typed text appears in node |
| **Search / Submit** | Focused container submit control (Search icon in input bar) | Semantic Search/Go/Submit button or gesture tap fallback | Multi-tier submit candidate matching (`performSubmitInput`) | Post-submit UI snapshot transition check |
| **Visual Perception** | MediaProjection capture session | Visual region detection (`VisualRegionDetector`) + OCR | Perception fusion engine (`PerceptionFusionEngine`) | Visual change signature comparison (`compareSignatures`) |
| **Quick Control: HOME** | `GLOBAL_ACTION_HOME` via `AutomationAccessibilityService` | N/A (Native Android Global Action) | System Global | Post-action active package check verifying launcher active |
| **Quick Control: BACK** | `GLOBAL_ACTION_BACK` via `AutomationAccessibilityService` | N/A (Native Android Global Action) | System Global | Post-action UI state signature comparison |
| **Quick Control: RECENTS** | `GLOBAL_ACTION_RECENTS` via `AutomationAccessibilityService` | N/A (Native Android Global Action) | System Global | Post-action UI state signature comparison |
| **Quick Control: READ SCREEN** | `ActionType.READ_VISIBLE_UI` via `DeviceActionExecutor` | Screen observation capture | Active UI tree snapshot (`UiSnapshot`) | Payload check (`OBSERVATION_AVAILABLE: Captured N UI nodes`) |

---

## 2. DETAILED MECHANISM AUDIT

### A. Touch / Click Execution
- **Primary Mechanism**: Resolves `UiNodeInfo` via 4-tier cascade (View ID > Exact Text > Content Description > Partial Text). Traverses up parent hierarchy until `node.isClickable` is true, then executes `node.performAction(ACTION_CLICK)`.
- **Fallback Mechanism**: When `ACTION_CLICK` returns `false` or no clickable parent exists, obtains actual target screen bounds `Rect(left, top, right, bottom)` and dispatches `dispatchGestureTap(centerX, centerY)`.
- **Blind Touch Protection**: Center-screen taps at fixed `(360, 640)` coordinates are strictly forbidden and eliminated. Every touch requires actual resolved node or visual bounds.
- **Verification**: Evaluates post-click snapshot to verify `beforeStateSig != afterStateSig` or package change.

### B. Scroll Execution
- **Primary Mechanism**: Resolves scrollable container via `snapshot.scrollableNodes.firstOrNull()`. Invokes `AccessibilityNodeInfo.ACTION_SCROLL_FORWARD` (down) or `ACTION_SCROLL_BACKWARD` (up).
- **Fallback Mechanism**: When Accessibility scroll fails or container lacks scroll action, calculates dynamic swipe stroke on container bounds (`startY = height * 0.7f` to `endY = height * 0.3f`) and dispatches `service.dispatchGesture()`.
- **Verification**: Re-observes active window post-scroll; fails with `ExecutionReason.STUCK` if `beforeStateSig == afterStateSig`.

### C. Typing Execution
- **Primary Mechanism**: Resolves editable field via `ActionResolver.resolveEditableTarget`. Focuses field via `ACTION_FOCUS`, then injects text via `ACTION_SET_TEXT`.
- **Parameter Extraction**: `TaskResolver.generateGenericWorkflowForTask` strips filler prefixes ("search for", "look about") to ensure clean parameter injection.
- **Verification**: Re-observes active window after 300ms delay and confirms that an editable node in `afterSnapshot.editableNodes` contains the typed input string.

### D. Search / Submit Execution
- **Primary Mechanism**: Inspects focused editable field container for neighbor clickable search controls (e.g. magnifying glass icon in input bar).
- **Secondary Mechanism**: Matches semantic submit controls ("Search", "Go", "Enter", "Submit") with ambiguity safety (blocks if multiple distinct controls match).
- **Verification**: Verifies UI state transition post-submission.

### E. Visual Perception & Fusion
- **Primary Mechanism**: Captures frame via MediaProjection `ImageReader` at downsampled $360 \times 640$ resolution.
- **Processing**: Computes MD5 perceptual visual signature and average luminance via `VisualStateAnalyzer`. Detects edge gradients via `VisualRegionDetector`.
- **Fusion**: Fuses accessibility nodes, OCR text, and visual regions in `PerceptionFusionEngine` into `ScreenObservation` containing `UnifiedElement`s.
- **Target Resolution**: `ActionResolver.resolveFusedTarget` provides fallback target bounds when Accessibility tree nodes are missing.

---

## 3. PHYSICAL VALIDATION VERIFICATION INVARIANTS

1. **Truthful Verification**: `ActionResult.SUCCESS != GoalResult.CONFIRMED`. Every task completion requires fresh post-action observation evidence.
2. **Explicit Timeout Sources**: Timeouts are explicitly classified (`TARGET_RESOLUTION`, `ACTION_DISPATCH`, `WAIT_CONDITION`, `OBSERVATION`, `VERIFICATION`, `RUNTIME`) in `WaitEngine.kt`.
3. **No App-Specific Rules**: Zero hardcoded Chrome/YouTube package checks or coordinate maps.
4. **Idempotent Cancellation**: STOP button halts active execution, clears `AutomationOverlayState`, persists `CANCELLED` status, and auto-resumes runtime to `IDLE` upon new task submission.
