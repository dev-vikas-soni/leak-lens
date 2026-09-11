package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.inspections.registry.RuleRegistry
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.UObjectLiteralExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Detects anonymous inner classes that implicitly hold a reference to an outer Activity/Fragment.
 */
class AnonymousInnerClassLeakInspection :
    BaseLeakLensInspection(RuleRegistry.ANONYMOUS_INNER_CLASS_LEAK) {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitObjectLiteralExpression(node: UObjectLiteralExpression): Boolean {
                    val anonymousClass = node.declaration
                    val outerClass = anonymousClass.getContainingUClass() ?: return false

                    if (!LeakLensInspectionUtils.isActivityOrFragment(outerClass)) return false

                    if (isAssignedToField(node) || isPassedToLongLivedMethod(node)) {
                        val elementToHighlight = node.sourcePsi ?: return false

                        val className = anonymousClass.javaPsi.qualifiedName ?: anonymousClass.name
                        val description =
                            "Anonymous inner class holds an implicit reference to ${outerClass.name}. If this object outlives the Activity, it will prevent GC."

                        registerLeak(
                            holder = holder,
                            element = elementToHighlight,
                            description = description,
                            className = className,
                            functionName = null,
                            symbolName = "inner_class",
                            suggestedFix = "Convert to a static inner class and pass the Activity/Fragment as a WeakReference."
                        )
                    }
                    return false
                }
            },
            arrayOf(UObjectLiteralExpression::class.java)
        )
    }

    private fun isAssignedToField(node: UObjectLiteralExpression) = node.uastParent is UField

    private fun isPassedToLongLivedMethod(node: UObjectLiteralExpression): Boolean {
        val call = node.uastParent as? UCallExpression ?: return false
        val methodName = call.methodName
        if (methodName != null && methodName in listOf(
                "postDelayed", "post", "execute", "submit", "registerListener",
                "addCallback", "setOnClickListener", "registerReceiver", "subscribe"
            )
        ) {
            return true
        }
        if (call.kind == UastCallKind.CONSTRUCTOR_CALL) {
            val typeName = call.returnType?.canonicalText ?: ""
            if (typeName.contains("Thread") || typeName.contains("Timer") || typeName.contains("WorkRequest")) {
                return true
            }
        }
        return false
    }
}
