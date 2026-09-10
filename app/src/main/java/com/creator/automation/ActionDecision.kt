package com.creator.automation

data class ActionDecision(
    val actionType: ActionType,
    val target: String? = null,
    val inputData: String? = null,
    val reason: String,
    val confidence: Double = 1.0,
    val expectedOutcome: String? = null,
    val riskLevel: ActionSemantics = ActionSemantics.REPEATABLE
)
