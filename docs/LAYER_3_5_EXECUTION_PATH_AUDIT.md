# LAYER 3–5 EXECUTION PATH AUDIT

**Target Device Architecture:** TECNO IN6 / Android 8.1.0 / API 27 / ~4 GB RAM / ~64 GB Storage
**Audit Purpose:** Trace end-to-end call graph, thread transitions, lifecycle boundaries, state freshness, error handling, mechanism resolution, and goal verification across Layers 3, 4, and 5.

---

## 1. END-TO-END CALL GRAPH TRACE

```text
Floating Overlay Console (AutomationAccessibilityService)
        │
        ├── User Taps [ DISCOVER TARGET ] / [ TEST CLICK ] / [ TEST SCROLL ] (Main Looper)
        ▼
LayerValidationController
        │
        ├── 1. Freshness Audit: ActionResolver.captureSnapshot() -> UiSnapshot + StateSignature
        ├── 2. Target/Region Resolution: discoverTarget() / resolveTargetWithAmbiguity()
        ├── 3. Execution Dispatch: DeviceActionExecutor.executeAndAudit() [CoroutineScope(IO)]
        ▼
DeviceActionExecutor
        │
        ├── 1. Precondition Check: checkPreconditions(snapshot)
        ├── 2. Pre-Dispatch Target Re-Observation (Freshness Invariant)
        ├── 3. Mechanism Selection:
        │       ├── Tier 1: AccessibilityNodeInfo.performAction(ACTION_CLICK / ACTION_SCROLL_FORWARD)
        │       └── Tier 2: Gesture Fallback: dispatchGestureTap() / AndroidAutomationCompat.dispatchSwipe()
        ▼
AutomationAccessibilityService (Main Thread Gesture / Accessibility Engine)
        │
        ├── Android Accessibility Framework -> WindowManager / Event Dispatcher
        ▼
WaitEngine & Post-Action Observation
        │
        ├── Delay (400ms–500ms) for UI transition
        ├── Fresh ActionResolver.captureSnapshot()
        └── State Signature Comparison (beforeSig vs afterSig)
        ▼
GoalVerifier & LayerValidationTrace
        │
        ├── Classification: CONFIRMED vs NOT_CONFIRMED vs FAILED
        └── Emit LayerValidationTrace to StateFlow -> Floating Overlay UI Update
```

---

## 2. STAGE-BY-STAGE EXECUTION AUDIT

### Stage 1: Observation Capture & Snapshot Creation
- **Primary Component:** `ActionResolver.captureSnapshot(root, fallbackPackageName)`
- **Input:** Raw `AccessibilityNodeInfo` root from `AutomationAccessibilityService.getRootNode()`.
- **Output:** `UiSnapshot` containing package name, node count, visible text list, view ID list, clickable nodes, scrollable nodes, editable nodes, and depth-limited node hierarchy (`MAX_TRAVERSAL_DEPTH = 30`).
- **Threading & Lifecycles:** Executed on calling thread (Service scope or IO dispatcher). Uses `rootInActiveWindow` on API 27.
- **Zero Perception Pollution Invariant:** When `fallbackPackageName != "com.creator.automation"`, nodes belonging to package `"com.creator.automation"` are strictly ignored during recursion, ensuring diagnostic overlay elements do not pollute foreground target resolution.

### Stage 2: Layer 3 Target & Surface Discovery
- **Primary Component:** `ActionResolver.discoverTarget(snapshot, request)` & `generateInteractionMap(snapshot)`
- **Input:** Active `UiSnapshot` and `TargetRequest` (query text / view ID / content description / role).
- **Output:** `TargetResolutionResult` (`FOUND_UNIQUE`, `AMBIGUOUS`, `NOT_FOUND`, `NOT_ACTIONABLE`).
- **Ranking Rules:**
  1. View ID match (`1.0` confidence).
  2. Exact visible text match (`0.95` confidence).
  3. Content description match (`0.90` confidence).
  4. Accessibility properties / partial match (`0.75` confidence).
  5. Class/Role name match (`0.40` confidence) -> **Always classified as AMBIGUOUS to prevent false matches**.
- **Read-Only Invariant:** Returns `actionDispatched = false` for all discovery operations.

### Stage 3: Target Retention & Freshness Verification
- **Primary Component:** `LayerValidationController.selectAndRetainTarget()` & `evaluateTargetFreshness()`
- **Mechanism:** Stores candidate alongside snapshot `StateSignatureGenerator.generateSignature(snapshot)`.
- **Freshness Invariant:** Before executing Layer 4 Touch or Layer 5 Scroll, `evaluateTargetFreshness()` compares current screen signature with retention signature. If signature shifted, attempts re-resolution on fresh snapshot; if target disappeared, sets status to `STALE` and blocks dispatch with `ValidationFailureReason.TARGET_STALE`.

### Stage 4: Layer 4 Touch (Click) Execution
- **Primary Component:** `DeviceActionExecutor.performClickText()`
- **Execution Hierarchy:**
  1. **Pre-Dispatch Re-Observation:** Re-fetches root node immediately prior to dispatch to prevent operating on detached/stale `AccessibilityNodeInfo` handles.
  2. **Clickable Ancestor Resolution:** If target node itself is not clickable (`isClickable == false`), climbs parent tree up to root to find closest clickable ancestor.
  3. **Tier 1 (Accessibility Action):** `targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)`.
  4. **Tier 2 (Gesture Fallback):** If Tier 1 fails/unsupported and bounds exist (`width > 0 && height > 0`), calls `AutomationAccessibilityService.dispatchGestureTap(centerX, centerY)`.
- **Dispatch vs Verification Separation:** `ActionResultStatus` records whether Android accepted dispatch (`SUCCESS`/`FAILED`), while verification compares pre/post state signatures to assign `CONFIRMED` vs `NOT_CONFIRMED`.

### Stage 5: Layer 5 Scroll Execution
- **Primary Component:** `DeviceActionExecutor.performScroll()`
- **Execution Hierarchy:**
  1. **Container Surface Resolution:** Identifies `scrollableNodes` in fresh snapshot. If empty, returns `SCROLL_REGION_NOT_FOUND`.
  2. **Tier 1 (Accessibility Scroll):** Dispatches `ACTION_SCROLL_FORWARD` (for DOWN) or `ACTION_SCROLL_BACKWARD` (for UP) on selected container handle.
  3. **Tier 2 (Gesture Swipe Fallback):** Dispatches region-bounded gesture swipe (`AndroidAutomationCompat.dispatchSwipe()`) derived strictly inside the container bounds (padding 20% top/bottom/edges) rather than screen-center coordinates.
- **Verification:** Waits 500ms, captures fresh post-scroll observation, and compares visible text sets and state signatures. Confirms `CONFIRMED` only when state signature or visible text set changes.

---

## 3. AUDIT FINDINGS & REQUIRED HARDENING

1. **Explicit State Machine Gaps:** `LayerValidationController` currently tracks layer statuses as plain strings rather than a formal `ValidationState` enum and `ValidationFailureReason` taxonomy.
2. **Scroll Gesture Bounds Invariance:** `performScroll()` gesture fallback currently uses display metrics screen height (0.75 -> 0.25) when `scrollableNode` is null. It must be hardened so gesture swipes are strictly bounded within the specific scroll container's observed screen bounds.
3. **Overlay Focus Interlock:** The overlay window flags in `AutomationAccessibilityService` must dynamically manage `FLAG_NOT_FOCUSABLE` to ensure touch inputs and text typing in `OverlayMenuView.L3_TARGET` operate reliably across external applications on API 27.
