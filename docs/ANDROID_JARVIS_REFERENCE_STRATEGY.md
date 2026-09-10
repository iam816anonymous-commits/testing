# CREATORAUTOMATION — ANDROID JARVIS REFERENCE STRATEGY ANALYSIS

**Date:** March 2025
**Target:** CreatorAutomation (`com.creator.automation`, API 27+, low-RAM)
**Objective:** Evaluation of open-source Android agent research strategies (Muse Android, Mobile-Agent, AutoDroid, Agent-Android, Ghost in the Droid, ClosePaw, Argus) and native adaptation into CreatorAutomation's architecture.

---

## 1. EXECUTIVE SUMMARY

To build a true native Android Jarvis-style autonomous runtime on Android 8.1 / API 27 without external PC dependencies, we evaluated leading open-source Android agent frameworks and research papers.

**Core Architectural Decision:**
We do NOT import external agent frameworks, python runtimes, ADB hosts, or Shizuku runners. Instead, we adapt proven architectural strategies natively into Kotlin and Android's AccessibilityService / standard Android APIs.

---

## 2. REFERENCE REPOSITORY STRATEGY EVALUATION

### 1. Muse Android (`Assangejulian/Muse_Android`)
* **Strategy:** Granular tool abstraction (`find_nodes`, `read_node`, `click`, `input_text`, `submit_input`, `scroll_until`, `wait_until`), observing live UI tree before every action, independent completion verification.
* **Why It Works:** Decouples high-level intention from low-level node execution. Treats ordinary action failures as feedback rather than crashing the session.
* **What We Adopt:** Granular capability abstraction, observe-before-act lifecycle, post-action signature verification, bounded local wait/scroll loops (`SCROLL_UNTIL`, `WAIT_FOR_TEXT`).
* **What We Reject:** Shizuku background runner dependency (we rely strictly on unprivileged AccessibilityService + standard Android APIs).
* **Implementation Location:** `CapabilityRegistry.kt`, `DeviceActionExecutor.kt`, `TaskDecisionEngine.kt`.

### 2. ClosePaw (`imoonkey/closepaw`)
* **Strategy:** Accessibility node-climbing to clickable parent containers, on-device ReAct loop, per-app `SKILL.md` hints, markdown memory.
* **Why It Works:** Handles non-clickable leaf text nodes (e.g. TextView inside a clickable FrameLayout) by walking up the ancestor hierarchy.
* **What We Adopt:** Parent container node climbing in `DeviceActionExecutor`, generic Skill registry patterns (`Skill.kt`), structured execution memory.
* **What We Reject:** Android 12+ API 31 requirement, hardcoded app blacklists.
* **Implementation Location:** `DeviceActionExecutor.performClickText`, `SkillRegistry.kt`.

### 3. Argus (`JackRushante/Argus`)
* **Strategy:** Deterministic compiler, approval fingerprints, risk labels (`ActionSemantics`), strict prompt-injection defenses.
* **Why It Works:** Treats all UI text as untrusted data, preventing adversarial web/app text from hijacking the LLM prompt.
* **What We Adopt:** `ActionSemantics` (`READ_ONLY`, `IDEMPOTENT`, `NON_IDEMPOTENT`, `HIGH_RISK`), `AutonomousExecutionGate`, sensitive parameter redaction (`[REDACTED]`).
* **What We Reject:** Copyleft GPL code (our implementation is 100% clean-room native Kotlin).
* **Implementation Location:** `AutonomousExecutionGate.kt`, `DeviceActionExecutor.kt`.

### 4. AutoDroid (`MobileLLM/AutoDroid`)
* **Strategy:** Candidate node collection, ambiguity candidate filtering, candidate scoring.
* **Why It Works:** Prevents blind click dispatch when multiple UI elements match the target string.
* **What We Adopt:** Candidate scoring algorithm, candidate count thresholds, `AMBIGUOUS_TARGET` blocking (`TargetResolutionStatus.AMBIGUOUS`).
* **What We Reject:** Python/ADB host architecture.
* **Implementation Location:** `ActionResolver.resolveTargetWithAmbiguity`.

### 5. MobileAgent-Android (`GiggleWang/MobileAgent-Android`)
* **Strategy:** 4-phase loop (Manager/Plan, Executor/Act, Reflector/Evaluate, Notetaker/Memory).
* **Why It Works:** Separates action dispatch from outcome evaluation and persistent memory updates.
* **What We Adopt:** `AgentState` phase transitions (`OBSERVING`, `RESOLVING`, `EXECUTING`, `VERIFYING`, `LEARNING`), `WorldState` snapshot comparison.
* **What We Reject:** Mandatory vision-language model requirements for basic UI automation.
* **Implementation Location:** `AgentCore.kt`, `WorldState.kt`.

---

## 3. ADAPTATION MATRIX

| Reference Concept | Open-Source Source | CreatorAutomation Native Implementation |
| :--- | :--- | :--- |
| **Parent Node Climbing** | ClosePaw | `DeviceActionExecutor.performClickText` (walks parent `AccessibilityNodeInfo`) |
| **Ambiguity Candidate Scoring** | AutoDroid | `ActionResolver.resolveTargetWithAmbiguity` (collects, scores, and blocks on equal candidate scores) |
| **Observe-Before-Act** | Muse Android | `AgentCore.executeTaskStep` & `AgentRuntimeManager.recoverInterruptedSessions` |
| **Structured Action Decision** | Argus / MobileAgent | `ActionDecision.kt`, `TaskDecisionEngine.kt` |
| **Prompt-Injection Defense** | Argus | `USER_INTENT` vs `UI_CONTENT` separation in `GoalModel.kt` |
| **Generic Skill Abstraction** | Agent Skills / ClosePaw | `Skill.kt`, `SkillRegistry.kt` |
| **Diagnostic Overlay** | Muse / MobileAgent | `AutomationOverlayState.kt`, `MainActivity.kt` |

---

## 4. VERIFICATION & TESTING STRATEGY

1. **Unit Tests:** `app/src/test/java/com/creator/automation/` tests target candidate scoring, parent node climbing, WorldState state signatures, GoalModel parsing, and local decision engine resolution.
2. **Build Check:** `./gradlew test assembleDebug` passes 100% cleanly.
3. **Physical Verification:** Physical API 27 device execution protocol documented in `docs/ANDROID_JARVIS_AUTOMATION_BENCHMARK.md`.
