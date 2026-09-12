package com.creator.automation

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiscoveredTransitionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransition(record: DiscoveredTransitionRecord)

    @Query("SELECT * FROM discovered_transitions WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun getTransitionsForPackage(packageName: String): Flow<List<DiscoveredTransitionRecord>>

    @Query("SELECT * FROM discovered_transitions WHERE packageName = :packageName AND sourceStateSignature = :sourceSignature")
    suspend fun getTransitionsForState(packageName: String, sourceSignature: String): List<DiscoveredTransitionRecord>

    @Query("SELECT * FROM discovered_transitions ORDER BY timestamp DESC LIMIT 50")
    fun getAllRecentTransitions(): Flow<List<DiscoveredTransitionRecord>>
}
