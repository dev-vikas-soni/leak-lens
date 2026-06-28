package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Quick-fix that replaces a stored Context field with applicationContext.
 *
 * For Kotlin:
 *   private val ctx: Context  →  private val ctx: Context get() = applicationContext
 *
 * For Java (adds a comment):
 *   // LeakLens: Replace ctx with getApplicationContext() — Context field removed
 */
class UseApplicationContextFix(private val fieldName: String) : LocalQuickFix {

    override fun getName(): String = "LeakLens: Replace '$fieldName' with applicationContext"
    override fun getFamilyName(): String = "LeakLens Worker Fixes"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement

        when {
            // Kotlin: find the KtProperty and convert to a computed property
            element.language.id == "kotlin" -> applyKotlinFix(project, element)
            else -> applyJavaFix(project, element)
        }
    }

    private fun applyKotlinFix(project: Project, element: com.intellij.psi.PsiElement) {
        // Walk up to the KtProperty
        var current: com.intellij.psi.PsiElement? = element
        while (current != null && current !is KtProperty) {
            current = current.parent
        }
        val property = current as? KtProperty ?: return

        val factory = KtPsiFactory(project)
        // Replace the stored field with a computed property backed by applicationContext
        val newPropText =
            "${property.modifierList?.text?.let { "$it " } ?: ""}val $fieldName: android.content.Context get() = applicationContext"
        val newProperty = factory.createProperty(newPropText)

        WriteCommandAction.runWriteCommandAction(project, "Use applicationContext", null, {
            property.replace(newProperty)
        })
    }

    private fun applyJavaFix(project: Project, element: com.intellij.psi.PsiElement) {
        val factory = com.intellij.psi.JavaPsiFacade.getElementFactory(project)
        val comment = factory.createCommentFromText(
            "// LeakLens: Use getApplicationContext() instead of storing '$fieldName'",
            element
        )
        WriteCommandAction.runWriteCommandAction(project, "Use applicationContext", null, {
            element.parent?.addBefore(comment, element)
        })
    }
}
