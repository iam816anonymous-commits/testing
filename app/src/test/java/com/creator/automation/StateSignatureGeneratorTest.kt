package com.creator.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StateSignatureGeneratorTest {

    @Test
    fun testGenerateSignature_IdenticalSnapshotsProduceSameHash() {
        val snapshot1 = UiSnapshot(
            packageName = "com.google.android.apps.youtube.creator",
            visibleTexts = listOf("Channel Analytics", "Views", "1,234 views"),
            viewIds = listOf("com.google.android.apps.youtube.creator:id/analytics_tab")
        )

        val snapshot2 = UiSnapshot(
            packageName = "com.google.android.apps.youtube.creator",
            visibleTexts = listOf("Channel Analytics", "Views", "5,678 views"), // Dynamic number differs
            viewIds = listOf("com.google.android.apps.youtube.creator:id/analytics_tab")
        )

        val sig1 = StateSignatureGenerator.generateSignature(snapshot1)
        val sig2 = StateSignatureGenerator.generateSignature(snapshot2)

        // Signatures should match because dynamic numbers are normalized
        assertEquals(sig1, sig2)
    }

    @Test
    fun testGenerateSignature_DifferentScreensProduceDifferentHash() {
        val studioSnapshot = UiSnapshot(
            packageName = "com.google.android.apps.youtube.creator",
            visibleTexts = listOf("Channel Dashboard", "Latest Video Performance"),
            viewIds = listOf("com.google.android.apps.youtube.creator:id/dashboard")
        )

        val chatGptSnapshot = UiSnapshot(
            packageName = "com.openai.chatgpt",
            visibleTexts = listOf("New Chat", "Ask anything"),
            viewIds = listOf("com.openai.chatgpt:id/input")
        )

        val sig1 = StateSignatureGenerator.generateSignature(studioSnapshot)
        val sig2 = StateSignatureGenerator.generateSignature(chatGptSnapshot)

        assertNotEquals(sig1, sig2)
    }
}
