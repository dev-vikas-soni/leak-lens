package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.inspections.registry.LeakLensRule
import com.github.devvikassoni.leaklens.model.Severity
import com.github.devvikassoni.leaklens.model.StaticAnalysisIssue
import com.github.devvikassoni.leaklens.reporting.StaticAnalysisReporterImpl
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.Key

/**
 * Base class for all LeakLens static inspections to ensure consistent issue collection and reporting.
 */
abstract class BaseLeakLensInspection(val rule: LeakLensRule) : LocalInspectionTool() {

    private val ISSUES_KEY =
        Key.create<MutableList<StaticAnalysisIssue>>("LeakLens.Issues.${rule.id}")

    override fun getGroupDisplayName() = "LeakLens"
    override fun getDisplayName() = rule.title
    override fun getShortName() = rule.id.replace("-", "")

    protected fun getIssueList(holder: ProblemsHolder): MutableList<StaticAnalysisIssue> {
        var issues = holder.file.getUserData(ISSUES_KEY)
        if (issues == null) {
            issues = mutableListOf()
            holder.file.putUserData(ISSUES_KEY, issues)
        }
        return issues
    }

    override fun inspectionFinished(
        session: LocalInspectionToolSession,
        problemsHolder: ProblemsHolder
    ) {
        val issues = problemsHolder.file.getUserData(ISSUES_KEY) ?: return
        val file = problemsHolder.file.virtualFile ?: return
        StaticAnalysisReporterImpl.getInstance(problemsHolder.project)
            .updateFile(file, rule.id, issues)

        // Clean up
        problemsHolder.file.putUserData(ISSUES_KEY, null)
    }

    protected fun registerLeak(
        holder: ProblemsHolder,
        element: com.intellij.psi.PsiElement,
        description: String,
        confidence: com.github.devvikassoni.leaklens.model.Confidence = rule.defaultConfidence,
        severity: com.github.devvikassoni.leaklens.model.Severity = rule.defaultSeverity,
        className: String? = null,
        functionName: String? = null,
        symbolName: String? = null,
        suggestedFix: String? = null,
        ownerLifetime: com.github.devvikassoni.leaklens.model.Lifetime? = null,
        referencedLifetime: com.github.devvikassoni.leaklens.model.Lifetime? = null,
        evidence: String? = null,
        riskExplanation: String? = null,
        confidenceReason: String? = null
    ) {
        val highlightType = when (severity) {
            Severity.HIGH -> com.intellij.codeInspection.ProblemHighlightType.ERROR
            Severity.MEDIUM -> com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            Severity.LOW -> com.intellij.codeInspection.ProblemHighlightType.INFORMATION
        }

        val fullDescription = if (ownerLifetime != null && referencedLifetime != null) {
            "$description ($ownerLifetime -> $referencedLifetime)"
        } else description

        holder.registerProblem(
            element,
            "LeakLens: $fullDescription",
            highlightType,
            *getQuickFixes(element),
            AskGeminiFix(
                fullDescription,
                className ?: "Unknown",
                LeakLensInspectionUtils.getLineNumber(element),
                ownerLifetime,
                referencedLifetime,
                evidence,
                riskExplanation
            )
        )

        val filePath = holder.file.virtualFile?.path ?: ""
        val line = LeakLensInspectionUtils.getLineNumber(element)

        getIssueList(holder).add(
            StaticAnalysisIssue(
                ruleId = rule.id,
                title = rule.title,
                description = description,
                severity = severity,
                confidence = confidence,
                category = rule.category,
                filePath = filePath,
                line = line,
                column = 0,
                className = className,
                functionName = functionName,
                fingerprint = StaticAnalysisIssue.createFingerprint(
                    ruleId = rule.id,
                    filePath = filePath,
                    className = className,
                    symbolName = symbolName ?: functionName,
                    extraContext = if (symbolName == null && functionName == null) line.toString() else null
                ),
                hasQuickFix = true,
                suggestedFix = suggestedFix,
                ownerLifetime = ownerLifetime,
                referencedLifetime = referencedLifetime,
                evidence = evidence,
                riskExplanation = riskExplanation,
                confidenceReason = confidenceReason
            )
        )
    }

    protected open fun getQuickFixes(element: com.intellij.psi.PsiElement): Array<com.intellij.codeInspection.LocalQuickFix> {
        return emptyArray()
    }
}
