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
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class FlowLifecycleInspection : BaseLeakLensInspection(RuleRegistry.FLOW_LIFECYCLE_LEAK) {

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
                    val isUnsafeCollect =
                        methodName == "collect" || methodName == "collectLatest" || methodName == "launchIn"
                    val isUnsafeComposeCollect = methodName == "collectAsState"

                    if (isUnsafeCollect || isUnsafeComposeCollect) {
                        val containingClass = node.getContainingUClass()
                        val isComposable = isInsideComposable(node)

                        val ownerLifetime = when {
                            isComposable -> Lifetime.COMPOSITION
                            containingClass != null -> LifetimeEngine.getLifetime(containingClass)
                            else -> Lifetime.UNKNOWN
                        }

                        // We only care about leaks in UI components (Activity, Fragment, View, Composition, ViewModel)
                        if (ownerLifetime != Lifetime.UNKNOWN && ownerLifetime.priority > Lifetime.VIEWMODEL.priority) {
                            return super.visitCallExpression(node)
                        }

                        if (!isInsideRepeatOnLifecycle(node) && !usesFlowWithLifecycle(node) && !isUsingLifecycleSafeCompose(
                                node
                            )
                        ) {

                            // If it's launchIn(scope), check the scope
                            if (methodName == "launchIn") {
                                val scopeArg = node.valueArguments.firstOrNull()
                                    ?: return super.visitCallExpression(node)
                                val scopeLifetime = LifetimeEngine.getScopeLifetime(scopeArg)
                                if (scopeLifetime.priority < ownerLifetime.priority && scopeLifetime != Lifetime.UNKNOWN) {
                                    // Scope is safer than owner, e.g. launchIn(lifecycleScope) in Activity
                                    return super.visitCallExpression(node)
                                }
                            }

                            val sourcePsi = node.methodIdentifier?.sourcePsi ?: node.sourcePsi
                            ?: return super.visitCallExpression(node)

                            val message = if (isUnsafeComposeCollect) {
                                "Unsafe use of collectAsState(). Use collectAsStateWithLifecycle() for better memory management in Compose."
                            } else {
                                "Unsafe Flow collection. Use repeatOnLifecycle or flowWithLifecycle to prevent background leaks."
                            }

                            registerLeak(
                                holder = holder,
                                element = sourcePsi,
                                description = message,
                                className = containingClass?.javaPsi?.qualifiedName,
                                functionName = methodName,
                                symbolName = "flow_collect",
                                suggestedFix = "Use repeatOnLifecycle(Lifecycle.State.STARTED) or collectAsStateWithLifecycle().",
                                ownerLifetime = ownerLifetime,
                                referencedLifetime = Lifetime.LOCAL,
                                evidence = "Flow collection in $ownerLifetime without lifecycle boundaries"
                            )
                        }
                    }
                    return super.visitCallExpression(node)
                }

                private fun isInsideComposable(node: UCallExpression): Boolean {
                    var parent = node.uastParent
                    while (parent != null) {
                        if (parent is UMethod && LeakLensInspectionUtils.isComposable(parent)) return true
                        parent = parent.uastParent
                    }
                    return false
                }

                private fun isUsingLifecycleSafeCompose(node: UCallExpression): Boolean {
                    return node.methodName == "collectAsStateWithLifecycle"
                }

                private fun isInsideRepeatOnLifecycle(node: UElement): Boolean {
                    var parent = node.uastParent
                    while (parent != null) {
                        if (parent is UCallExpression && (parent.methodName == "repeatOnLifecycle" || parent.methodName == "flowWithLifecycle")) return true
                        parent = parent.uastParent
                    }
                    return false
                }

                private fun usesFlowWithLifecycle(node: UCallExpression): Boolean {
                    val receiver = node.receiver
                    return receiver != null && hasFlowWithLifecycleInChain(receiver)
                }

                private fun hasFlowWithLifecycleInChain(element: UElement): Boolean {
                    var current: UElement? = element
                    while (current != null) {
                        if (current is UCallExpression && current.methodName == "flowWithLifecycle") return true
                        if (current is UQualifiedReferenceExpression) {
                            if (current.selector is UCallExpression && (current.selector as UCallExpression).methodName == "flowWithLifecycle") return true
                            current = current.receiver
                        } else if (current is UCallExpression) {
                            current = current.receiver
                        } else break
                    }
                    return false
                }
            },
            arrayOf(UCallExpression::class.java)
        )
    }

    override fun getQuickFixes(element: com.intellij.psi.PsiElement): Array<com.intellij.codeInspection.LocalQuickFix> {
        return arrayOf(WrapWithRepeatOnLifecycleFix())
    }
}
