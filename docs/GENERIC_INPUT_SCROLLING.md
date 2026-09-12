# Generic Android Text Input and Scrolling Pipeline

## 1. Executive Overview
This document specifies the generic, app-agnostic text input and scrolling execution pipelines in `CreatorAutomation`.
Both pipelines strictly adhere to the observe-act-observe-verify cycle without relying on app-specific hardcoded selectors, fixed coordinates, or arbitrary static sleeps.

---

## 2. Generic Text Input Pipeline

### Execution Pipeline (5 Stages)
1. **Target Discovery & Freshness Verification:**
   - Resolve editable target node via `ActionResolver.resolveEditableTarget()` on a freshly observed `UiSnapshot`.
   - Verify node is enabled (`isEnabled == true`).
2. **Focus & Click Activation:**
   - If `!node.isFocused`, dispatch `ACTION_FOCUS` and `ACTION_CLICK` to activate IME focus.
3. **Text Injection via `ACTION_SET_TEXT`:**
   - Construct `Bundle` with `ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE`.
   - Execute `node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)`.
4. **Post-Injection Observation:**
   - Capture fresh `UiSnapshot` after 300ms delay.
5. **Text Verification & Normalization:**
   - Verify that any editable node in the active UI tree contains the normalized expected input string or password placeholder (`[REDACTED]`).
   - If initial injection is unverified, attempt re-focused retry.

---

## 3. Generic Scrolling & Gesture Pipeline

### Mechanism Priority (3 Tiers)
1. **Tier 1: Accessibility Scroll Action (`ACTION_SCROLL_FORWARD` / `ACTION_SCROLL_BACKWARD`)**
   - Dispatched directly to the first available `isScrollable = true` node.
2. **Tier 2: Scrollable Node Re-Resolution**
   - If initial scrollable target was stale, re-observe window and re-resolve scroll container.
3. **Tier 3: Dynamic Gesture Swipe Fallback (`AndroidAutomationCompat.dispatchSwipe`)**
   - Derived dynamically from current display metrics or container bounds (`TargetBounds`).
   - Forward Scroll (Scroll Down): Swipe UP from Y=75% to Y=25%.
   - Backward Scroll (Scroll Up): Swipe DOWN from Y=25% to Y=75%.

### Post-Scroll Verification
- Compares pre-scroll and post-scroll `StateSignature` and visible text sets (`freshSnapshot.visibleTexts != afterSnapshot.visibleTexts`).
- If no state or visual change occurs, reports `SCROLL_NO_PROGRESS` / `ExecutionReason.STUCK`.
