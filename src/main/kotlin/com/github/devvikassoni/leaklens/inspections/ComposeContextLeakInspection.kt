package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class ComposeContextLeakInspection : LocalInspectionTool() {

    override fun getGroupDisplayName() = "LeakLens"
    override fun getDisplayName() = "Context/Activity passed to ViewModel in Compose"
    override fun getShortName() = "LeakLensComposeContextLeak"

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        val fileIssues = mutableListOf<LeakInfo>()

        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    // Only check calls inside a @Composable function
                    val containingMethod =
                        node.getContainingUMethod() ?: return super.visitCallExpression(node)
                    if (!isComposable(containingMethod)) {
                        return super.visitCallExpression(node)
                    }

                    // Look for method calls on a ViewModel or constructors of ViewModels
                    // For simplicity, let's just flag if any argument passed to ANY function/constructor 
                    // is a Context and the receiver type contains "ViewModel".

                    val isReceiverViewModel =
                        node.receiverType?.canonicalText?.contains("ViewModel") == true ||
                                node.returnType?.canonicalText?.contains("ViewModel") == true ||
                                node.methodName?.contains("ViewModel") == true

                    if (isReceiverViewModel) {
                        for (arg in node.valueArguments) {
                            val argType = arg.getExpressionType()?.canonicalText ?: continue
                            if (argType.contains("android.content.Context") ||
                                argType.contains("android.app.Activity")
                            ) {

                                val elementToHighlight = arg.sourcePsi ?: continue
                                val line = LeakLensInspectionUtils.getLineNumber(elementToHighlight)
                                val description =
                                    "LeakLens: Passing Context or Activity to a ViewModel from a @Composable can cause memory leaks."

                                holder.registerProblem(
                                    elementToHighlight,
                                    description,
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                    RemoveContextArgFix(),
                                    AskGeminiFix(description, "Composable", line)
                                )

                                fileIssues.add(
                                    LeakInfo(
                                        signature = "compose_context_leak_$line",
                                        shortDescription = "Context passed to ViewModel in Compose",
                                        leakTrace = "Passed as argument at line $line",
                                        retainedObjectClassName = argType,
                                        retainedByteSize = 0,
                                        retainedObjectCount = 1,
                                        severity = LeakSeverity.CRITICAL,
                                        referenceChain = emptyList(),
                                        suggestedFix = "Do not pass Context to ViewModel. Use LocalContext only for UI operations inside Compose."
                                    )
                                )
                            }
                        }
                    }
                    return super.visitCallExpression(node)
                }

                override fun afterVisitFile(node: UFile) {
                    LeakLensInspectionUtils.reportLiveIssue(
                        holder,
                        "ComposeContextLeak",
                        fileIssues
                    )
                }
            },
            arrayOf(UCallExpression::class.java, UFile::class.java)
        )
    }

    private fun isComposable(method: UMethod): Boolean {
        return method.annotations.any {
            it.qualifiedName == "androidx.compose.runtime.Composable" || it.qualifiedName?.endsWith(
                "Composable"
            ) == true || it.qualifiedName?.substringAfterLast(".") == "Composable"
        }
    }
}
