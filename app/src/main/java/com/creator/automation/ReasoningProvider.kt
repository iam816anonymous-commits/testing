package com.creator.automation

interface ReasoningProvider {
    fun getProviderName(): String
    suspend fun requestReasoning(request: ReasoningRequest): ReasoningPlan?
}
