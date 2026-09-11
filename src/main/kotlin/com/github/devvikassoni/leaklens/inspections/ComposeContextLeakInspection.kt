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
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ComposeContextLeakInspection : BaseLeakLensInspection(RuleRegistry.COMPOSE_CONTEXT_CAPTURE) {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    // Objects captured in effects or remember
                    val composeLifetime = LifetimeEngine.getComposeConstructLifetime(node)
                    if (composeLifetime != Lifetime.UNKNOWN) {
                        checkCaptureLeak(node, composeLifetime, holder)
                    }

                    return super.visitCallExpression(node)
                }

                private fun checkCaptureLeak(
                    node: UCallExpression,
                    ownerLifetime: Lifetime,
                    holder: ProblemsHolder
                ) {
                    val methodName = node.methodName ?: "Compose construct"
                    node.valueArguments.forEach { arg ->
                        arg.accept(object : AbstractUastVisitor() {
                            override fun visitElement(node: UElement): Boolean {
                                if (node is UExpression) {
                                    val refLifetime = LifetimeEngine.getExpressionLifetime(node)

                                    if (ownerLifetime.canOutlive(refLifetime)) {
                                        val sourcePsi =
                                            node.sourcePsi ?: return super.visitElement(node)

                                        registerLeak(
                                            holder = holder,
                                            element = sourcePsi,
                                            description = "Object with $refLifetime lifetime captured in $methodName.",
                                            className = "Compose",
                                            symbolName = "capture_$methodName",
                                            ownerLifetime = ownerLifetime,
                                            referencedLifetime = refLifetime,
                                            evidence = "$refLifetime captured in $methodName",
                                            riskExplanation = "$methodName survives longer than the captured object."
                                        )
                                        return true // Stop at first capture in this branch
                                    } else if (refLifetime != Lifetime.UNKNOWN) {
                                        // If lifetime is known and safe, don't look at children (e.g. context.applicationContext)
                                        return true
                                    }
                                }
                                return super.visitElement(node)
                            }
                        })
                    }
                }
            },
            arrayOf(UCallExpression::class.java)
        )
    }
}
