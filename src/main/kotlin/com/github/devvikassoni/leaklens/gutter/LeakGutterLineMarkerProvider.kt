package com.github.devvikassoni.leaklens.gutter

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.github.devvikassoni.leaklens.services.LeakLensProjectService
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * Provides gutter icons on classes/fields/statements that appear in active leak traces or static findings.
 * Shows warning markers like breakpoint icons on leak-prone lines.
 */
class LeakGutterLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element is PsiWhiteSpace) return null
        val virtualFile = element.containingFile?.virtualFile ?: return null
        val filePath = virtualFile.path
        val project = element.project
        val service = LeakLensProjectService.getInstance(project)

        val document = element.containingFile.viewProvider.document ?: return null
        val startOffset = element.textRange.startOffset
        val line = document.getLineNumber(startOffset) + 1

        // 1. Check for live issues / static findings on this line
        if (service.isLineLeaky(filePath, line)) {
            val lineStartOffset = document.getLineStartOffset(line - 1)
            val isFirstLeafOnLine = element.firstChild == null && isFirstTokenOnLine(element, document, lineStartOffset)

            if (isFirstLeafOnLine) {
                val staticIssues = service.getStaticIssuesForFile(filePath)
                val staticIssue = staticIssues.find { it.line == line }

                if (staticIssue != null) {
                    val tooltip =
                        "⚠️ LeakLens Static: ${staticIssue.title}\n\n${staticIssue.description}\n\n💡 Fix: ${staticIssue.suggestedFix ?: "No automatic fix available."}"
                    return createWarningMarker(element, tooltip, project, AllIcons.General.Warning)
                }

                val liveIssues = service.getLiveIssuesForFile(filePath)
                val issue = liveIssues.find { it.sourceLine == line }
                if (issue != null) {
                    val tooltip =
                        "⚠️ LeakLens: ${issue.shortDescription}\n\n${issue.suggestedFix ?: ""}"
                    return createWarningMarker(element, tooltip, project, AllIcons.General.Warning)
                }
            }
        }

        // 2. Check for heap analysis leaks (requires class qualified name identifier)
        val parent = element.parent
        if (parent is com.intellij.psi.PsiNameIdentifierOwner && parent.nameIdentifier == element) {
            var qualifiedName: String? = null
            if (parent is com.intellij.psi.PsiClass) {
                qualifiedName = parent.qualifiedName
            } else if (parent is org.jetbrains.kotlin.psi.KtClassOrObject) {
                qualifiedName = parent.fqName?.asString()
            }

            if (qualifiedName != null) {
                if (service.retainedClassNames.contains(qualifiedName) ||
                    service.referenceChainClassNames.contains(qualifiedName)
                ) {
                    val leaks = service.leaks.value

                    // Check if this class is the retained (leaking) object in any leak
                    val matchingLeak = leaks.find { it.retainedObjectClassName == qualifiedName }
                    if (matchingLeak != null) {
                        val icon = getIconForSeverity(matchingLeak.severity)
                        val tooltip = buildTooltip(matchingLeak)
                        return createWarningMarker(element, tooltip, project, icon)
                    }

                    // Check if this class appears in any reference chain
                    val referenceLeak = leaks.find { leak ->
                        leak.referenceChain.any { ref -> ref.owningClassName == qualifiedName }
                    }
                    if (referenceLeak != null) {
                        val tooltip = "⚠️ This class appears in a leak trace: ${referenceLeak.shortDescription}"
                        return createWarningMarker(element, tooltip, project, AllIcons.General.Warning)
                    }
                }
            }
        }

        return null
    }

    private fun createWarningMarker(
        element: PsiElement,
        tooltipText: String,
        project: Project,
        markerIcon: Icon
    ): LineMarkerInfo<PsiElement> {
        return LineMarkerInfo(
            element,
            element.textRange,
            markerIcon,
            object : com.intellij.util.Function<PsiElement, String> {
                override fun `fun`(param: PsiElement): String = tooltipText
            },
            object : GutterIconNavigationHandler<PsiElement> {
                override fun navigate(e: MouseEvent?, elt: PsiElement?) {
                    com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
                        .getToolWindow("LeakLens")?.show()
                }
            },
            GutterIconRenderer.Alignment.LEFT
        )
    }

    private fun isFirstTokenOnLine(element: PsiElement, document: Document, lineStartOffset: Int): Boolean {
        val elementOffset = element.textRange.startOffset
        if (elementOffset < lineStartOffset || elementOffset >= document.textLength) return false
        val textBetween = document.getText(TextRange(lineStartOffset, elementOffset))
        return textBetween.isBlank()
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
