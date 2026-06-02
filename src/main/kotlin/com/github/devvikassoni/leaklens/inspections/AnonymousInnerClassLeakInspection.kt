package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.*
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import com.intellij.uast.UastHintedVisitorAdapter

/**
 * Detects anonymous inner classes that implicitly hold a reference to an outer Activity/Fragment.
 * Common pattern: new Runnable() { ... } or object : SomeCallback { ... } inside Activity.
 * 
 * Migrated to UAST to support both Java and Kotlin.
 */
class AnonymousInnerClassLeakInspection : LocalInspectionTool() {

    override fun getGroupDisplayName(): String = "LeakLens"
    override fun getDisplayName(): String = "Anonymous inner class may leak outer Activity/Fragment"
    override fun getShortName(): String = "LeakLensAnonymousInnerClassLeak"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitObjectLiteralExpression(node: UObjectLiteralExpression): Boolean {
                    val anonymousClass = node.declaration
                    
                    // Check if it's an inner class that captures outer scope
                    // In UAST, anonymous classes are usually inner.
                    
                    val outerClass = anonymousClass.getContainingUClass() ?: return false
                    if (!isActivityOrFragment(outerClass)) return false

                    // Check if the anonymous class is assigned to a field or passed to a long-lived callback
                    if (isAssignedToField(node) || isPassedToLongLivedMethod(node)) {
                        val elementToHighlight = node.sourcePsi ?: return false
                        holder.registerProblem(
                            elementToHighlight,
                            "LeakLens: Anonymous inner class holds implicit reference to ${outerClass.name}. " +
                            "If this object outlives the Activity/Fragment, it will cause a memory leak. " +
                            "Consider using a static inner class with WeakReference.",
                            ProblemHighlightType.WARNING
                        )
                    }
                    return false
                }
            },
            arrayOf(UObjectLiteralExpression::class.java)
        )
    }

    private fun isActivityOrFragment(uClass: UClass): Boolean {
        var current = uClass.javaPsi.superClass
        while (current != null) {
            val name = current.qualifiedName ?: ""
            if (name.contains("Activity") || name.contains("Fragment")) return true
            current = current.superClass
        }
        return false
    }

    private fun isAssignedToField(node: UObjectLiteralExpression): Boolean {
        return node.uastParent is UField
    }

    private fun isPassedToLongLivedMethod(node: UObjectLiteralExpression): Boolean {
        val call = node.uastParent as? UCallExpression ?: return false
        val methodName = call.methodName ?: return false
        return methodName in listOf(
            "postDelayed", "post", "execute", "submit",
            "registerListener", "addCallback", "setOnClickListener",
            "addOnGlobalLayoutListener", "registerReceiver"
        )
    }
}

