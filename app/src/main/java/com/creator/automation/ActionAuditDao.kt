package com.creator.automation

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionAuditDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditRecord(record: ActionAuditRecord): Long

    @Query("SELECT * FROM action_audit_records ORDER BY timestamp DESC")
    fun getAllAuditRecordsFlow(): Flow<List<ActionAuditRecord>>

    @Query("SELECT * FROM action_audit_records ORDER BY timestamp DESC")
    suspend fun getAllAuditRecords(): List<ActionAuditRecord>

    @Query("SELECT * FROM action_audit_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestAuditRecord(): ActionAuditRecord?

    @Query("DELETE FROM action_audit_records")
    suspend fun deleteAllAuditRecords()
}
