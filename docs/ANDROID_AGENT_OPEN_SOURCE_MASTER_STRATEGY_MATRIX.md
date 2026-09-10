# ANDROID AGENT OPEN-SOURCE MASTER STRATEGY MATRIX

**Date:** March 2025
**Target Platform:** Native Kotlin Android Autonomous Agent (Android 8.1 / API 27+, 4 GB RAM, 64 GB Storage)

---

## 1. COMPREHENSIVE REPOSITORY STRATEGY EVALUATION

| Project | Primary Purpose | Observation Strategy | Target Resolution | Action Model | Safety / Recovery | Classification |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **PokeClaw** | Phone-resident AI agent | Accessibility UI tree | Semantic target discovery | Discrete tap/swipe/type tools | Observe-act-observe loop | **ADOPT NOW** |
| **Autofish** | Deterministic automation | Fresh UI snapshot | Stable node references | Single action dispatch | Observe-before-act recovery | **ADOPT NOW** |
| **ClawMobile** | Agent runtime & skills | UI state signatures | Candidate scoring | Reusable skill patterns | Skill confidence scoring | **ADAPT LATER** |
| **Muse Android** | Discrete tool automation | Live Accessibility tree | Priority matching | Bounded local loops | Verification & feedback | **ADOPT NOW** |
| **ClosePaw** | On-device ReAct harness | Node tree traversal | Parent container climbing | Discrete mobile actions | Safety app blacklists | **ADOPT NOW** |
| **Argus** | Deterministic compiler | UI state keys | Typed rule validation | Fingerprinted actions | Risk labels (`ActionSemantics`) | **ADOPT NOW** |
| **DroidPilot** | Accessibility execution | Structured UI tree | Selector matching | Explicit tool protocol | Timeout bounds | **ADAPT LATER** |
| **NeuralBridge** | In-process execution | Accessibility event stream | Semantic resolver | Action latency optimization | Event streaming | **ADAPT LATER** |
| **DroidWright** | Composable automation | Selector scripts | Query matching | Scripted action loops | ANR protection | **RESEARCH ONLY** |
| **OpenTasker** | Rule automation system | System triggers | Capability manifests | Permission gates | Circuit breakers | **ADAPT LATER** |
| **Ksenax** | On-device local AI | Local vision / UI | Policy tool calling | Safe tool calls | Local-first constraints | **RESEARCH ONLY** |
| **Damru** | Browser-native automation | Chrome CDP / DOM | DOM selectors | Web-native actions | Browser session isolation | **DO NOT USE** |

---

## 2. STRATEGY ADOPTION SUMMARY

* **`ADOPT NOW` (Native Kotlin Core):**
  - **PokeClaw:** Discrete native tool primitives (`click`, `type`, `scroll`, `submit`).
  - **Autofish:** Observe-before-act execution model and fresh snapshot captures.
  - **Muse Android:** Bounded local loops (`scroll_until`, `wait_until`) and bounds-derived gesture tap fallback (`dispatchGestureTap`).
  - **ClosePaw:** Parent container node climbing for non-clickable leaf text views.
  - **Argus:** Risk-classified action semantics (`ActionSemantics`) and prompt injection defense boundaries.
* **`ADAPT LATER`:**
  - **ClawMobile:** Skill confidence tracking and demonstration learning.
  - **OpenTasker:** Execution admission circuit breakers.
* **`RESEARCH ONLY`:**
  - **Ksenax:** On-device GGUF models (too memory-intensive for 4 GB RAM baseline).
* **`DO NOT USE`:**
  - **Damru:** Host CDP / Playwright browser controller (requires PC / Docker infrastructure).
