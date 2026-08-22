package com.github.devvikassoni.leaklens.gutter

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.github.devvikassoni.leaklens.services.LeakLensProjectService
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import javax.swing.Icon

/**
 * Provides gutter icons on classes/fields that appear in active leak traces.
 * Shows warning markers like breakpoint icons on leak-prone lines.
 */
class LeakGutterLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Check if element is an identifier (PsiIdentifier in Java, LeafPsiElement in Kotlin)
        // We can just check if it's the name identifier of its parent
        val parent = element.parent
        if (parent !is com.intellij.psi.PsiNameIdentifierOwner || parent.nameIdentifier != element) {
            return null
        }

        // Extract qualified name safely for both Java and Kotlin without direct plugin dependencies
        var qualifiedName: String? = null
        if (parent is com.intellij.psi.PsiClass) {
            qualifiedName = parent.qualifiedName
        } else if (parent is org.jetbrains.kotlin.psi.KtClassOrObject) {
            qualifiedName = parent.fqName?.asString()
        }

        if (qualifiedName == null) return null

        val project = element.project
        val service = LeakLensProjectService.getInstance(project)
        val virtualFile = element.containingFile.virtualFile
        val filePath = virtualFile?.path

        // 1. Check for live issues (static analysis)
        if (filePath != null) {
            val document = element.containingFile.viewProvider.document
            if (document != null) {
                val line = document.getLineNumber(element.textRange.startOffset) + 1
                if (service.isLineLeaky(filePath, line)) {
                    val liveIssues = service.getLiveIssuesForFile(filePath)
                    val issue = liveIssues.find { it.signature.endsWith("_$line") }
                    if (issue != null) {
                        val tooltip =
                            "⚠️ LeakLens (Live): ${issue.shortDescription}\n\n${issue.suggestedFix ?: ""}"
                        return LineMarkerInfo(
                            element,
                            element.textRange,
                            AllIcons.General.Warning,
                            { tooltip },
                            { _, _ ->
                                com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
                                    .getToolWindow("LeakLens")?.show()
                            },
                            GutterIconRenderer.Alignment.LEFT,
                            { tooltip }
                        )
                    }
                }
            }
        }

        // 2. Check for heap analysis leaks (existing logic)
        // Fast O(1) check
        if (!service.retainedClassNames.contains(qualifiedName) &&
            !service.referenceChainClassNames.contains(qualifiedName)
        ) {
            return null
        }

        val leaks = service.leaks.value

        // Check if this class is the retained (leaking) object in any leak
        val matchingLeak = leaks.find { it.retainedObjectClassName == qualifiedName }
        if (matchingLeak != null) {
            val icon = getIconForSeverity(matchingLeak.severity)
            val tooltip = buildTooltip(matchingLeak)
            return LineMarkerInfo(
                element,
                element.textRange,
                icon,
                { tooltip },
                { _, _ ->
                    com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
                        .getToolWindow("LeakLens")?.show()
                },
                GutterIconRenderer.Alignment.LEFT,
                { tooltip }
            )
        }

        // Check if this class appears in any reference chain
        val referenceLeak = leaks.find { leak ->
            leak.referenceChain.any { ref -> ref.owningClassName == qualifiedName }
        }
        if (referenceLeak != null) {
            val tooltip = "⚠️ This class appears in a leak trace: ${referenceLeak.shortDescription}"
            return LineMarkerInfo(
                element,
                element.textRange,
                AllIcons.General.Warning,
                { tooltip },
                null,
                GutterIconRenderer.Alignment.LEFT,
                { tooltip }
            )
        }

        return null
    }

    private fun getIconForSeverity(severity: LeakSeverity): Icon {
        return when (severity) {
            LeakSeverity.CRITICAL -> AllIcons.General.Error
            LeakSeverity.WARNING -> AllIcons.General.Warning
            LeakSeverity.LIBRARY_LEAK -> AllIcons.General.Information
        }
    }

    private fun buildTooltip(leak: LeakInfo): String {
        return buildString {
            append("🔍 LeakLens: Memory Leak Detected\n")
            append("Severity: ${leak.severity.displayName}\n")
            append("${leak.shortDescription}\n")
            append("Retained: ${leak.retainedByteSize / 1024} KB")
            if (leak.suggestedFix != null) {
                append("\n\n💡 Fix: ${leak.suggestedFix}")
            }
        }
    }
}
