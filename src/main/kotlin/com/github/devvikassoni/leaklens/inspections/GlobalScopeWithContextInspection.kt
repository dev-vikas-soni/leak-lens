package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.inspections.engine.LifetimeEngine
import com.github.devvikassoni.leaklens.inspections.registry.RuleRegistry
import com.github.devvikassoni.leaklens.model.Lifetime
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Detects coroutines launched in long-lived scopes that capture Context/Activity references.
 */
class GlobalScopeWithContextInspection :
    BaseLeakLensInspection(RuleRegistry.GLOBAL_SCOPE_WITH_CONTEXT) {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val methodName = node.methodName
                    if (methodName !in listOf("launch", "async")) return false

                    val receiver = node.receiver ?: return false
                    val scopeLifetime = LifetimeEngine.getScopeLifetime(receiver)
                    if (scopeLifetime == Lifetime.UNKNOWN) return false

                    node.valueArguments.forEach { arg ->
                        arg.accept(object : AbstractUastVisitor() {
                            override fun visitElement(node: UElement): Boolean {
                                if (node is UExpression) {
                                    val type =
                                        node.getExpressionType() ?: return super.visitElement(node)

                                    val relationship =
                                        LifetimeEngine.analyzeAsynchronousRelationship(
                                            scopeLifetime = scopeLifetime,
                                            referencedType = type,
                                            evidenceSource = "Coroutine $methodName block captures ${type.presentableText}"
                                        )

                                    if (relationship.isRisky) {
                                        val elementToHighlight =
                                            node.sourcePsi ?: return super.visitElement(node)
                                        registerLeak(
                                            holder = holder,
                                            element = elementToHighlight,
                                            description = rule.description,
                                            className = node.getContainingUClass()?.javaPsi?.qualifiedName,
                                            functionName = methodName,
                                            symbolName = "coroutine_capture",
                                            suggestedFix = "Use a scope bound to the captured object's lifecycle.",
                                            ownerLifetime = relationship.ownerLifetime,
                                            referencedLifetime = relationship.referencedLifetime,
                                            evidence = relationship.evidence,
                                            riskExplanation = relationship.riskExplanation,
                                            confidence = relationship.confidence
                                        )
                                        // Once we flag one capture in a block, we can stop for this specific node
                                        return true
                                    }
                                }
                                return super.visitElement(node)
                            }
                        })
                    }
                    return false
                }
            },
            arrayOf(UCallExpression::class.java)
        )
    }

    override fun getQuickFixes(element: com.intellij.psi.PsiElement): Array<com.intellij.codeInspection.LocalQuickFix> {
        // Only suggest lifecycleScope if we are sure we are in an Activity/Fragment context
        return arrayOf(UseLifecycleScopeQuickFix())
    }

    private class UseLifecycleScopeQuickFix : com.intellij.codeInspection.LocalQuickFix {
        override fun getName() = "Use lifecycleScope"
        override fun getFamilyName() = "LeakLens quick fixes"

        override fun applyFix(
            project: com.intellij.openapi.project.Project,
            descriptor: com.intellij.codeInspection.ProblemDescriptor
        ) {
            val element = descriptor.psiElement
            // This is a simplified quick fix, usually needs more context to be safe
            if (element is org.jetbrains.kotlin.psi.KtSimpleNameExpression) {
                val factory = org.jetbrains.kotlin.psi.KtPsiFactory(project)
                element.replace(factory.createSimpleName("lifecycleScope"))
            }
        }
    }
}
