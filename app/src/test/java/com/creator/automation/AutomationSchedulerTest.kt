package com.creator.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationSchedulerTest {

    @Test
    fun testGetUniqueWorkName() {
        val workflowId = "yt_studio_read_only"
        val workName = AutomationScheduler.getUniqueWorkName(workflowId)

        assertEquals("automation_workflow_yt_studio_read_only", workName)
    }

    @Test
    fun testWorkflowScheduleModelDefaults() {
        val schedule = WorkflowSchedule(workflowId = "test_wf")

        assertEquals("test_wf", schedule.workflowId)
        assertEquals(false, schedule.enabled)
        assertEquals(1440L, schedule.intervalMinutes)
        assertEquals(true, schedule.requiresBatteryNotLow)
        assertEquals(false, schedule.requiresCharging)
        assertEquals(0L, schedule.nextExpectedRunTimestamp)
    }
}
