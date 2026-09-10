package com.creator.automation

interface ReasoningProvider {
    fun getProviderName(): String
    fun isConfigured(): Boolean = true
    suspend fun requestReasoning(request: ReasoningRequest): ReasoningPlan?
}
