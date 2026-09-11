# Typing and Search Pipeline Architecture

## 1. Task Type Isolation
`TaskResolver.kt` isolates four fundamental task categories:
1. **NAVIGATION TASK:** "go home", "go back", "recents" -> System navigation workflow without app launches or screen captures.
2. **TEXT INPUT TASK:** "type <text>", "enter <text>" -> Pure typing workflow (`TYPE_TEXT` -> `READ_VISIBLE_UI`) executing directly against active window.
3. **APP LAUNCH TASK:** "open <app>" -> App launch workflow with dynamic package resolution.
4. **SEARCH TASK:** "search for <text>" / "open <app> and search for <text>" -> Multi-step workflow (`LAUNCH_APP` -> `TYPE_TEXT` -> `SUBMIT_INPUT` -> `READ_VISIBLE_UI`).

---

## 2. 5-Stage Typing Pipeline
1. **Observe & Resolve:** Fresh UI snapshot captures editable nodes.
2. **Focus & Click:** `ACTION_FOCUS` and `ACTION_CLICK` activate focus on target node.
3. **`ACTION_SET_TEXT` Injection:** Standard Accessibility API injects text sequence.
4. **Post-Observation:** Fresh snapshot observed after 300ms.
5. **Text Verification & Normalization:** Confirms text string or `[REDACTED]` placeholder in active UI tree.

---

## 3. Generic `SUBMIT_INPUT` Capabilities
1. **Semantic Submit Controls:** Resolves clickable controls with text/desc "Search", "Go", "Enter", "Submit".
2. **IME / Editor Action:** Triggers focused node search click or semantic submit.
3. **Ambiguity Blocking:** If multiple candidate search buttons exist, blocks execution for safety.
