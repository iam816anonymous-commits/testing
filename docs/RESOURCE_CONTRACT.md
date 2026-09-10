# CREATORAUTOMATION — RESOURCE CONTRACT & BUDGET SPECIFICATION

**Hardware Target:** Physical Android 8.1 / API 27 Device
**Specs:** 4 GB RAM, 64 GB Storage, Low-Power ARM CPU

---

## 1. HARDWARE CONSTRAINTS & PRINCIPLES

CreatorAutomation is a **low-resource, local-first Android agent**. Baseline resource usage must remain flat as agent capabilities grow.

---

## 2. RESOURCE BUDGETS

| Resource | Maximum Allocation | Strategy / Policy |
| :--- | :--- | :--- |
| **JVM Heap Memory** | < 100 MB | Discard stale `AccessibilityNodeInfo` references immediately; recycle Bitmaps & CameraX frames in `finally` blocks. |
| **App Storage Size** | < 250 MB | Bounded Room DB audit logs, automatic cleanup of temporary screenshots and expired session records. |
| **Perception Processing** | Event-driven | Accessibility tree primary; MediaProjection screen capture and CameraX frame analysis on-demand only. |
| **Local AI Models** | 0 MB (No local LLM) | Use deterministic local rules (`TaskDecisionEngine`) + optional external LLM worker. |
| **Background Threads** | Bounded pool | Coroutine Scope with IO dispatcher; no unbounded thread creation. |

---

## 3. RESOURCE GUARD & CLEANUP (`ResourceGuard.kt`)

`ResourceGuard` monitors runtime memory pressure and enforces three operational states:
1. **`NORMAL`:** Standard execution limits.
2. **`REDUCED_RESOURCE_MODE`:** Triggered when memory exceeds 80% threshold. Reduces perception frame rate and polling intervals.
3. **`EMERGENCY_CLEANUP`:** Triggered when memory exceeds 90% threshold. Clears screenshot cache, purges finished DB audit records, and halts background perception tasks.
