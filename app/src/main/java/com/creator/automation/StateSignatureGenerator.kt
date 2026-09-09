package com.creator.automation

import java.security.MessageDigest

object StateSignatureGenerator {

    /**
     * Normalizes dynamic text elements (e.g. timestamps, numbers, view counts)
     * and produces a deterministic state signature string.
     */
    fun generateSignature(snapshot: UiSnapshot): String {
        val packageName = snapshot.packageName

        // Normalize text by stripping pure digits, timestamps, and extra whitespace
        val normalizedTexts = snapshot.visibleTexts
            .map { normalizeText(it) }
            .filter { it.isNotBlank() && it.length > 2 }
            .sorted()
            .distinct()

        val sortedViewIds = snapshot.viewIds
            .map { it.substringAfterLast("/") }
            .sorted()
            .distinct()

        val rawSignatureString = "pkg=$packageName|ids=${sortedViewIds.joinToString(",")}|texts=${normalizedTexts.joinToString(",")}"

        return sha256(rawSignatureString).take(16)
    }

    private fun normalizeText(text: String): String {
        return text.trim()
            .replace(Regex("\\d+"), "#") // Replace numbers with placeholder '#'
            .lowercase()
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
