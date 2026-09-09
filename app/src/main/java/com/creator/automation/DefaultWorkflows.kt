package com.creator.automation

object DefaultWorkflows {

    val youtubeStudioReadOnlyWorkflow = Workflow(
        id = "yt_studio_read_only",
        name = "YouTube Studio — Read Only",
        targetPackage = "com.google.android.apps.youtube.creator",
        enabled = true,
        steps = listOf(
            WorkflowStep(
                id = "step_1_open_studio",
                action = AutomationAction(
                    type = ActionType.OPEN_URL,
                    targetValue = "https://studio.youtube.com/"
                )
            ),
            WorkflowStep(
                id = "step_2_wait_for_load",
                action = AutomationAction(
                    type = ActionType.WAIT,
                    targetValue = "3000"
                )
            ),
            WorkflowStep(
                id = "step_3_check_auth_state",
                action = AutomationAction(
                    type = ActionType.CHECK_AUTH_STATE
                )
            ),
            WorkflowStep(
                id = "step_4_read_visible_ui",
                action = AutomationAction(
                    type = ActionType.READ_VISIBLE_UI
                )
            ),
            WorkflowStep(
                id = "step_5_capture_initial_screen",
                action = AutomationAction(
                    type = ActionType.CAPTURE_SCREEN
                )
            ),
            WorkflowStep(
                id = "step_6_click_analytics",
                action = AutomationAction(
                    type = ActionType.CLICK_TEXT,
                    targetValue = "Analytics",
                    timeoutMs = 10000L
                ),
                verificationAction = AutomationAction(
                    type = ActionType.VERIFY_TEXT,
                    targetValue = "Analytics",
                    timeoutMs = 5000L
                )
            ),
            WorkflowStep(
                id = "step_7_capture_analytics_screen",
                action = AutomationAction(
                    type = ActionType.CAPTURE_SCREEN
                )
            )
        ),
        timeoutMs = 60000L,
        retryPolicy = RetryPolicy(maxRetries = 1, retryDelayMs = 2000L)
    )

    fun getAllWorkflows(): List<Workflow> {
        return listOf(youtubeStudioReadOnlyWorkflow)
    }
}
