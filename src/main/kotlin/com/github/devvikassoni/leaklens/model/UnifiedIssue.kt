package com.github.devvikassoni.leaklens.model

/**
 * A unified model for displaying both static and heap analysis findings in the Dashboard.
 */
data class UnifiedIssue(
    val id: String,
    val title: String,
    val description: String,
    val severity: LeakSeverity,
    val confidence: Confidence,
    val source: IssueSource,
    val filePath: String?,
    val line: Int,
    val className: String,
    val suggestedFix: String?,
    val originalIssue: Any // Holds either LeakInfo or StaticAnalysisIssue
)

fun LeakInfo.toUnifiedIssue(): UnifiedIssue {
    return UnifiedIssue(
        id = this.signature,
        title = this.shortDescription,
        description = this.leakTrace,
        severity = this.severity,
        confidence = Confidence.HIGH, // Heap leaks are high confidence by default
        source = IssueSource.HEAP_ANALYSIS,
        filePath = this.referenceChain.firstOrNull()?.sourceFile,
        line = this.referenceChain.firstOrNull()?.lineNumber ?: 0,
        className = this.retainedObjectClassName,
        suggestedFix = this.suggestedFix,
        originalIssue = this
    )
}

fun StaticAnalysisIssue.toUnifiedIssue(): UnifiedIssue {
    return UnifiedIssue(
        id = this.fingerprint,
        title = this.title,
        description = this.description,
        severity = when (this.severity) {
            Severity.HIGH -> LeakSeverity.CRITICAL
            Severity.MEDIUM -> LeakSeverity.WARNING
            Severity.LOW -> LeakSeverity.WARNING
        },
        confidence = this.confidence,
        source = IssueSource.STATIC_ANALYSIS,
        filePath = this.filePath,
        line = this.line,
        className = this.className ?: "Unknown",
        suggestedFix = this.suggestedFix,
        originalIssue = this
    )
}
