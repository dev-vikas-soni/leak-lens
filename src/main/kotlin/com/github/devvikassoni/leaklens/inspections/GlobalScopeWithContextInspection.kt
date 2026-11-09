package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Detects `GlobalScope.launch/async` inside Activities, Fragments, Views and ViewModels. Such
 * coroutines are never cancelled and keep the enclosing component alive.
 */
class GlobalScopeWithContextInspection : LeakLensInspectionBase() {

    override fun getDisplayName() = "GlobalScope coroutine may leak Activity/Context"
    override fun getShortName() = "LeakLensGlobalScopeWithContext"

    override fun buildLeakVisitor(reporter: LeakReporter): PsiElementVisitor =
        UastHintedVisitorAdapter.create(
            reporter.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val methodName = node.methodName
                    if (methodName != "launch" && methodName != "async") return false
                    if (!isGlobalScopeReceiver(node)) return false
                    val owner = node.getContainingUClass()?.javaPsi ?: return false
                    val replacement = replacementScope(owner) ?: return false

                    val anchor = node.methodIdentifier?.sourcePsi ?: node.sourcePsi ?: return false
                    val ownerName = owner.name ?: "component"
                    val fix = replacement.second?.let { ReplaceGlobalScopeFix(replacement.first, it) }
                        ?.takeIf { node.sourcePsi is KtCallExpression }
                    reporter.report(
                        anchor,
                        "LeakLens: GlobalScope.$methodName in $ownerName is never cancelled and can leak it. " +
                            "Use ${replacement.first}.",
                        Finding(
                            key = LeakReporter.memberKey(node, "GlobalScope.$methodName"),
                            title = "GlobalScope.$methodName in $ownerName",
                            location = "Launched in GlobalScope",
                            retainedClass = owner.qualifiedName ?: ownerName,
                            suggestedFix = "Use ${replacement.first} so the coroutine is cancelled with its owner.",
                        ),
                        *listOfNotNull(fix).toTypedArray(),
                    )
                    return false
                }
            },
            arrayOf(UCallExpression::class.java)
        )

    private fun isGlobalScopeReceiver(node: UCallExpression): Boolean {
        val receiverClass = AndroidTypes.classOf(AndroidTypes.receiverTypeOf(node))
        if (receiverClass?.qualifiedName == AndroidTypes.GLOBAL_SCOPE) return true
        return when (val resolved = AndroidTypes.resolveRef(node.receiver)) {
            is PsiClass -> resolved.qualifiedName == AndroidTypes.GLOBAL_SCOPE
            is KtObjectDeclaration -> resolved.fqName?.asString() == AndroidTypes.GLOBAL_SCOPE
            else -> false
        }
    }

    /** Scope to suggest and the import providing it (null import = no automatic fix). */
    private fun replacementScope(owner: PsiClass): Pair<String, String?>? = when {
        AndroidTypes.isLifecycleComponent(owner) -> "lifecycleScope" to "androidx.lifecycle.lifecycleScope"
        AndroidTypes.isViewModel(owner) -> "viewModelScope" to "androidx.lifecycle.viewModelScope"
        AndroidTypes.isView(owner) -> "findViewTreeLifecycleOwner()?.lifecycleScope" to null
        else -> null
    }

    private class ReplaceGlobalScopeFix(private val scope: String, private val import: String) : LocalQuickFix {
        override fun getName() = "Replace GlobalScope with $scope"
        override fun getFamilyName() = "LeakLens: Replace GlobalScope"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val call = PsiTreeUtil.getParentOfType(descriptor.psiElement, KtCallExpression::class.java) ?: return
            val qualified = (call.parent as? KtDotQualifiedExpression)
                ?.takeIf { it.selectorExpression == call }
                ?: return
            qualified.receiverExpression.replace(KtPsiFactory(project).createExpression(scope))
            LeakFixUtils.addKotlinImports(qualified.containingFile, import)
        }
    }
}
