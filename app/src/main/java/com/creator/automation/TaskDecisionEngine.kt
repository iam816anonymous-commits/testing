package com.creator.automation

import android.content.Context

class TaskDecisionEngine(
    private val context: Context,
    private val skillRegistry: SkillRegistry = SkillRegistry(context)
) {

    /**
     * Evaluates the current GoalModel and WorldState to decide the next structured ActionDecision.
     */
    fun decideNextAction(goal: GoalModel, worldState: WorldState): ActionDecision {
        // 1. Check if goal is already satisfied
        if (goal.isGoalSatisfied(worldState)) {
            return ActionDecision(
                actionType = ActionType.END,
                reason = "Goal '${goal.rawUserIntent}' is verified complete in WorldState",
                confidence = 1.0,
                expectedOutcome = "Goal completion confirmed"
            )
        }

        // 2. Check action budget
        if (goal.currentActionCount >= goal.maxActionBudget) {
            return ActionDecision(
                actionType = ActionType.END,
                reason = "Action budget exceeded (${goal.currentActionCount}/${goal.maxActionBudget})",
                confidence = 0.0,
                expectedOutcome = "Session halted due to budget limit"
            )
        }

        // 3. Step A: App Launch check
        if (!goal.targetAppQuery.isNullOrBlank() && !worldState.packageName.contains(goal.targetAppQuery, ignoreCase = true)) {
            return ActionDecision(
                actionType = ActionType.LAUNCH_APP,
                target = goal.targetAppQuery,
                reason = "Target application '${goal.targetAppQuery}' is not currently active (${worldState.packageName})",
                confidence = 0.95,
                expectedOutcome = "App '${goal.targetAppQuery}' brought to foreground"
            )
        }

        // 4. Step B: Query / Search entry
        if (!goal.expectedTextInResult.isNullOrBlank()) {
            val editable = worldState.editableTargets.firstOrNull()
            if (editable != null) {
                return ActionDecision(
                    actionType = ActionType.TYPE_TEXT,
                    target = editable.text ?: editable.viewIdResourceName,
                    inputData = goal.expectedTextInResult,
                    reason = "Editable field available on screen; entering search query '${goal.expectedTextInResult}'",
                    confidence = 0.90,
                    expectedOutcome = "Query text injected into active editable field"
                )
            }

            // Step C: Look for clickable Search / Submit control if text already typed
            val searchBtn = worldState.clickableTargets.firstOrNull {
                it.text?.equals("Search", ignoreCase = true) == true ||
                        it.contentDescription?.equals("Search", ignoreCase = true) == true ||
                        it.text?.equals("Go", ignoreCase = true) == true
            }
            if (searchBtn != null) {
                return ActionDecision(
                    actionType = ActionType.CLICK_TEXT,
                    target = searchBtn.text ?: searchBtn.contentDescription ?: "Search",
                    reason = "Clicking visible search button '${searchBtn.text}'",
                    confidence = 0.90,
                    expectedOutcome = "Search submitted"
                )
            }

            // Fallback: Generic SUBMIT_INPUT
            return ActionDecision(
                actionType = ActionType.SUBMIT_INPUT,
                reason = "Submitting input for search query",
                confidence = 0.85,
                expectedOutcome = "Submission dispatched"
            )
        }

        // 5. Default Fallback: Read UI observation
        return ActionDecision(
            actionType = ActionType.READ_VISIBLE_UI,
            reason = "Observing visible UI targets on package '${worldState.packageName}'",
            confidence = 0.70,
            expectedOutcome = "Captured updated WorldState"
        )
    }
}
