package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.toUElement

/**
 * Detects Activity Context passed to a Singleton, which lives for the app's duration.
 */
class ContextPassedToSingletonInspection : LocalInspectionTool() {

    override fun getGroupDisplayName() = "LeakLens"
    override fun getDisplayName() = "Activity Context passed to Singleton"
    override fun getShortName() = "LeakLensContextPassedToSingleton"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val fileIssues = mutableListOf<LeakInfo>()

        return object : PsiElementVisitor() {
            override fun visitElement(element: com.intellij.psi.PsiElement) {
                val uElement = element.toUElement() ?: return
                if (uElement is UCallExpression) {
                    visitCallExpression(uElement)
                } else if (uElement is UFile) {
                    visitFile(uElement)
                }
            }

            private fun visitCallExpression(node: UCallExpression) {
                val args = node.valueArguments
                for (arg in args) {
                    if (isActivityContext(arg)) {
                        val method = node.resolve() ?: continue
                        val containingClass = method.containingClass ?: continue

                        if (isSingleton(containingClass)) {
                            val paramIndex = args.indexOf(arg)
                            val paramType =
                                method.parameterList.parameters.getOrNull(paramIndex)?.type
                                    ?: continue

                            if (LeakLensInspectionUtils.isActivityOrFragmentType(paramType) || paramType.canonicalText.contains(
                                    "Context"
                                )
                            ) {
                                val elementToHighlight = arg.sourcePsi ?: node.sourcePsi ?: continue
                                val description =
                                    "LeakLens: Passing Activity Context to a Singleton will cause a memory leak."
                                holder.registerProblem(
                                    elementToHighlight,
                                    description,
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                    UseApplicationContextQuickFix(),
                                    AskGeminiFix(
                                        description,
                                        containingClass.name ?: "Singleton",
                                        LeakLensInspectionUtils.getLineNumber(elementToHighlight)
                                    )
                                )

                                fileIssues.add(
                                    createLeakInfo(
                                        containingClass.name ?: "Singleton", arg
                                    )
                                )
                            }
                        }
                    }
                }
            }

            private fun visitFile(node: UFile) {
                LeakLensInspectionUtils.reportLiveIssue(
                    holder,
                    "ContextPassedToSingleton",
                    fileIssues
                )
            }
        }
    }

    private fun createLeakInfo(singletonName: String, arg: UExpression): LeakInfo {
        val line = arg.sourcePsi?.let { LeakLensInspectionUtils.getLineNumber(it) } ?: 0
        return LeakInfo(
            signature = "singleton_context_leak_${singletonName}_$line",
            shortDescription = "Activity Context passed to $singletonName",
            leakTrace = "Passed to: $singletonName (line $line)",
            retainedObjectClassName = "android.content.Context",
            retainedByteSize = 0,
            retainedObjectCount = 1,
            severity = LeakSeverity.WARNING,
            referenceChain = emptyList(),
            suggestedFix = "Use 'context.applicationContext' instead of an Activity context."
        )
    }

    private fun isActivityContext(arg: UExpression): Boolean {
        if (arg is UThisExpression) return true
        if (arg is USimpleNameReferenceExpression && (arg.identifier == "context" || arg.identifier == "activity")) return true
        return false
    }

    private fun isSingleton(psiClass: PsiClass): Boolean {
        val hasInstance = psiClass.fields.any { field ->
            field.hasModifierProperty(PsiModifier.STATIC) &&
            (field.name == "INSTANCE" || field.name == "instance" || field.name == "sInstance")
        }
        val hasSingletonAnnotation = psiClass.annotations.any { it.qualifiedName?.contains("Singleton") == true }
        return hasInstance || hasSingletonAnnotation
    }

    private class UseApplicationContextQuickFix : LocalQuickFix {
        override fun getName() = "Use applicationContext"
        override fun getFamilyName() = "LeakLens singleton fix"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement
            val isKotlin = element.language.id.equals("kotlin", ignoreCase = true)
            val suffix = if (isKotlin) ".applicationContext" else ".getApplicationContext()"
            val newText = "${element.text}$suffix"
            
            val document = element.containingFile.viewProvider.document ?: return
            document.replaceString(element.textRange.startOffset, element.textRange.endOffset, newText)
        }
    }
}
