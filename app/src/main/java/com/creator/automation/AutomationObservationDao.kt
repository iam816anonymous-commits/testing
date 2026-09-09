package com.creator.automation

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationObservationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObservation(observation: AutomationObservation): Long

    @Query("SELECT * FROM automation_observations ORDER BY timestamp DESC")
    fun getAllObservations(): Flow<List<AutomationObservation>>

    @Query("SELECT * FROM automation_observations ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestObservation(): AutomationObservation?

    @Query("SELECT * FROM automation_observations WHERE workflowId = :workflowId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestForWorkflow(workflowId: String): AutomationObservation?
}
