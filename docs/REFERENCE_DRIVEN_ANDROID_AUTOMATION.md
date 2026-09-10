# REFERENCE-DRIVEN ANDROID AUTOMATION STRATEGY ANALYSIS

**Date:** March 2025
**Target:** CreatorAutomation (`com.creator.automation`, API 27+, low-RAM)
**Objective:** In-depth evaluation of proven open-source Android GUI agents (Muse Android, ClosePaw, Argus, AutoDroid, MobileAgent, Ghost in the Droid) and clean-room Kotlin adaptation into CreatorAutomation.

---

## 1. STRATEGY EXTRACTION & ANALYSIS

### 1. Muse Android (`Assangejulian/Muse_Android`)
* **Key Concept:** Granular tool abstraction (`find_nodes`, `read_node`, `click_node`, `click_text`, `input_text`, `submit_input`, `scroll_until`, `wait_until`).
* **Why It Works:** Decouples high-level task goals from low-level execution mechanics. Every action mutation is followed by re-observation and verification.
* **Native Adaptation:** CreatorAutomation adopts Muse's discrete tool primitives and bounded local loops (`scroll_until`, `wait_until`) without Shizuku or external runners.

### 2. ClosePaw (`imoonkey/closepaw`)
* **Key Concept:** Parent container node climbing for non-clickable leaf text views, ReAct loop, per-app `SKILL.md` hints.
* **Why It Works:** Solves the common Android accessibility issue where text nodes (e.g. TextView) are not clickable themselves, but sit inside a clickable parent container (`FrameLayout` or `LinearLayout`).
* **Native Adaptation:** Integrated into `DeviceActionExecutor.performClickText` to automatically walk up `node.parent` until a clickable node is found.

### 3. Argus (`JackRushante/Argus`)
* **Key Concept:** Risk-classified action semantics (`ActionSemantics`), approval fingerprints, prompt-injection defense boundaries.
* **Why It Works:** Treats UI text as untrusted data, preventing adversarial web/app strings from hijacking the decision engine.
* **Native Adaptation:** CreatorAutomation implements `AutonomousExecutionGate`, `GoalModel.kt` (`rawUserIntent` vs `UI_CONTENT`), and sensitive parameter redaction (`[REDACTED]`).

### 4. AutoDroid (`MobileLLM/AutoDroid`)
* **Key Concept:** Candidate node collection, ambiguity candidate filtering, candidate scoring.
* **Why It Works:** Avoids blind execution when multiple candidates match a target query.
* **Native Adaptation:** Integrated into `ActionResolver.resolveTargetWithAmbiguity` with candidate scoring and `AMBIGUOUS_TARGET` blocking.

---

## 2. GESTURE CLICK FALLBACK & DISPATCH ENGINE

When standard `AccessibilityNodeInfo.performAction(ACTION_CLICK)` fails on certain Android apps that do not properly handle accessibility click events, CreatorAutomation employs a generic bounds-derived `dispatchGesture()` fallback:

```text
Target Node Resolved
         │
         ▼
[1. Try performAction(ACTION_CLICK)] ──► SUCCESS ──► Verify State Change
         │
         ├── Fails / Unsupported
         ▼
[2. Calculate Center Point from TargetBounds]
         │
         ▼
[3. Dispatch Generic Tap Gesture via dispatchGesture()]
         │
         ▼
[4. Re-observe & Verify State Signature Change]
```

*Note: Coordinates are strictly derived from live resolved target node bounds (`bounds.centerX`, `bounds.centerY`). No hardcoded coordinates are allowed.*
