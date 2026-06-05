package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.*
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity

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
                        holder.registerProblem(
                            elementToHighlight,
                            "LeakLens: Anonymous inner class holds an implicit reference to ${outerClass.name}. " +
                            "If this object outlives the Activity, it will prevent GC.",
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                        )
                        
                        if (isOnTheFly) {
                            fileIssues.add(createLeakInfo(outerClass, node))
                        }
                    }
                    return false
                }

                override fun afterVisitFile(node: UFile) {
                    LeakLensInspectionUtils.reportLiveIssue(holder, isOnTheFly, "AnonymousInnerClassLeak", fileIssues)
                }
            },
            arrayOf(UObjectLiteralExpression::class.java, UFile::class.java)
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
        val methodName = call.methodName ?: return false
        return methodName in listOf(
            "postDelayed", "post", "execute", "submit", "registerListener", 
            "addCallback", "setOnClickListener", "registerReceiver"
        )
    }
}
