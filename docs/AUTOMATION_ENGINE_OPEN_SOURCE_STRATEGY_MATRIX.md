# OPEN-SOURCE ANDROID AUTOMATION STRATEGY MATRIX REPORT

**Date:** March 2025
**Target:** CreatorAutomation Android Project (`com.creator.automation`, API 27+)
**Scope:** Open-Source Strategy Study & Comparative Substrate Analysis
**Goal:** Extract proven strategies, algorithms, and architectural patterns from open-source Android automation projects and compare them against CreatorAutomation's Kotlin architecture.

---

## 1. COMPREHENSIVE STRATEGY COMPARISON MATRIX

| Repository | Relevant Subsystem | Strategy / Technique | Problem It Solves | Why It Works | CreatorAutomation Equivalent | Current Strength | Recommended Adaptation | Adaptation Difficulty | API 27 Safe? | Recommendation |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :---: | :---: |
| **ClosePaw** (`imoonkey/closepaw`) | UI Tree Traversal | Parent Container Bubble-Up | Non-clickable child text nodes fail click dispatches | Walks up `AccessibilityNodeInfo.parent` until `isClickable == true` | `DeviceActionExecutor.performClickText()` | **Stronger** (Added candidate counting & ambiguity blocking) | Maintain current implementation | Low | YES | **ADAPTED / KEEP** |
| **MobileAgent-Android** (`GiggleWang/MobileAgent-Android`) | Target Resolution | Multi-Tier Priority Matching | Coordinate guessing breaks across screens and font sizes | Hierarchical resolution: View ID $\rightarrow$ Exact Text $\rightarrow$ ContentDesc $\rightarrow$ Partial | `ActionResolver.resolveTargetWithAmbiguity()` | **Equivalent** (Deterministic tree resolution) | Add candidate match confidence scoring | Low | YES | **ADAPTED / HARDEN** |
| **AutoDroid** (`MobileLLM/AutoDroid`) | Ambiguity Safety | Candidate Counting & Hard Blocking | Blindly clicking candidate 0 causes misclicks on duplicate text | Counts candidate matches; returns `AMBIGUOUS` if count > 1 | `ActionResolver.kt` & `DeviceActionExecutor.kt` | **Stronger** (Runs 100% on-device without Python/host ADB) | Maintain strict candidate 0 block & `NEEDS_USER_INPUT` | Low | YES | **ADAPTED / KEEP** |
| **Ghost in the Droid** (`ghost-in-the-droid/android-agent`) | Checkpoint Recovery | Observe-Before-Act Safeguard | Process crashes lead to blind re-execution of stale steps | Captures fresh `UiSnapshot` and checks state signature before recovery | `AgentRuntimeManager.recoverInterruptedSessions()` | **Stronger** (Enforces `NEEDS_USER_INPUT` for unverified `NON_IDEMPOTENT` steps) | Maintain observe-before-act policy | Low | YES | **ADAPTED / KEEP** |
| **Agent-Android** (`xiaoran7/Agent-Android`) | Verification Loop | Post-Action Re-Observation & State Comparison | Declaring success from API return values causes false positives | Re-captures `UiSnapshot` and compares before/after signatures | `DeviceActionExecutor.executeAndAudit()` & `GoalVerifier.kt` | **Stronger** (Multi-step goal verification + visual luminance check) | Add `NO_PROGRESS` detection for scroll/input actions | Low | YES | **ADAPTED / HARDEN** |
| **MobileAgent** (`Bilal140202/MobileAgent`) | Action Abstraction | Structured Action Representations | Unstructured scripts cannot be audited or persisted | Maps actions to structured data models with preconditions & semantics | `AutomationAction` & `ActionAuditRecord` | **Stronger** (Persisted in Room DB with sensitive parameter redaction) | Maintain current architecture | Low | YES | **ADAPTED / KEEP** |
| **Argus** (`JackRushante/argus`) | Architecture | Planner / Execution Engine Separation | Coupling reasoning with UI execution creates fragile code | Planner generates structured action specs; engine executes deterministically | `TaskResolver.kt` & `DeviceActionExecutor.kt` | **Equivalent** (Clean capability snapshot contract in `docs/AUTOMATION_ENGINE_2_1_CONTRACT.md`) | Maintain clean contract | Low | YES | **ADAPTED / KEEP** |
| **Muse Android** (`Assangejulian/Muse_Android`) | Observation & Input | Node Filtering & Focus Injection | Non-focusable text fields reject input | Inspects `isEditable`/`isFocused`, focuses node via `ACTION_FOCUS` before `ACTION_SET_TEXT` | `ActionResolver.resolveEditableTarget()` & `DeviceActionExecutor.performTypeText()` | **Stronger** (No Shizuku dependency; 100% native Accessibility API) | Incorporate stale-node re-verification prior to input | Low | YES | **ADAPTED / HARDEN** |

---

## 2. STRATEGY COMPARISON & HIGH-VALUE ADAPTATION DECISIONS

### Adapted Strategies (Already Incorporated or Hardened)
1. **Parent Container Bubble-Up (ClosePaw):** Direct text views (e.g. `TextView` inside `FrameLayout`) walk up node parents to locate actionable containers.
2. **Deterministic Priority Resolution (MobileAgent-Android):** View ID $\rightarrow$ Exact Text $\rightarrow$ Content Description $\rightarrow$ Partial Match.
3. **Target Candidate Ambiguity Blocking (AutoDroid):** Candidate count > 1 strictly returns `AMBIGUOUS_TARGET` and halts execution.
4. **Observe-Before-Act Recovery (Ghost in the Droid):** Fresh UI observation mandatory before resuming crashed sessions.
5. **Post-Action Verification & Progress Detection (Agent-Android):** Re-observes UI state post-dispatch to verify state signature changes or visual luminance shifts.

### Rejected / Not Recommended Strategies
1. **Python / Host-Side ADB Runtimes (AutoDroid, Ghost in the Droid):** Rejected. CreatorAutomation must run 100% native on-device on Android 8.1 / API 27 without external servers or ADB.
2. **Shizuku / Privileged ADB Control (Muse Android):** Rejected. CreatorAutomation operates via standard unprivileged Android `AccessibilityService` without requiring root or Shizuku setup.
3. **Coordinate Guessing / Visual Tap Selection:** Rejected. Coordinate taps fail across device screen resolutions and orientation changes. CreatorAutomation enforces semantic Accessibility tree targeting.
