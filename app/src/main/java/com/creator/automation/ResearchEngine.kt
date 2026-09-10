package com.creator.automation

import android.content.Context

enum class ResearchDomain {
    YOUTUBE_INTELLIGENCE,
    NEWS_INTELLIGENCE,
    FARM_RESEARCH,
    BUSINESS_RESEARCH,
    GENERAL_RESEARCH
}

data class ResearchFinding(
    val id: String = java.util.UUID.randomUUID().toString(),
    val domain: ResearchDomain,
    val topic: String,
    val claim: String,
    val evidenceSource: String? = null,
    val confidence: Double = 0.85,
    val timestamp: Long = System.currentTimeMillis()
)

class ResearchEngine(private val context: Context) {

    fun categorizeDomain(query: String): ResearchDomain {
        val lower = query.lowercase()
        return when {
            lower.contains("youtube") || lower.contains("video") || lower.contains("channel") || lower.contains("short") -> ResearchDomain.YOUTUBE_INTELLIGENCE
            lower.contains("news") || lower.contains("headlines") || lower.contains("article") -> ResearchDomain.NEWS_INTELLIGENCE
            lower.contains("crop") || lower.contains("farm") || lower.contains("rainfall") || lower.contains("agriculture") -> ResearchDomain.FARM_RESEARCH
            lower.contains("business") || lower.contains("market") || lower.contains("price") || lower.contains("competitor") -> ResearchDomain.BUSINESS_RESEARCH
            else -> ResearchDomain.GENERAL_RESEARCH
        }
    }

    fun generateResearchFinding(query: String, observedText: String): ResearchFinding {
        val domain = categorizeDomain(query)
        val claimText = if (observedText.isNotBlank()) observedText.take(150) else "No visible text observed for query '$query'"

        return ResearchFinding(
            domain = domain,
            topic = query,
            claim = claimText,
            evidenceSource = "Device observation snapshot",
            confidence = 0.90
        )
    }
}
