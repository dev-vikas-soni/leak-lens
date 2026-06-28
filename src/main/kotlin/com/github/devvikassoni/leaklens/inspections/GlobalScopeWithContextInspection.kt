package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Detects coroutines launched in GlobalScope that capture Context/Activity references.
 */
class GlobalScopeWithContextInspection : LocalInspectionTool() {

    override fun getGroupDisplayName() = "LeakLens"
    override fun getDisplayName() = "GlobalScope coroutine may leak Activity/Context"
    override fun getShortName() = "LeakLensGlobalScopeWithContext"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val fileIssues = mutableListOf<LeakInfo>()

        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val methodName = node.methodName
                    val receiver = node.receiver

                    if (receiver?.asSourceString() == "GlobalScope" && methodName in listOf(
                            "launch",
                            "async"
                        )
                    ) {
                        val containingClass = node.getContainingUClass() ?: return false
                        if (LeakLensInspectionUtils.isActivityOrFragment(containingClass)) {
                            val elementToHighlight =
                                node.methodIdentifier?.sourcePsi ?: node.sourcePsi ?: return false
                            val description =
                                "LeakLens: GlobalScope.${methodName} may cause a memory leak. Use lifecycleScope."
                            holder.registerProblem(
                                elementToHighlight,
                                description,
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                UseLifecycleScopeQuickFix(),
                                AskGeminiFix(
                                    description,
                                    containingClass.name ?: "Unknown",
                                    LeakLensInspectionUtils.getLineNumber(elementToHighlight)
                                )
                            )

                            fileIssues.add(createLeakInfo(methodName ?: "launch", node))
                        }
                    }
                    return false
                }

                override fun afterVisitFile(node: UFile) {
                    LeakLensInspectionUtils.reportLiveIssue(
                        holder,
                        "GlobalScopeWithContext",
                        fileIssues
                    )
                }
            },
            arrayOf(UCallExpression::class.java, UFile::class.java)
        )
    }

    private fun createLeakInfo(methodName: String, node: UCallExpression): LeakInfo {
        val line = node.sourcePsi?.let { LeakLensInspectionUtils.getLineNumber(it) } ?: 0
        return LeakInfo(
            signature = "global_scope_leak_${methodName}_$line",
            shortDescription = "GlobalScope.$methodName in Activity/Fragment",
            leakTrace = "Launched in GlobalScope (line $line)",
            retainedObjectClassName = "kotlinx.coroutines.GlobalScope",
            retainedByteSize = 0,
            retainedObjectCount = 1,
            severity = LeakSeverity.WARNING,
            referenceChain = emptyList(),
            suggestedFix = "Use 'lifecycleScope' or 'viewModelScope' for automatic cancellation."
        )
    }

    private class UseLifecycleScopeQuickFix : LocalQuickFix {
        override fun getName() = "Use lifecycleScope"
        override fun getFamilyName() = "LeakLens quick fixes"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement
            val document = element.containingFile.viewProvider.document ?: return
            val newText = element.text.replace("GlobalScope", "lifecycleScope")
            document.replaceString(element.textRange.startOffset, element.textRange.endOffset, newText)
        }
    }
}
