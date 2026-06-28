package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Quick-fix that wraps an unsafe Flow.collect() / collectLatest() call with the
 * lifecycle-safe `repeatOnLifecycle(Lifecycle.State.STARTED) { ... }` block.
 *
 * Before:
 *   lifecycleScope.launch {
 *       myFlow.collect { ... }
 *   }
 *
 * After:
 *   lifecycleScope.launch {
 *       repeatOnLifecycle(Lifecycle.State.STARTED) {
 *           myFlow.collect { ... }
 *       }
 *   }
 */
class WrapWithRepeatOnLifecycleFix : LocalQuickFix {

    override fun getName(): String = "LeakLens: Wrap with repeatOnLifecycle"
    override fun getFamilyName(): String = "LeakLens Flow Fixes"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        // descriptor.psiElement is the method identifier (e.g. `collect` keyword)
        // Walk up to find the full call expression: receiver.collect { ... }
        val callIdentifier: PsiElement = descriptor.psiElement
        val collectCall: KtExpression = findCollectCallExpression(callIdentifier) ?: return

        val factory = KtPsiFactory(project)
        val originalText = collectCall.text

        // Build the replacement: repeatOnLifecycle(Lifecycle.State.STARTED) { <original> }
        val wrappedText = "repeatOnLifecycle(Lifecycle.State.STARTED) {\n    $originalText\n}"
        val newExpression: KtExpression = factory.createExpression(wrappedText)

        WriteCommandAction.runWriteCommandAction(project, "Wrap with repeatOnLifecycle", null, {
            collectCall.replace(newExpression)
        })
    }

    /**
     * From the highlighted element (method identifier), walk up to find
     * the enclosing KtDotQualifiedExpression (e.g. `myFlow.collect { }`)
     * or the KtCallExpression if it's a direct call.
     */
    private fun findCollectCallExpression(element: PsiElement): KtExpression? {
        var current: PsiElement? = element.parent
        while (current != null) {
            // e.g. myFlow.collect { ... }  — the dot-qualified is what we want to wrap
            if (current is KtDotQualifiedExpression) {
                val selector = current.selectorExpression
                if (selector is KtCallExpression) {
                    val name = selector.calleeExpression?.text
                    if (name == "collect" || name == "collectLatest") {
                        return current
                    }
                }
            }
            // Stop at lambda boundaries – we don't want to go beyond the enclosing lambda body
            if (current is KtCallExpression) break
            current = current.parent
        }
        return null
    }
}
