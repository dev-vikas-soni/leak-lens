package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.inspections.engine.LifetimeEngine
import com.github.devvikassoni.leaklens.inspections.registry.RuleRegistry
import com.github.devvikassoni.leaklens.model.Lifetime
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class DeprecatedLifecycleScopeInspection :
    BaseLeakLensInspection(RuleRegistry.DEPRECATED_LIFECYCLE_SCOPE) {

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

                    if (methodName in listOf(
                            "launchWhenStarted",
                            "launchWhenResumed",
                            "launchWhenCreated"
                        )
                    ) {
                        val elementToHighlight =
                            node.methodIdentifier?.sourcePsi ?: node.sourcePsi ?: return false
                        val containingClass = node.getContainingUClass()
                        val ownerLifetime = containingClass?.let { LifetimeEngine.getLifetime(it) }
                            ?: Lifetime.UNKNOWN

                        registerLeak(
                            holder = holder,
                            element = elementToHighlight,
                            description = rule.description,
                            className = containingClass?.javaPsi?.qualifiedName,
                            functionName = methodName,
                            symbolName = "deprecated_builder",
                            suggestedFix = "Use repeatOnLifecycle instead of launchWhenStarted/Resumed.",
                            ownerLifetime = ownerLifetime,
                            referencedLifetime = Lifetime.ACTIVITY,
                            evidence = "Use of deprecated $methodName in $ownerLifetime"
                        )
                    }
                    return false
                }
            },
            arrayOf(UCallExpression::class.java)
        )
    }
}
