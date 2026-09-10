# OPEN-SOURCE BUILDING BLOCKS FOR CREATORAUTOMATION (FINAL JARVIS VISION)

**Date:** March 2025
**Target:** CreatorAutomation Android Project (`com.creator.automation`, API 27+, low-RAM)
**Scope:** Deep Research Analysis, Subsystem Mapping, Top 10 Reference Repositories, Risk Mitigation, and Staged Integration Roadmap

---

## 1. EXECUTIVE SUMMARY

We reviewed a wide range of open-source Android “phone agent” projects to identify reusable architectural patterns for a native, on-device Android UI automation agent (API 27+, low-RAM).

The key conclusion is:
> **Do not choose one GitHub project and turn it into your Jarvis.**
> CreatorAutomation should become a **hybrid architecture** assembled from the strongest strategies across several projects, while keeping our existing native Kotlin/API 27 Accessibility automation core as the foundation.

---

## 2. SUBSYSTEM MAPPING MATRIX

| Subsystem / Strategy | Primary Open-Source References | CreatorAutomation Component |
| :--- | :--- | :--- |
| **Accessibility / Interaction Body** | ClosePaw, MobileAgent-Android | `DeviceActionExecutor`, `ActionResolver` |
| **State Observation (Perception)** | MobileAgent, MobileAgent-Android, AndroidWorld | `ObservationProvider`, `ScreenObservationProvider`, `CameraObservationProvider` |
| **Action Semantics & Ambiguity Safety** | ClosePaw, AutoDroid, MobileAgent-v3 | `ActionResolver.resolveTargetWithAmbiguity()`, `ActionSemantics`, `AMBIGUOUS_TARGET` |
| **Wait, Retry & Crash Recovery** | Ghost in the Droid, Argus, Maestro | `WaitEngine`, `RecoveryManager`, `AgentRuntimeManager` |
| **Skills & Tool Substrate** | Agent Skills, ClosePaw, Ghost in the Droid | `CapabilityRegistry`, `TaskResolver`, `SKILL.md` format |
| **Memory Architecture** | ClosePaw, Jenny, PAi, Letta, Hermes | `AppDatabase` (Room), `AgentSessionRecord`, `DemonstrationRecord` |
| **Planner & Reasoner Loop** | ClosePaw, PAi, MobileAgent-v3, Argus | `TaskReasoner`, `TaskResolver`, `BranchingEngine` |
| **Evaluation & Benchmarking** | AndroidWorld, Mobile-Eval | `GenericActionExecutorTest`, CI test suite |
| **Safety & Privacy Controls** | Argus, ClosePaw, Jarvis Agent, PAi | `AutonomousExecutionGate`, `ActionAuditRecord` redaction, permission prompts |

---

## 3. PRIORITIZED TOP 10 REPOSITORIES TO STUDY

1. **ClosePaw (`imoonkey/closepaw`):** On-device ReAct harness, accessibility tree node climbing, markdown memory, skill parser. (Apache-2.0)
2. **MobileAgent-Android (`GiggleWang/MobileAgent-Android`):** Native Kotlin multi-phase agent loop (Plan, Act, Reflect, Note), screen capture integration. (MIT)
3. **PAi_Android (`Psycho051378/PAi_Android`):** Comprehensive assistant with hybrid LLM router, scheduler, facts DB, and local AI capabilities. (Apache-2.0)
4. **Ghost in the Droid (`ghost-in-the-droid/android-agent`):** Rich tool surface (62 tools), Python skill system, observe-before-act recovery checkpoints. (MIT)
5. **AndroidWorld (`google-research/android_world`):** Benchmark environment (116 tasks across 20 apps) for automated agent evaluation. (Apache-2.0)
6. **DroidBot-GPT (`MobileLLM/DroidBot-GPT`):** Baseline UI-to-text prompt formatting and ambiguity candidate filtering. (MIT)
7. **Argus (`JackRushante/Argus`):** Deterministic compiler, approval fingerprints, risk labels (`ActionSemantics`), strict prompt-injection defenses. (GPL-3.0 - strategy reference only)
8. **Jenny (`flagdizero/jenny-android-ai-agent`):** Local-first memory, cron-like task scheduler, proactive event triggers. (AGPL-3.0 - strategy reference only)
9. **OpenJarvis (`open-jarvis/OpenJarvis`):** Local-first cross-platform framework, plugin marketplace, resource-aware engineering metrics. (Apache-2.0)
10. **AutoDroid (`MobileLLM/AutoDroid`):** Candidate counting and strict ambiguity blocking (`AMBIGUOUS_TARGET`). (MIT)

---

## 4. RISK MITIGATION STRATEGIES

* **Security & Privacy:** Redact sensitive parameter inputs (`passwords`, `tokens`, `PINs`) in Room audit logs (`DeviceActionExecutor.redactSensitiveText()`). Enforce local-first execution without external telemetry.
* **Prompt Injection Defense:** Treat UI text as untrusted external data. Never execute raw LLM text directly; route LLM output through local JSON validators and `AutonomousExecutionGate` safety checks.
* **API 27 Compatibility:** Avoid unprivileged API dependencies (e.g. key injection requiring `INJECT_EVENTS` or Shizuku). Maintain 100% native Accessibility API execution on API 27+.
* **Resource Optimization:** Maintain low-RAM footprint (< 100MB JVM heap) for old Android hardware, keeping background services lightweight and closing CameraX / MediaProjection frames immediately.

---

## 5. STAGED INTEGRATION ROADMAP (2025–2026)

```text
Phase 1 (Q4 2025): Architectural Consolidation & Engine Hardening
  ├── Complete generic SUBMIT_INPUT semantic operation
  ├── Enforce strict candidate ambiguity blocking (AMBIGUOUS_TARGET)
  └── Implement Argus-style risk labeling (ActionSemantics)

Phase 2 (Q2 2026): Universal Tool Layer & Memory Architecture
  ├── Register generic tools (launch_app, click, type_text, submit_input, scroll, wait)
  ├── Implement Agent Skills format (SKILL.md)
  └── Add persistent experience memory in Room database

Phase 3 (Q3 2026): WorldState & Adaptive Planner Integration
  ├── Build explicit WorldState representation (screen, focus, nodes, confidence)
  ├── Integrate structured LLM plan validator
  └── Establish AndroidWorld-style benchmark test suite

Phase 4 (Q4 2026): Proactive Agent & Full Jarvis Integration
  ├── Implement event triggers and cron scheduler
  └── Finalize safety policy gates and local-first execution
```
