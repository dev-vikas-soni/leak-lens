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
                    val methodName = node.methodName
                    val containingMethod =
                        node.getContainingUMethod() ?: return super.visitCallExpression(node)
                    if (!isComposable(containingMethod)) return super.visitCallExpression(node)

                    // 1. Existing check: Context passed to ViewModel
                    checkViewModelContextLeak(node)

                    // 2. New check: Context captured in remember { ... }
                    if (methodName == "remember" || methodName == "rememberLauncherForActivityResult") {
                        checkRememberContextLeak(node)
                    }

                    return super.visitCallExpression(node)
                }

                private fun checkViewModelContextLeak(node: UCallExpression) {
                    val isReceiverViewModel =
                        node.receiverType?.canonicalText?.contains("ViewModel") == true ||
                                node.returnType?.canonicalText?.contains("ViewModel") == true ||
                                node.methodName?.contains("ViewModel") == true

                    if (isReceiverViewModel) {
                        for (arg in node.valueArguments) {
                            val argType = arg.getExpressionType()?.canonicalText ?: continue
                            if (argType.contains("android.content.Context") || argType.contains("android.app.Activity")) {
                                registerLeak(
                                    arg.sourcePsi ?: continue,
                                    "LeakLens: Passing Context/Activity to a ViewModel in Compose causes leaks. Use LocalContext only for UI operations.",
                                    argType
                                )
                            }
                        }
                    }
                }

                private fun checkRememberContextLeak(node: UCallExpression) {
                    node.valueArguments.forEach { arg ->
                        arg.accept(object : org.jetbrains.uast.visitor.AbstractUastVisitor() {
                            override fun visitCallExpression(node: UCallExpression): Boolean {
                                node.valueArguments.forEach { innerArg ->
                                    val type = innerArg.getExpressionType()?.canonicalText ?: ""
                                    if (type.contains("android.content.Context") || type.contains("android.app.Activity")) {
                                        registerLeak(
                                            innerArg.sourcePsi ?: return@forEach,
                                            "LeakLens: Context captured in remember { } can outlive Activity. Use rememberUpdatedState or pass Context as a key.",
                                            type
                                        )
                                    }
                                }
                                return super.visitCallExpression(node)
                            }
                        })
                    }
                }

                private fun registerLeak(
                    element: com.intellij.psi.PsiElement,
                    description: String,
                    type: String
                ) {
                    val line = LeakLensInspectionUtils.getLineNumber(element)
                    holder.registerProblem(
                        element,
                        description,
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        AskGeminiFix(description, "Compose", line)
                    )
                    fileIssues.add(
                        LeakInfo(
                            signature = "compose_leak_$line",
                            shortDescription = description,
                            leakTrace = "Captured in Compose block at line $line",
                            retainedObjectClassName = type,
                            retainedByteSize = 0,
                            retainedObjectCount = 1,
                            severity = LeakSeverity.CRITICAL,
                            referenceChain = emptyList(),
                            suggestedFix = "Use LocalContext.current.applicationContext if possible, or pass context as a key to remember."
                        )
                    )
                }
            },
            arrayOf(UCallExpression::class.java)
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
