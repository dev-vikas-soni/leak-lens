package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.inspections.engine.LifetimeEngine
import com.github.devvikassoni.leaklens.inspections.registry.RuleRegistry
import com.github.devvikassoni.leaklens.model.Lifetime
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Detects Activity Context passed to a Singleton, which lives for the app's duration.
 */
class ContextPassedToSingletonInspection :
    BaseLeakLensInspection(RuleRegistry.CONTEXT_PASSED_TO_SINGLETON) {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val args = node.valueArguments
                    for (arg in args) {
                        if (isActivityContext(arg)) {
                            val argPsi = arg.sourcePsi
                            if (argPsi != null && LeakLensInspectionUtils.isApplicationContext(
                                    argPsi
                                )
                            ) continue

                            val method = node.resolve() ?: continue
                            val containingClass = method.containingClass ?: continue
                            val relationship = LifetimeEngine.analyzeRelationship(
                                owner = containingClass,
                                referencedType = arg.getExpressionType() ?: continue,
                                evidenceSource = "Argument passed to ${method.name}"
                            )

                            if (relationship.isRisky) {
                                val elementToHighlight =
                                    arg.sourcePsi ?: node.sourcePsi ?: continue

                                registerLeak(
                                    holder = holder,
                                    element = elementToHighlight,
                                    description = rule.description,
                                    className = containingClass.qualifiedName,
                                    symbolName = "singleton_arg",
                                    suggestedFix = "Use 'context.applicationContext' instead of an Activity context.",
                                    ownerLifetime = relationship.ownerLifetime,
                                    referencedLifetime = relationship.referencedLifetime,
                                    evidence = relationship.evidence,
                                    riskExplanation = relationship.riskExplanation,
                                    confidence = relationship.confidence
                                )
                            }
                        }
                    }
                    return false
                }

                override fun visitBinaryExpression(node: org.jetbrains.uast.UBinaryExpression): Boolean {
                    if (node.operator == org.jetbrains.uast.UastBinaryOperator.ASSIGN) {
                        val right = node.rightOperand
                        if (isActivityContext(right)) {
                            val rightPsi = right.sourcePsi
                            if (rightPsi != null && LeakLensInspectionUtils.isApplicationContext(
                                    rightPsi
                                )
                            ) return false

                            val left = node.leftOperand
                            if (left is org.jetbrains.uast.UReferenceExpression) {
                                val resolved = left.resolve()
                                val containingClass = when (resolved) {
                                    is PsiField -> resolved.containingClass
                                    is PsiMethod -> resolved.containingClass
                                    else -> null
                                } ?: return false

                                val relationship = LifetimeEngine.analyzeRelationship(
                                    owner = containingClass,
                                    referencedType = right.getExpressionType() ?: return false,
                                    evidenceSource = "Assignment to field ${(resolved as? com.intellij.psi.PsiNamedElement)?.name}"
                                )

                                if (relationship.isRisky) {
                                    val elementToHighlight =
                                        right.sourcePsi ?: node.sourcePsi ?: return false

                                    val memberName =
                                        (resolved as? com.intellij.psi.PsiNamedElement)?.name
                                            ?: "unknown"
                                    registerLeak(
                                        holder = holder,
                                        element = elementToHighlight,
                                        description = rule.description,
                                        className = containingClass.qualifiedName,
                                        symbolName = memberName,
                                        suggestedFix = "Use 'context.applicationContext' instead of an Activity context.",
                                        ownerLifetime = relationship.ownerLifetime,
                                        referencedLifetime = relationship.referencedLifetime,
                                        evidence = relationship.evidence,
                                        riskExplanation = relationship.riskExplanation,
                                        confidence = relationship.confidence
                                    )
                                }
                            }
                        }
                    }
                    return false
                }
            },
            arrayOf(UCallExpression::class.java, org.jetbrains.uast.UBinaryExpression::class.java)
        )
    }

    override fun getQuickFixes(element: com.intellij.psi.PsiElement): Array<com.intellij.codeInspection.LocalQuickFix> {
        return arrayOf(UseApplicationContextFix())
    }

    private fun isActivityContext(arg: UExpression): Boolean {
        val type = arg.getExpressionType() ?: return false
        val lifetime = LifetimeEngine.getLifetime(type)
        return lifetime == Lifetime.ACTIVITY || lifetime == Lifetime.FRAGMENT
    }
}
