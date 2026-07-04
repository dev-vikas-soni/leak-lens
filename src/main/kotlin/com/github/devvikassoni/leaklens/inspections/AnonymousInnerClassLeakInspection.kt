package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UField
import org.jetbrains.uast.UObjectLiteralExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Detects anonymous inner classes that implicitly hold a reference to an outer Activity/Fragment.
 */
class AnonymousInnerClassLeakInspection : LocalInspectionTool() {

    override fun getGroupDisplayName() = "LeakLens"
    override fun getDisplayName() = "Anonymous inner class may leak outer Activity/Fragment"
    override fun getShortName() = "LeakLensAnonymousInnerClassLeak"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val fileIssues = mutableListOf<LeakInfo>()

        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitObjectLiteralExpression(node: UObjectLiteralExpression): Boolean {
                    val anonymousClass = node.declaration
                    val outerClass = anonymousClass.getContainingUClass() ?: return false

                    if (!LeakLensInspectionUtils.isActivityOrFragment(outerClass)) return false

                    if (isAssignedToField(node) || isPassedToLongLivedMethod(node)) {
                        val elementToHighlight = node.sourcePsi ?: return false
                        val description =
                            "LeakLens: Anonymous inner class holds an implicit reference to ${outerClass.name}. " +
                                    "If this object outlives the Activity, it will prevent GC."

                        holder.registerProblem(
                            elementToHighlight,
                            description,
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            AskGeminiFix(
                                description,
                                outerClass.name ?: "Unknown",
                                LeakLensInspectionUtils.getLineNumber(elementToHighlight)
                            )
                        )

                        fileIssues.add(createLeakInfo(outerClass, node))
                    }
                    return false
                }
            },
            arrayOf(UObjectLiteralExpression::class.java)
        )
    }

    private fun createLeakInfo(outerClass: UClass, node: UObjectLiteralExpression): LeakInfo {
        val line = node.sourcePsi?.let { LeakLensInspectionUtils.getLineNumber(it) } ?: 0
        return LeakInfo(
            signature = "inner_class_leak_${outerClass.name}_$line",
            shortDescription = "Anonymous Inner Class holding ${outerClass.name}",
            leakTrace = "Implicit 'this' reference to ${outerClass.name} (line $line)",
            retainedObjectClassName = outerClass.javaPsi.qualifiedName ?: outerClass.name ?: "Unknown",
            retainedByteSize = 0,
            retainedObjectCount = 1,
            severity = LeakSeverity.WARNING,
            referenceChain = emptyList(),
            suggestedFix = "Convert to a static inner class and pass the Activity/Fragment as a WeakReference."
        )
    }

    private fun isAssignedToField(node: UObjectLiteralExpression) = node.uastParent is UField

    private fun isPassedToLongLivedMethod(node: UObjectLiteralExpression): Boolean {
        val call = node.uastParent as? UCallExpression ?: return false

        // Check for specific long-lived method names
        val methodName = call.methodName
        if (methodName != null && methodName in listOf(
            "postDelayed", "post", "execute", "submit", "registerListener",
                "addCallback", "setOnClickListener", "registerReceiver", "subscribe"
            )
        ) return true

        // Check for constructors of long-lived types (e.g., new Thread(runnable))
        if (call.kind == UastCallKind.CONSTRUCTOR_CALL) {
            val typeName = call.returnType?.canonicalText ?: ""
            if (typeName.contains("Thread") || typeName.contains("Timer") || typeName.contains("WorkRequest")) {
                return true
            }
        }

        return false
    }
}
