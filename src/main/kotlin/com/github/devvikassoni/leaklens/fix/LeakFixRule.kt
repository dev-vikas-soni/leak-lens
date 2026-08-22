package com.github.devvikassoni.leaklens.fix

import com.github.devvikassoni.leaklens.model.LeakInfo

/**
 * Represents a fix suggestion for a detected leak pattern.
 */
data class FixSuggestion(
    val ruleName: String,
    val explanation: String,
    val codeSnippet: String?,
    val confidence: Confidence = Confidence.HIGH
) {
    enum class Confidence { HIGH, MEDIUM, LOW }
}

/**
 * A rule that matches a leak pattern and provides a fix suggestion.
 */
interface LeakFixRule {
    val name: String
    val description: String

    /**
     * Returns a FixSuggestion if this rule matches the leak, null otherwise.
     */
    fun match(leak: LeakInfo): FixSuggestion?
}
