package com.creator.automation

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskRecord)

    @Query("SELECT * FROM task_records WHERE id = :id")
    suspend fun getTaskById(id: String): TaskRecord?

    @Query("SELECT * FROM task_records ORDER BY createdAt DESC")
    fun getAllTasksFlow(): Flow<List<TaskRecord>>

    @Query("DELETE FROM task_records WHERE id = :id")
    suspend fun deleteTask(id: String)

    @Query("DELETE FROM task_records")
    suspend fun deleteAllTasks()
}
