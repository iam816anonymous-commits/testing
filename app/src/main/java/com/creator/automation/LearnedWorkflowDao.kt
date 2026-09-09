package com.creator.automation

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LearnedWorkflowDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkflow(workflow: LearnedWorkflow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<LearnedWorkflowStep>)

    @Query("SELECT * FROM learned_workflows WHERE id = :id")
    suspend fun getWorkflowById(id: String): LearnedWorkflow?

    @Query("SELECT * FROM learned_workflows WHERE startingStateSignature = :startingStateSignature AND status != 'PAUSED'")
    suspend fun getWorkflowsForStartingState(startingStateSignature: String): List<LearnedWorkflow>

    @Query("SELECT * FROM learned_workflows ORDER BY updatedAt DESC")
    fun getAllWorkflowsFlow(): Flow<List<LearnedWorkflow>>

    @Query("SELECT * FROM learned_workflow_steps WHERE workflowId = :workflowId ORDER BY sequenceNumber ASC")
    suspend fun getStepsForWorkflow(workflowId: String): List<LearnedWorkflowStep>

    @Query("DELETE FROM learned_workflows WHERE id = :id")
    suspend fun deleteWorkflow(id: String)

    @Query("DELETE FROM learned_workflow_steps WHERE workflowId = :workflowId")
    suspend fun deleteStepsForWorkflow(workflowId: String)
}
