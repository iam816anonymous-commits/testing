package com.creator.automation

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AutomationObservation::class,
        WorkflowSchedule::class,
        DemonstrationRecord::class,
        ActionAuditRecord::class,
        LearnedWorkflow::class,
        LearnedWorkflowStep::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun observationDao(): AutomationObservationDao
    abstract fun scheduleDao(): WorkflowScheduleDao
    abstract fun demonstrationDao(): DemonstrationDao
    abstract fun actionAuditDao(): ActionAuditDao
    abstract fun learnedWorkflowDao(): LearnedWorkflowDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "creator_automation_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
