# ANDROID AGENT REFERENCE STRATEGY MATRIX

| Reference Project | Strategy | Why Useful | API 27 Applicable? | Adopted? | Rejected? | CreatorAutomation Implementation |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Muse Android** | Discrete tools, observe-before-act, local loops | High reliability & structured feedback | Yes | **YES** | Shizuku dependency | `DeviceActionExecutor.kt`, `WaitEngine.kt`, `SkillRegistry.kt` |
| **ClosePaw** | Parent container node climbing | Handles leaf text view clicks | Yes | **YES** | API 31 requirement, Shizuku | `DeviceActionExecutor.performClickText` |
| **Argus** | Risk labels, prompt-injection defense | Safety & auditability | Yes | **YES** | Copyleft GPL code | `AutonomousExecutionGate.kt`, `GoalModel.kt` |
| **AutoDroid** | Candidate scoring & ambiguity filtering | Prevents misclicks on duplicate text | Yes | **YES** | Python / ADB host runner | `ActionResolver.resolveTargetWithAmbiguity` |
| **MobileAgent-Android** | 4-phase loop, state snapshotting | Modular execution phases | Yes | **YES** | Mandatory vision-model loop | `AgentCore.kt`, `WorldState.kt` |
| **Ghost in the Droid** | Observe-before-act recovery | Resilient crash recovery | Yes | **YES** | Python server backend | `AgentRuntimeManager.recoverInterruptedSessions` |
