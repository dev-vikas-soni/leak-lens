package com.github.devvikassoni.leaklens.compat

import com.intellij.psi.PsiElement
import org.jetbrains.uast.UElement
import org.jetbrains.uast.toUElement

/**
 * Facade for UAST operations to handle potential differences in UAST versions.
 */
object UastFacade {
    fun getUElement(element: PsiElement): UElement? {
        return element.toUElement()
    }

    /**
     * Checks if UAST is available for the given language.
     */
    fun isSupported(element: PsiElement): Boolean {
        return try {
            element.toUElement() != null
        } catch (_: Exception) {
            false
        }
    }
}
