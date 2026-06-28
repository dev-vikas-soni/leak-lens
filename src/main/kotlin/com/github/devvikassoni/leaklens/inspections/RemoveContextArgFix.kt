package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * Quick-fix that removes the Context/Activity argument from a ViewModel method call
 * inside a @Composable function.
 *
 * Before:
 *   viewModel.setContext(context)
 *
 * After:
 *   viewModel.setContext()
 *
 * Note: The user will still need to refactor the ViewModel method signature.
 * The fix removes only the leaking argument and leaves a TODO comment.
 */
class RemoveContextArgFix : LocalQuickFix {

    override fun getName(): String = "LeakLens: Remove Context argument (prevents leak)"
    override fun getFamilyName(): String = "LeakLens Compose Fixes"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        // descriptor.psiElement is the argument expression (e.g. `context` or `this`)
        val argElement: PsiElement = descriptor.psiElement

        // Walk up to find the KtValueArgument node
        val valueArg: KtValueArgument = findValueArgument(argElement) ?: return

        // Find the parent call expression to check argument count
        val callExpression: KtCallExpression =
            valueArg.parent?.parent as? KtCallExpression ?: return
        val argList = callExpression.valueArgumentList ?: return

        WriteCommandAction.runWriteCommandAction(project, "Remove Context argument", null, {
            if (argList.arguments.size == 1) {
                // Only argument — remove it directly
                valueArg.delete()
            } else {
                // Multiple arguments — remove this one along with any trailing comma
                val indexInList = argList.arguments.indexOf(valueArg)
                if (indexInList >= 0) {
                    argList.removeArgument(indexInList)
                }
            }
        })
    }

    private fun findValueArgument(element: PsiElement): KtValueArgument? {
        var current: PsiElement? = element
        while (current != null) {
            if (current is KtValueArgument) return current
            current = current.parent
        }
        return null
    }
}
