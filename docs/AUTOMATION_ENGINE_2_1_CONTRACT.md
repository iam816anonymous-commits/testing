# AUTOMATION ENGINE 2.1 CONTRACT & PLANNER INTERFACE SPECIFICATION

**Date:** March 2025
**Scope:** Interface Contract Between Future Planner/WorldState Layer and Automation Engine 2.1
**Target:** CreatorAutomation Android Project (`com.creator.automation`)

---

## 1. ARCHITECTURAL SEPARATION OF RESPONSIBILITIES

Automation Engine 2.1 serves as the **deterministic action & observation substrate** for CreatorAutomation.

```text
               HIGHER ARCHITECTURAL LAYERS
                     (User / Planner / Policy)
                                │
                                │ Structured Action Requests
                                │ (e.g. LAUNCH_APP, TYPE_TEXT, CLICK)
                                ▼
               AUTOMATION ENGINE 2.1 SUBSTRATE
                                │
                                ├── Observe Current Device UI State
                                ├── Resolve Target Semantically
                                ├── Check Action Preconditions
                                ├── Dispatch Native Android Accessibility Action
                                ├── Evaluate Wait Conditions & Polling
                                ├── Verify Goal / State Change
                                └── Recover or Request User Intervention
                                │
                                ▼
                     NATIVE ANDROID OS (API 27+)
```

### Core Responsibility Rule
* **Future Planner / WorldState Layer:** Decides **WHAT** should happen (e.g., `"Launch Chrome" -> "Type query" -> "Press Enter"`).
* **Automation Engine 2.1:** Decides **HOW** to safely and deterministically interact with the live Android UI.

---

## 2. WHAT THE PLANNER DOES NOT NEED TO KNOW

The future Planner is completely decoupled from low-level Android OS and Accessibility APIs. It does **NOT** need to know or handle:

1. `AccessibilityNodeInfo` tree traversal or node references.
2. Clickable parent container bubble-up logic (`node.isClickable == false` parent walking).
3. Android Accessibility action IDs (`ACTION_SET_TEXT`, `ACTION_CLICK`, `ACTION_FOCUS`, `ACTION_SCROLL_FORWARD`).
4. `PackageManager` intent resolution or package name scanning.
5. Coroutine polling, backoff delays, or wait timeouts.
6. Process-death session recovery or checkpoint database queries.
7. Sensitive parameter redaction (`[REDACTED]` for passwords, secrets, PINs).
8. Target ambiguity detection algorithms (`res.candidateCount > 1`).
9. Room DB audit logging (`ActionAuditRecord`).

---

## 3. SUPPORTED STRUCTURED ACTIONS IN CONTRACT

The Planner invokes Automation Engine 2.1 using `AutomationAction` instances specifying generic `ActionType` values:

| ActionType | Parameters | Planner Intent | Engine 2.1 Execution |
| :--- | :--- | :--- | :--- |
| `LAUNCH_APP` | `targetValue` = App Label/Pkg | Launch target app | Resolves app via `AppResolver` & launches intent |
| `OPEN_URL` | `targetValue` = URL string | Open web URL | Dispatches browser `ACTION_VIEW` Intent |
| `CLICK_TEXT` | `targetValue` = Target text/ID | Click visible element | Resolves target & performs `ACTION_CLICK` |
| `LONG_CLICK` | `targetValue` = Target text/ID | Long-press element | Resolves target & performs `ACTION_LONG_CLICK` |
| `TYPE_TEXT` | `targetValue` = Label/Hint, `inputData` = Text | Type input text | Focuses editable field & sets text via `ACTION_SET_TEXT` |
| `CLEAR_TEXT` | `targetValue` = Label/Hint | Clear editable field | Sets empty string `""` on editable field |
| `PRESS_ENTER` | None | Trigger search/submit | Clicks search button or sends enter/submit IME action |
| `SCROLL_DOWN` | None | Scroll container down | Executes `ACTION_SCROLL_FORWARD` on scrollable container |
| `SCROLL_UP` | None | Scroll container up | Executes `ACTION_SCROLL_BACKWARD` on scrollable container |
| `GO_BACK` | None | Navigate back | Triggers `GLOBAL_ACTION_BACK` |
| `PRESS_HOME` | None | Go to launcher home | Triggers `GLOBAL_ACTION_HOME` |
| `PRESS_RECENTS` | None | Open recents switcher | Triggers `GLOBAL_ACTION_RECENTS` |
| `READ_VISIBLE_UI` | None | Refresh UI state | Captures `UiSnapshot` & `AutomationCapabilitySnapshot` |

---

## 4. AUTOMATION CAPABILITY SNAPSHOT (`CapabilitySnapshot.kt`)

To allow the future Planner to reason about available UI interactions without exposing raw Android objects, Automation Engine 2.1 provides `AutomationCapabilitySnapshot`:

```kotlin
data class AutomationCapabilitySnapshot(
    val packageName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val availableCapabilities: List<String>,
    val clickableTargets: List<String>,
    val editableTargets: List<String>,
    val scrollableContainerCount: Int,
    val visibleTextSummary: List<String>
)
```

### Exposed Capabilities
* `CAN_LAUNCH_APP`
* `CAN_OPEN_URL`
* `CAN_CLICK_TARGET` (Present when clickable UI nodes exist)
* `CAN_TYPE_TEXT` (Present when editable input fields exist)
* `CAN_CLEAR_TEXT`
* `CAN_PRESS_ENTER`
* `CAN_SCROLL` / `CAN_SCROLL_UP` / `CAN_SCROLL_DOWN` (Present when scrollable containers exist)
* `CAN_GO_BACK` / `CAN_PRESS_HOME` / `CAN_PRESS_RECENTS`
* `CAN_READ_UI` / `CAN_REFRESH_OBSERVATION`

---

## 5. RECOVERY & HUMAN INTERVENTION CONTRACT

When an action execution fails or encounters ambiguity, Automation Engine 2.1 returns a structured `ActionResult`:

```text
               Action Execution Failure
                         │
        ┌────────────────┼────────────────┐
        ▼                ▼                ▼
   UI_NOT_FOUND    AMBIGUOUS_TARGET   UNVERIFIED_NON_IDEMPOTENT
        │                │                │
   LOCAL_REPAIR          │                │
  (Bounded Retry)        └────────┬───────┘
                                  ▼
                          USER_INTERVENTION
                                  │
                                  ▼
                          NEEDS_USER_INPUT
                        (AgentState Persisted)
```

* **`NEEDS_USER_INPUT` State:** Automatically stops autonomous loop, displays an intervention banner on `MainActivity`, and awaits explicit user resolution before fresh observation and resumption.
