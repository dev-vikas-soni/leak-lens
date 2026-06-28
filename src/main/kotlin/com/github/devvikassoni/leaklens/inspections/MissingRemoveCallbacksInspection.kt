package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UField
import org.jetbrains.uast.UFile
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Detects Handler usage without removeCallbacksAndMessages in onDestroy.
 */
class MissingRemoveCallbacksInspection : LocalInspectionTool() {

    override fun getGroupDisplayName() = "LeakLens"
    override fun getDisplayName() = "Missing removeCallbacksAndMessages in onDestroy"
    override fun getShortName() = "LeakLensMissingRemoveCallbacks"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val fileIssues = mutableListOf<LeakInfo>()

        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitClass(node: UClass): Boolean {
                    if (!LeakLensInspectionUtils.isActivityOrFragment(node)) return false

                    val handlerFields =
                        node.fields.filter { it.type.canonicalText.contains("Handler") }
                    if (handlerFields.isEmpty()) return false

                    val onDestroy =
                        node.methods.find { it.name == "onDestroy" || it.name == "onDestroyView" }
                    val bodyText = onDestroy?.uastBody?.asSourceString() ?: ""

                    for (field in handlerFields) {
                        val fieldName = field.name
                        val hasCleanup = bodyText.contains("$fieldName.removeCallbacks") ||
                                bodyText.contains("$fieldName.removeMessages") ||
                                bodyText.contains("$fieldName?.removeCallbacks")

                        if (!hasCleanup) {
                            val elementToHighlight =
                                field.uastAnchor?.sourcePsi ?: field.sourcePsi ?: continue
                            val description =
                                "LeakLens: Handler '$fieldName' may cause a leak. Call removeCallbacks in onDestroy."
                            holder.registerProblem(
                                elementToHighlight,
                                description,
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                RemoveCallbacksQuickFix(fieldName),
                                AskGeminiFix(
                                    description,
                                    "android.os.Handler",
                                    LeakLensInspectionUtils.getLineNumber(elementToHighlight)
                                )
                            )

                            fileIssues.add(createLeakInfo(field))
                        }
                    }
                    return false
                }

                override fun afterVisitFile(node: UFile) {
                    LeakLensInspectionUtils.reportLiveIssue(
                        holder,
                        "MissingRemoveCallbacks",
                        fileIssues
                    )
                }
            },
            arrayOf(UClass::class.java, UFile::class.java)
        )
    }

    private fun createLeakInfo(field: UField): LeakInfo {
        val line = field.sourcePsi?.let { LeakLensInspectionUtils.getLineNumber(it) } ?: 0
        return LeakInfo(
            signature = "missing_cleanup_leak_${field.name}_$line",
            shortDescription = "Missing cleanup for Handler ${field.name}",
            leakTrace = "Handler field: ${field.name} (line $line)",
            retainedObjectClassName = "android.os.Handler",
            retainedByteSize = 0,
            retainedObjectCount = 1,
            severity = LeakSeverity.WARNING,
            referenceChain = emptyList(),
            suggestedFix = "Call ${field.name}.removeCallbacksAndMessages(null) in onDestroy() or onDestroyView()."
        )
    }

    private class RemoveCallbacksQuickFix(private val handlerName: String) : LocalQuickFix {
        override fun getName() = "Add removeCallbacksAndMessages in onDestroy"
        override fun getFamilyName() = "LeakLens quick fixes"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement
            val uField = element.toUElementOfType<UField>() ?: return
            val uClass = uField.getContainingUClass() ?: return
            
            if (element.language.id == "JAVA") {
                val factory = JavaPsiFacade.getElementFactory(project)
                val psiClass = uClass.javaPsi
                val onDestroy = psiClass.findMethodsByName("onDestroy", false).firstOrNull() 
                    ?: psiClass.findMethodsByName("onDestroyView", false).firstOrNull()
                
                if (onDestroy != null) {
                    val body = onDestroy.body ?: return
                    body.addBefore(factory.createStatementFromText("$handlerName.removeCallbacksAndMessages(null);", psiClass), body.rBrace)
                } else {
                    val newMethod = factory.createMethodFromText(
                        "@Override protected void onDestroy() { super.onDestroy(); $handlerName.removeCallbacksAndMessages(null); }", 
                        psiClass
                    )
                    psiClass.add(newMethod)
                }
            } else {
                val factory = JavaPsiFacade.getElementFactory(project)
                element.parent.addBefore(factory.createCommentFromText("// LeakLens: Call $handlerName.removeCallbacksAndMessages(null) in onDestroy()", element), element)
            }
        }
    }
}
