package com.github.devvikassoni.leaklens.model

/**
 * Represents a single finding from static analysis.
 */
data class StaticAnalysisIssue(
    val ruleId: String,
    val title: String,
    val description: String,
    val severity: Severity,
    val confidence: Confidence,
    val category: IssueCategory,
    val source: IssueSource = IssueSource.STATIC_ANALYSIS,

    val filePath: String,
    val line: Int,
    val column: Int,

    val className: String?,
    val functionName: String?,

    val fingerprint: String,

    val ownerLifetime: Lifetime? = null,
    val referencedLifetime: Lifetime? = null,
    val evidence: String? = null,
    val riskExplanation: String? = null,
    val confidenceReason: String? = null,

    val hasQuickFix: Boolean = false,
    val hasAiFix: Boolean = true,
    val suggestedFix: String? = null
) {
    companion object {
        fun createFingerprint(
            ruleId: String,
            filePath: String,
            className: String?,
            symbolName: String?,
            extraContext: String? = null
        ): String {
            // We include ruleId and className as core semantic identity.
            // We use symbolName to distinguish findings in the same class/file.
            // We exclude line numbers to survive edits.
            // We include extraContext for cases where the same symbol has multiple distinct leaks.
            return "$ruleId:${className ?: ""}:${symbolName ?: ""}:${extraContext ?: ""}"
        }
    }
}

enum class Severity {
    HIGH,
    MEDIUM,
    LOW
}

enum class Confidence {
    HIGH,
    MEDIUM,
    LOW
}

enum class IssueSource {
    STATIC_ANALYSIS,
    HEAP_ANALYSIS
}

enum class IssueCategory {
    CONTEXT_RETENTION,
    LIFECYCLE,
    COMPOSE,
    COROUTINE,
    FLOW,
    HILT,
    CALLBACK,
    LISTENER,
    HANDLER,
    VIEW,
    FRAGMENT,
    VIEWMODEL,
    NAVIGATION,
    BACKGROUND_WORK,
    RESOURCE,
    OTHER
}
