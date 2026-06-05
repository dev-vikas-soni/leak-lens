package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.*
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.intellij.openapi.project.Project

/**
 * Detects Activity Context passed to a Singleton, which lives for the app's duration.
 */
class ContextPassedToSingletonInspection : LocalInspectionTool() {

    override fun getGroupDisplayName() = "LeakLens"
    override fun getDisplayName() = "Activity Context passed to Singleton"
    override fun getShortName() = "LeakLensContextPassedToSingleton"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val fileIssues = mutableListOf<LeakInfo>()

        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val args = node.valueArguments
                    for (arg in args) {
                        if (isActivityContext(arg)) {
                            val method = node.resolve() ?: continue
                            val containingClass = method.containingClass ?: continue

                            if (isSingleton(containingClass)) {
                                val paramIndex = args.indexOf(arg)
                                val paramType = method.parameterList.parameters.getOrNull(paramIndex)?.type ?: continue

                                if (LeakLensInspectionUtils.isActivityOrFragmentType(paramType)) {
                                    val elementToHighlight = arg.sourcePsi ?: node.sourcePsi ?: continue
                                    holder.registerProblem(
                                        elementToHighlight,
                                        "LeakLens: Passing Activity Context to a Singleton will cause a memory leak.",
                                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                        UseApplicationContextQuickFix()
                                    )
                                    
                                    if (isOnTheFly) {
                                        fileIssues.add(createLeakInfo(containingClass.name ?: "Singleton", arg))
                                    }
                                }
                            }
                        }
                    }
                    return false
                }

                override fun afterVisitFile(node: UFile) {
                    LeakLensInspectionUtils.reportLiveIssue(holder, isOnTheFly, "ContextPassedToSingleton", fileIssues)
                }
            },
            arrayOf(UCallExpression::class.java, UFile::class.java)
        )
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
        override fun getFamilyName() = "LeakLens quick fixes"

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
