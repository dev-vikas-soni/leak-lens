package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Quick-fix that replaces a Context reference with its applicationContext.
 * Handles both fields (Workers) and method arguments (Singletons).
 */
class UseApplicationContextFix(private val fieldName: String? = null) : LocalQuickFix {

    override fun getName(): String =
        if (fieldName != null) "LeakLens: Use applicationContext instead of '$fieldName'" else "LeakLens: Use applicationContext"

    override fun getFamilyName(): String = "LeakLens Context Fixes"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return

        when {
            element.language.id == "kotlin" -> applyKotlinFix(project, element)
            else -> applyJavaFix(project, element)
        }
    }

    private fun applyKotlinFix(project: Project, element: com.intellij.psi.PsiElement) {
        val factory = KtPsiFactory(project)

        // Check if it's a property/field (Worker case)
        var current: com.intellij.psi.PsiElement? = element
        while (current != null && current !is KtProperty && current !is org.jetbrains.kotlin.psi.KtClass) {
            current = current.parent
        }

        if (current is KtProperty && fieldName != null) {
            val property = current
            val newPropText =
                "${property.modifierList?.text?.let { "$it " } ?: ""}val $fieldName: android.content.Context get() = applicationContext"
            val newProperty = factory.createProperty(newPropText)

            WriteCommandAction.runWriteCommandAction(project, "Use applicationContext", null, {
                property.replace(newProperty)
            })
        } else {
            // Expression case (Singleton argument)
            val newExpr = factory.createExpression("${element.text}.applicationContext")
            WriteCommandAction.runWriteCommandAction(project, "Use applicationContext", null, {
                element.replace(newExpr)
            })
        }
    }

    private fun applyJavaFix(project: Project, element: com.intellij.psi.PsiElement) {
        val factory = com.intellij.psi.JavaPsiFacade.getElementFactory(project)

        if (fieldName != null && element is com.intellij.psi.PsiField) {
            val comment = factory.createCommentFromText(
                "// LeakLens: Use getApplicationContext() instead of storing '$fieldName'",
                element
            )
            WriteCommandAction.runWriteCommandAction(project, "Use applicationContext", null, {
                element.parent?.addBefore(comment, element)
            })
        } else {
            val newExpr = factory.createExpressionFromText(
                element.text + ".getApplicationContext()",
                element.parent
            )
            WriteCommandAction.runWriteCommandAction(project, "Use applicationContext", null, {
                element.replace(newExpr)
            })
        }
    }
}
