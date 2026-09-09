package com.creator.automation

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkflowScheduleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSchedule(schedule: WorkflowSchedule)

    @Query("SELECT * FROM workflow_schedules WHERE workflowId = :workflowId")
    suspend fun getSchedule(workflowId: String): WorkflowSchedule?

    @Query("SELECT * FROM workflow_schedules")
    fun getAllSchedulesFlow(): Flow<List<WorkflowSchedule>>

    @Query("SELECT * FROM workflow_schedules WHERE enabled = 1")
    suspend fun getEnabledSchedules(): List<WorkflowSchedule>

    @Query("DELETE FROM workflow_schedules WHERE workflowId = :workflowId")
    suspend fun deleteSchedule(workflowId: String)
}
