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
        } else if (parent.javaClass.name.endsWith("KtClass") || parent.javaClass.name.endsWith("KtObjectDeclaration")) {
            try {
                val fqNameMethod = parent.javaClass.getMethod("getFqName")
                val fqName = fqNameMethod.invoke(parent)
                if (fqName != null) {
                    val asStringMethod = fqName.javaClass.getMethod("asString")
                    qualifiedName = asStringMethod.invoke(fqName) as? String
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        
        if (qualifiedName == null) return null

        val project = element.project
        val service = LeakLensProjectService.getInstance(project)
        
        // Fast O(1) check
        if (!service.retainedClassNames.contains(qualifiedName) && 
            !service.referenceChainClassNames.contains(qualifiedName)) {
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

