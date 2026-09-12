package com.creator.automation

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "discovered_transitions")
data class DiscoveredTransitionRecord(
    @PrimaryKey val transitionId: String = UUID.randomUUID().toString(),
    val packageName: String,
    val sourceStateSignature: String,
    val actionType: String,
    val targetSurfaceId: String,
    val targetStateSignature: String,
    val isStateChanged: Boolean,
    val verificationStatus: String,
    val confidence: Double,
    val timestamp: Long = System.currentTimeMillis()
)
