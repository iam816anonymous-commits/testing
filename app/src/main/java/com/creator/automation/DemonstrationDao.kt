package com.creator.automation

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DemonstrationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: DemonstrationRecord): Long

    @Query("SELECT * FROM demonstration_records WHERE packageName = :packageName AND stateSignature = :stateSignature")
    suspend fun getRecordsForState(packageName: String, stateSignature: String): List<DemonstrationRecord>

    @Query("SELECT * FROM demonstration_records ORDER BY timestamp DESC")
    fun getAllRecordsFlow(): Flow<List<DemonstrationRecord>>

    @Query("DELETE FROM demonstration_records WHERE id = :id")
    suspend fun deleteRecord(id: Long)

    @Query("DELETE FROM demonstration_records")
    suspend fun deleteAllRecords()
}
