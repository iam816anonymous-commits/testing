# V1.1 — Persistent Agent Runtime Architecture

## 1. Overview & Vision
`CreatorAutomation V1.1` establishes a **persistent, crash-recoverable, event-driven-capable agent runtime** on Android 8.1.0 (API 27). Rather than relying on transient in-memory execution loops, the agent's state machine, execution milestones, observations, and actions are continuously checkpointed to a local Room/SQLite database.

When the application process dies (due to system low-memory killer, app update, device reboot, or crash), the runtime automatically recovers interrupted sessions upon restart, captures fresh device observations, compares state signatures, and resumes execution safely without duplicating side-effecting actions.

---

## 2. Architecture & Components

```text
                  USER / SCHEDULED TASK
                            │
                            ▼
                  AgentRuntimeManager
                            │
            ┌───────────────┴───────────────┐
            ▼                               ▼
    AgentSessionRecord             AgentCore (Loop)
   (Room DB Entity v7)                     │
            │                     ┌────────┴────────┐
            ▼                     ▼                 ▼
     AgentSessionDao         Observe            Resolve
            │                     │                 │
            ▼                     ▼                 ▼
   Checkpoint Milestones    Multi-Source         Execute
   & Interrupted Recovery   (Acc/Screen/Cam)        │
                                                    ▼
                                                 Verify
```

### Key Classes

* **`AgentSessionRecord`**: Room Entity (`agent_session_records`) tracking `sessionId`, `taskId`, `taskDescription`, `currentState`, `currentStepIndex`, `checkpointStateSignature`, `recoveryAttemptCount`, `isCompleted`, `isCancelled`, and `isInterrupted`.
* **`AgentSessionDao`**: Room Data Access Object providing persistent session CRUD queries, finding active/interrupted sessions, and clearing finished sessions.
* **`AgentRuntimeManager`**: Runtime orchestrator managing session creation, milestone checkpointing, process-death recovery, cancellation, and duplicate session/task execution protection.
* **`AgentCore`**: Core event-driven agent loop (`OBSERVE -> RESOLVE -> PLAN -> ACT -> VERIFY -> LEARN`).
* **`AutomationWorker`**: WorkManager background job delegating scheduled tasks and executing process-death recovery.

---

## 3. Persistent Session State Machine

The persistent session tracks the exact lifecycle states of `AgentCore`:

```text
IDLE
  ↓
OBSERVING ──→ RESOLVING ──→ PLANNING ──→ EXECUTING
  ↑                                          │
  │                                          ▼
RECOVERING ←─────────────────────────── VERIFYING
  │                                          │
  ├───────────────┐                          ▼
  ▼               ▼                       LEARNING
RESUME          PAUSED                       │
                  │                          ▼
                  ▼                      COMPLETED
              CANCELLED
```

### Valid Transitions

* `IDLE -> OBSERVING -> RESOLVING -> PLANNING -> EXECUTING -> VERIFYING -> LEARNING -> COMPLETED`
* `EXECUTING -> RECOVERING -> OBSERVING -> EXECUTING / PAUSED / FAILED`
* `EXECUTING -> PAUSED -> CANCELLED`

---

## 4. Crash-Safe Checkpointing & Process-Death Recovery Algorithm

### Checkpoints
Checkpoints are persisted to Room DB at key execution milestones:
1. Task session creation (`IDLE` -> `OBSERVING`)
2. Multi-source observation capture (`RESOLVING`)
3. Action execution start (`EXECUTING`)
4. Post-action state verification (`VERIFYING`)
5. Learning / step completion (`COMPLETED` / `FAILED`)

### Recovery Algorithm (`recoverInterruptedSessions`)
Upon application launch or WorkManager invocation:
1. Query incomplete, non-cancelled sessions (`isCompleted = 0 AND isCancelled = 0`).
2. Mark session `isInterrupted = true`, state = `RECOVERING`, increment `recoveryAttemptCount`.
3. If `recoveryAttemptCount >= 2`, mark session `FAILED` to prevent infinite recovery loops.
4. **CRITICAL RULE**: Capture a fresh device observation (`captureObservation()`) **before acting**.
5. Compare current state signature with `checkpointStateSignature`.
6. Resume execution step via `AgentCore` only if safe; otherwise pause/fail safely without re-executing side-effecting actions.

---

## 5. Duplicate Execution Protection & Cancellation

* **Session Uniqueness**: `startOrResumeTaskSession` queries `getActiveSessionByTaskDescription`. If an active session for the task already exists, it is reused rather than launching duplicate side effects.
* **Idempotency**: WorkManager retries delegate to `AgentRuntimeManager`, preventing duplicate task execution.
* **Cancellation**: `cancelActiveSession` transitions session to `CANCELLED`, records cancellation reasons in Room DB, logs an audit record, and halts `AgentCore` execution cleanly.

---

## 6. Android 8.1 (API 27) Considerations

* Minimum SDK remains `minSdk = 26` (target SDK 35, tested on API 27).
* No foreground service is required to keep the agent alive. The agent relies on event triggers, Room DB persistence, and WorkManager background execution.
* Low memory overhead: Checkpoints store compact state signatures, summaries, and timestamps rather than raw bitmaps or video frames.

---

## 7. Open-Source Architectural Inspirations

* **MobileAgent / MobileAgent-Android**: Informs the observe → plan → execute → reflect loop and bounded failure recovery.
* **ClosePaw**: Informs persistent phone-agent session state, process takeover, and audit traces.
* **4AIs / OpenClaw Android**: Informs on-device action auditing and persistent session lifecycle tracking.
