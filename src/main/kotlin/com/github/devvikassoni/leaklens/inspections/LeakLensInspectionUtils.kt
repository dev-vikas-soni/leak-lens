package com.github.devvikassoni.leaklens.inspections

import com.intellij.psi.PsiElement

/** Shared helpers for LeakLens inspections. Type checks live in [AndroidTypes]. */
object LeakLensInspectionUtils {

    /** Resolves the 1-based line number for a PSI element, or 0 if unavailable. */
    fun getLineNumber(element: PsiElement): Int {
        val document = element.containingFile?.viewProvider?.document ?: return 0
        val offset = element.textRange?.startOffset ?: return 0
        if (offset > document.textLength) return 0
        return document.getLineNumber(offset) + 1
    }
}
