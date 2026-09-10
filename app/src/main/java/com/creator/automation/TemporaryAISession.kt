package com.creator.automation

enum class AISessionState {
    CREATED,
    CONTEXT_PREPARED,
    REQUESTED,
    RESPONDED,
    EXTRACTING,
    COMPLETED,
    EXPIRED,
    FAILED
}

enum class AIWorkerType {
    GENERAL_REASONING,
    RESEARCH,
    ANALYSIS,
    STRATEGY,
    PLANNING,
    FAILURE_ANALYSIS,
    AGENT_RD
}

data class TemporaryAISession(
    val sessionId: String = java.util.UUID.randomUUID().toString(),
    val providerName: String,
    val workerType: AIWorkerType,
    val taskDescription: String,
    val state: AISessionState = AISessionState.CREATED,
    val ttlMs: Long = 300000L, // 5 minute context TTL
    val createdTimestamp: Long = System.currentTimeMillis(),
    val responseSummary: String? = null,
    val failureReason: String? = null
) {
    val isExpired: Boolean get() = (System.currentTimeMillis() - createdTimestamp) > ttlMs
}
