package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.ai.AiUtils
import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project

/**
 * Copies a ready-made leak analysis prompt to the clipboard for use with Gemini in Android Studio.
 * Does not modify code, so it runs outside a write action and has no preview.
 */
class AskGeminiFix(
    private val description: String,
    private val className: String,
    private val line: Int
) : LocalQuickFix {
    override fun getName(): String = "LeakLens: Copy AI prompt for this leak"
    override fun getFamilyName(): String = "LeakLens AI Fixes"
    override fun startInWriteAction(): Boolean = false
    override fun availableInBatchMode(): Boolean = false

    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo =
        IntentionPreviewInfo.EMPTY

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val leak = LeakInfo(
            signature = "static_scan:$className",
            shortDescription = description,
            leakTrace = "Static scan finding in $className at line $line",
            retainedObjectClassName = className,
            retainedByteSize = 0L,
            retainedObjectCount = 1,
            severity = LeakSeverity.WARNING,
            referenceChain = emptyList(),
            suggestedFix = null,
            sourceLine = line,
        )
        AiUtils.askGemini(project, leak)
    }
}
