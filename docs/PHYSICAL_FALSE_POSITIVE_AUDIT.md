# ANTI-FALSE-POSITIVE PHYSICAL AUDIT & PREVENTATIVE RULES

## 1. PREVENTED FALSE POSITIVE PATHS

1. **Action Dispatch Success != Goal Completion:** `performAction(ACTION_CLICK)` returning `true` does NOT mark a task `COMPLETED`. The agent MUST observe post-action UI changes and verify goal criteria.
2. **Empty Package Wait Timeout:** `WAIT_FOR_PACKAGE("")` conditions with blank package names fail immediately as `INVALID_WAIT_CONDITION` rather than hanging for 10 seconds.
3. **Unverified Torch State:** Flashlight command execution on API 27 hardware reports `VERIFICATION_UNAVAILABLE` rather than claiming false success.
4. **Ambiguous Target Protection:** When multiple candidates match a target query (`candidateCount > 1`), execution is blocked as `AMBIGUOUS_TARGET` to prevent accidental clicks on wrong controls.
5. **Session Trap Auto-Reset:** Submitting a new command in `AgentCommandScreen` automatically resets `_agentState` from `PAUSED` or `CANCELLED` to `IDLE`, preventing permanent runtime loop traps.

## 2. CROSS-APP INPUT DIFFERENCES
- **Chrome (`com.android.chrome`):** Exposes direct focused editable input fields with `ACTION_SET_TEXT` support.
- **YouTube (`com.google.android.youtube`):** Requires initial `CLICK_TEXT("Search")` to open search overlay before focused editable input node becomes accessible for `TYPE_TEXT`.
- **Notebook / Notes (`com.creator.automation`):** Requires focused node resolution before `ACTION_SET_TEXT` injection.
