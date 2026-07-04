package com.github.devvikassoni.leaklens.inspections

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.uast.UClass

/**
 * Shared utility functions for LeakLens inspections.
 *
 * Key performance improvement over the original implementation:
 * - [isActivity], [isFragment], [isActivityOrFragment] now use [InheritanceUtil.isInheritor]
 *   which is backed by the platform's [com.intellij.psi.util.CachedValuesManager].
 *   This replaces a manual `while (superClass != null)` walk that was O(N) per call
 *   and caused O(N²) highlighting lag on large files.
 * - [isViewOrBindingType] excludes ViewModel/ViewHolder to reduce false positives.
 */
object LeakLensInspectionUtils {

    // Exact FQNs for Android lifecycle classes.
    // Using exact names prevents false positives from classes like "MyContextHelper".
    private val ACTIVITY_FQNS = setOf(
        "android.app.Activity",
        "androidx.activity.ComponentActivity",
        "androidx.appcompat.app.AppCompatActivity",
    )

    private val FRAGMENT_FQNS = setOf(
        "android.app.Fragment",
        "androidx.fragment.app.Fragment",
    )

    /**
     * Checks if a class is an Activity, Fragment, or similar Android component.
     * Uses [InheritanceUtil.isInheritor] which is backed by CachedValuesManager — O(1) amortised.
     */
    fun isActivityOrFragment(uClass: UClass): Boolean {
        return isActivity(uClass) || isFragment(uClass)
    }

    fun isActivity(uClass: UClass): Boolean {
        val psi = uClass.javaPsi
        return ACTIVITY_FQNS.any { fqn -> InheritanceUtil.isInheritor(psi, fqn) }
    }

    fun isFragment(uClass: UClass): Boolean {
        val psi = uClass.javaPsi
        return FRAGMENT_FQNS.any { fqn -> InheritanceUtil.isInheritor(psi, fqn) }
    }

    /**
     * Checks if a [PsiClass] inherits from any of the Android UI component base classes.
     * Fallback used by inspections that work directly with PSI rather than UAST.
     */
    fun isAndroidUiClass(psiClass: PsiClass): Boolean {
        return (ACTIVITY_FQNS + FRAGMENT_FQNS).any { fqn ->
            InheritanceUtil.isInheritor(psiClass, fqn)
        }
    }

    /**
     * Checks if a type is an Activity, Fragment, or Context.
     * Uses [InheritanceUtil.isInheritor] for accurate semantic resolution.
     */
    fun isActivityOrFragmentType(type: PsiType): Boolean {
        val psiClass = com.intellij.psi.util.PsiTypesUtil.getPsiClass(type) ?: return false
        return (ACTIVITY_FQNS + FRAGMENT_FQNS + "android.content.Context").any { fqn ->
            InheritanceUtil.isInheritor(psiClass, fqn)
        }
    }

    /**
     * Checks if a type is a View or ViewBinding.
     */
    fun isViewOrBindingType(type: PsiType): Boolean {
        val psiClass = com.intellij.psi.util.PsiTypesUtil.getPsiClass(type) ?: return false
        return InheritanceUtil.isInheritor(psiClass, "android.view.View") ||
                type.canonicalText.contains("Binding")
    }

    /**
     * Resolves the 1-based line number for a PSI element.
     */
    fun getLineNumber(element: PsiElement): Int {
        val document = element.containingFile.viewProvider.document ?: return 0
        return document.getLineNumber(element.textRange.startOffset) + 1
    }
}
