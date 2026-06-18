package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.ai.AiUtils
import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.github.devvikassoni.leaklens.model.LeakTraceReference
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project

/**
 * A quick fix that appears as a lightbulb in the editor, allowing users to
 * instantly get an AI-powered fix suggestion for the detected leak.
 */
class AskGeminiFix(
    private val description: String,
    private val className: String,
    private val line: Int
) : LocalQuickFix {
    override fun getName(): String = "LeakLens: Ask AI for a fix"
    override fun getFamilyName(): String = "LeakLens AI Fixes"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val dummyLeak = LeakInfo(
            signature = "static_scan_${className}_$line",
            shortDescription = description,
            leakTrace = "Static scan finding in $className at line $line",
            retainedObjectClassName = className,
            retainedByteSize = 0L,
            retainedObjectCount = 1,
            severity = LeakSeverity.WARNING,
            referenceChain = emptyList<LeakTraceReference>(),
            suggestedFix = "Use the 'Ask Gemini AI' button to get a full analysis."
        )
        AiUtils.askGemini(project, dummyLeak)
    }
}
