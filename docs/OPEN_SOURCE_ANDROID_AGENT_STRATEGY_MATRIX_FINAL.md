# OPEN-SOURCE ANDROID AGENT STRATEGY MATRIX (FINAL)

**Date:** March 2025
**Target Architecture:** Native Kotlin Android Autonomous Agent (API 27+, low-RAM)

---

## 1. STRATEGY CLASSIFICATION MATRIX

| Reference Project | Core Strategy | Problem Solved | Relevant Layer | Adaptation Status | Reason / Justification |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **PokeClaw** | Phone-resident agent, generic tap/swipe/text tools | On-device task execution without host tethering | Tools & Runtime | **ADOPT NOW** | Native Android tools match our low-RAM contract. |
| **Autofish** | Observe before action, stable UI references | Prevents stale node actions & false success | Action Executor | **ADOPT NOW** | Enforces fresh UI snapshots before every action dispatch. |
| **Muse Android** | Discrete tool model, bounds gesture fallback | High reliability & progress feedback | Action Executor | **ADOPT NOW** | Discrete tool semantics with `dispatchGestureTap` fallback. |
| **ClosePaw** | Parent container node climbing | Handles clicks on non-clickable leaf text views | Action Resolver | **ADOPT NOW** | Solves leaf `TextView` click failures via `node.parent` walking. |
| **Argus** | Risk labels, prompt injection defense | Safety, auditability & prompt security | Policy Gate | **ADOPT NOW** | `AutonomousExecutionGate` & `rawUserIntent` intent separation. |
| **AutoDroid** | Candidate scoring & ambiguity filtering | Prevents misclicks on duplicate text targets | Action Resolver | **ADOPT NOW** | `AMBIGUOUS_TARGET` blocking when multiple candidates match. |
| **ClawMobile** | Skill learning & demonstration feedback | Reusable multi-step task patterns | Skills & Learning | **ADAPT LATER** | `SkillRegistry` and `LearnedWorkflow` learning pipeline. |
| **MobileAgent-v3** | Self-evolving GUI trajectory production | Continuous skill improvement | Learning Engine | **ADAPT LATER** | Local evaluation framework without model retraining. |
| **Damru** | CDP / browser-native automation | High-fidelity web DOM interaction | Research | **DO NOT USE** | Requires host-side browser & CDP infrastructure. |
| **Redroid / Docker** | Cloud emulator containerization | Scalable emulator farm testing | Testing | **DO NOT USE** | Incompatible with on-device physical phone runtime. |

---

## 2. ADOPTION SUMMARY

* **`ADOPT NOW`:** PokeClaw tool abstraction, Autofish fresh observation, Muse bounds gesture fallback, ClosePaw parent climbing, Argus risk labeling & prompt defense, AutoDroid ambiguity candidate scoring.
* **`ADAPT LATER`:** ClawMobile demonstration skill learning, MobileAgent-v3 trajectory evaluation.
* **`DO NOT USE`:** Damru CDP, Redroid Docker containers, ADB tethering, Shizuku runners, root permissions.
