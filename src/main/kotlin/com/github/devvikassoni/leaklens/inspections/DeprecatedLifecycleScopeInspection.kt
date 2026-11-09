package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Detects the deprecated `LifecycleCoroutineScope.launchWhenX` builders, which only *suspend* the
 * coroutine in the background (keeping upstream flows and resources alive) instead of cancelling it.
 */
class DeprecatedLifecycleScopeInspection : LeakLensInspectionBase() {

    override fun getDisplayName() = "Deprecated lifecycleScope.launchWhenX causes leaks"
    override fun getShortName() = "LeakLensDeprecatedLifecycleScope"

    override fun buildLeakVisitor(reporter: LeakReporter): PsiElementVisitor =
        UastHintedVisitorAdapter.create(
            reporter.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val methodName = node.methodName ?: return false
                    val state = STATES[methodName] ?: return false
                    val method = node.resolve() ?: return false
                    if (!AndroidTypes.inherits(
                            method.containingClass,
                            AndroidTypes.LIFECYCLE_COROUTINE_SCOPE
                        )
                    ) {
                        return false
                    }

                    val anchor = node.methodIdentifier?.sourcePsi ?: node.sourcePsi ?: return false
                    val fix = migrationFixFor(node, state)
                    reporter.report(
                        anchor,
                        "LeakLens: $methodName is deprecated; it suspends instead of cancelling in the " +
                            "background, keeping collectors and resources alive. Use repeatOnLifecycle.",
                        Finding(
                            key = LeakReporter.memberKey(node, methodName),
                            title = "Deprecated $methodName used",
                            location = "$methodName call",
                            retainedClass = AndroidTypes.LIFECYCLE_COROUTINE_SCOPE,
                            suggestedFix = "lifecycleScope.launch { " +
                                "repeatOnLifecycle(Lifecycle.State.$state) { ... } }",
                        ),
                        *listOfNotNull(fix).toTypedArray(),
                    )
                    return false
                }
            },
            arrayOf(UCallExpression::class.java)
        )

    private fun migrationFixFor(node: UCallExpression, state: String): LocalQuickFix? {
        val call = node.sourcePsi as? KtCallExpression ?: return null
        if (lambdaOf(call) == null) return null
        val qualified = call.parent as? KtDotQualifiedExpression
        val receiver = qualified?.takeIf { it.selectorExpression == call }?.receiverExpression?.text
        val owner = ownerPrefix(receiver) ?: return null
        return MigrateToRepeatOnLifecycleFix(state, owner)
    }

    private class MigrateToRepeatOnLifecycleFix(private val state: String, private val owner: String) : LocalQuickFix {
        override fun getName() = "Migrate to launch { repeatOnLifecycle(Lifecycle.State.$state) }"
        override fun getFamilyName() = "LeakLens: Migrate to repeatOnLifecycle"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val call = PsiTreeUtil.getParentOfType(descriptor.psiElement, KtCallExpression::class.java) ?: return
            val lambda = lambdaOf(call) ?: return
            val qualified = (call.parent as? KtDotQualifiedExpression)?.takeIf { it.selectorExpression == call }
            val receiver = qualified?.receiverExpression?.text?.let { "$it." }.orEmpty()
            val body = lambda.bodyExpression?.text.orEmpty()
            val newText = "${receiver}launch {\n${owner}repeatOnLifecycle(Lifecycle.State.$state) {\n$body\n}\n}"
            val replaced = (qualified ?: call).replace(KtPsiFactory(project).createExpression(newText))
            LeakFixUtils.addKotlinImports(
                replaced.containingFile,
                "kotlinx.coroutines.launch",
                "androidx.lifecycle.Lifecycle",
                "androidx.lifecycle.repeatOnLifecycle",
            )
            LeakFixUtils.reformat(project, replaced)
        }
    }

    companion object {
        private val STATES = mapOf(
            "launchWhenCreated" to "CREATED",
            "launchWhenStarted" to "STARTED",
            "launchWhenResumed" to "RESUMED",
        )

        internal fun lambdaOf(call: KtCallExpression): KtLambdaExpression? =
            call.lambdaArguments.singleOrNull()?.getLambdaExpression()
                ?: call.valueArguments.lastOrNull()?.getArgumentExpression() as? KtLambdaExpression

        /**
         * `lifecycleScope` → "" and `x.lifecycleScope` → "x." so repeatOnLifecycle runs on the same
         * LifecycleOwner as the scope; null when the owner can't be derived safely.
         */
        internal fun ownerPrefix(scopeText: String?): String? = when {
            scopeText == null || scopeText == "lifecycleScope" -> ""
            scopeText.endsWith(".lifecycleScope") -> scopeText.removeSuffix("lifecycleScope")
            else -> null
        }
    }
}
