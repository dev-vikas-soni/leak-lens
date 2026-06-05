package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.github.devvikassoni.leaklens.services.LeakLensProjectService
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType
import org.jetbrains.uast.UClass

/**
 * Shared utility functions for LeakLens inspections.
 * Provides robust type checking and unified live issue reporting.
 */
object LeakLensInspectionUtils {

    /**
     * Reports a live issue to the LeakLens tool window while typing.
     */
    fun reportLiveIssue(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        inspectionName: String,
        fileIssues: List<LeakInfo>
    ) {
        if (isOnTheFly) {
            LeakLensProjectService.getInstance(holder.project)
                .updateLiveIssues(holder.file.virtualFile.path, inspectionName, fileIssues)
        }
    }

    /**
     * Checks if a class is an Activity, Fragment, or similar Android component.
     */
    fun isActivityOrFragment(uClass: UClass): Boolean {
        return isActivity(uClass) || isFragment(uClass)
    }

    fun isActivity(uClass: UClass): Boolean {
        return checkSuperTypes(uClass.javaPsi, "Activity")
    }

    fun isFragment(uClass: UClass): Boolean {
        return checkSuperTypes(uClass.javaPsi, "Fragment")
    }

    /**
     * Checks if a type is an Activity, Fragment, View, or Context.
     */
    fun isActivityOrFragmentType(type: PsiType): Boolean {
        val text = type.canonicalText
        return text.contains("Activity") ||
                text.contains("Fragment") ||
                text.contains("android.app.Activity") ||
                text.contains("androidx.fragment.app.Fragment") ||
                text.contains("androidx.appcompat.app.AppCompatActivity")
    }

    /**
     * Checks if a type is a View or ViewBinding.
     */
    fun isViewOrBindingType(type: PsiType): Boolean {
        val name = type.canonicalText
        return name.contains("View") || name.contains("Binding") ||
                name.contains("android.widget.") || name.contains("android.view.")
    }

    /**
     * Resolves the line number for a PSI element.
     */
    fun getLineNumber(element: com.intellij.psi.PsiElement): Int {
        val document = element.containingFile.viewProvider.document ?: return 0
        return document.getLineNumber(element.textRange.startOffset) + 1
    }

    private fun checkSuperTypes(psiClass: PsiClass, target: String): Boolean {
        // 1. Resolved superclass chain
        var current = psiClass.superClass
        while (current != null) {
            val name = current.qualifiedName ?: ""
            if (name.contains(target)) return true
            current = current.superClass
        }
        
        // 2. Fallback: Check raw extends/implements list (handles mocks/stub files)
        psiClass.extendsList?.referenceElements?.forEach { ref ->
            if (ref.text.contains(target)) return true
        }
        
        return false
    }
}
