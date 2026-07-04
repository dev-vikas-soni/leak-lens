package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Detects usage of deprecated lifecycle coroutine builders like launchWhenStarted,
 * which are known to cause resource leaks when the app is in the background.
 */
class DeprecatedLifecycleScopeInspection : LocalInspectionTool() {

    override fun getGroupDisplayName() = "LeakLens"
    override fun getDisplayName() = "Deprecated lifecycleScope.launchWhenX causes leaks"
    override fun getShortName() = "LeakLensDeprecatedLifecycleScope"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val fileIssues = mutableListOf<LeakInfo>()

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
                        val containingClass = node.getContainingUClass() ?: return false

                        val elementToHighlight =
                            node.methodIdentifier?.sourcePsi ?: node.sourcePsi ?: return false
                        val description =
                            "LeakLens: $methodName is deprecated and can cause memory/resource leaks in the background. Use repeatOnLifecycle instead."

                        holder.registerProblem(
                            elementToHighlight,
                            description,
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            AskGeminiFix(
                                description,
                                containingClass.name ?: "Unknown",
                                LeakLensInspectionUtils.getLineNumber(elementToHighlight)
                            )
                        )

                        fileIssues.add(createLeakInfo(methodName ?: "launchWhenStarted", node))
                    }
                    return false
                }
            },
            arrayOf(UCallExpression::class.java)
        )
    }

    private fun createLeakInfo(methodName: String, node: UCallExpression): LeakInfo {
        val line = node.sourcePsi?.let { LeakLensInspectionUtils.getLineNumber(it) } ?: 0
        return LeakInfo(
            signature = "lifecycle_scope_leak_${methodName}_$line",
            shortDescription = "Deprecated $methodName used",
            leakTrace = "Launched with $methodName (line $line)",
            retainedObjectClassName = "androidx.lifecycle.LifecycleCoroutineScope",
            retainedByteSize = 0,
            retainedObjectCount = 1,
            severity = LeakSeverity.WARNING,
            referenceChain = emptyList(),
            suggestedFix = "Use lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { ... } } to safely collect flows."
        )
    }
}
