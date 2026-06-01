package com.github.devvikassoni.leaklens.gutter

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.github.devvikassoni.leaklens.services.LeakLensProjectService
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import javax.swing.Icon

/**
 * Provides gutter icons on classes/fields that appear in active leak traces.
 * Shows warning markers like breakpoint icons on leak-prone lines.
 */
class LeakGutterLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Only process class name identifiers
        if (element !is PsiIdentifier) return null
        val parent = element.parent
        if (parent !is PsiClass) return null

        val project = element.project
        val service = LeakLensProjectService.getInstance(project)
        val leaks = service.leaks.value
        if (leaks.isEmpty()) return null

        val qualifiedName = parent.qualifiedName ?: return null

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
                null,
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

