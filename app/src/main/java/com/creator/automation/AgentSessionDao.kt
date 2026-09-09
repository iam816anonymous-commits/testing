package com.creator.automation

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: AgentSessionRecord)

    @Update
    suspend fun updateSession(session: AgentSessionRecord)

    @Query("SELECT * FROM agent_session_records WHERE sessionId = :sessionId")
    suspend fun getSessionById(sessionId: String): AgentSessionRecord?

    @Query("SELECT * FROM agent_session_records ORDER BY lastUpdatedTimestamp DESC")
    fun getAllSessionsFlow(): Flow<List<AgentSessionRecord>>

    @Query("SELECT * FROM agent_session_records WHERE isCompleted = 0 AND isCancelled = 0 ORDER BY lastUpdatedTimestamp DESC")
    suspend fun getActiveOrInterruptedSessions(): List<AgentSessionRecord>

    @Query("SELECT * FROM agent_session_records WHERE taskDescription = :taskDescription AND isCompleted = 0 AND isCancelled = 0 LIMIT 1")
    suspend fun getActiveSessionByTaskDescription(taskDescription: String): AgentSessionRecord?

    @Query("DELETE FROM agent_session_records WHERE isCompleted = 1 OR isCancelled = 1")
    suspend fun clearFinishedSessions()
}
