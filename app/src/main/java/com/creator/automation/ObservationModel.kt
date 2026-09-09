package com.creator.automation

import java.util.UUID

enum class ObservationSource {
    ACCESSIBILITY,
    SCREEN,
    CAMERA,
    NOTIFICATION,
    AUDIO,
    DEVICE
}

data class CurrentObservation(
    val id: String = UUID.randomUUID().toString(),
    val source: ObservationSource = ObservationSource.ACCESSIBILITY,
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val stateSignature: String,
    val summary: String,
    val confidence: Double = 1.0,
    val snapshot: UiSnapshot? = null
)
