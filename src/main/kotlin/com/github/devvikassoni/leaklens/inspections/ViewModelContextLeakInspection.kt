package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UField
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Detects if a ViewModel stores a reference to a Context, Activity, or View,
 * which is a classic memory leak since ViewModels outlive Activities.
 */
class ViewModelContextLeakInspection : LocalInspectionTool() {

    override fun getGroupDisplayName() = "LeakLens"
    override fun getDisplayName() = "ViewModel holds Activity/Context/View reference"
    override fun getShortName() = "LeakLensViewModelContextLeak"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val fileIssues = mutableListOf<LeakInfo>()

        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {

                override fun visitClass(node: UClass): Boolean {
                    // Check if it's a ViewModel
                    val superTypes = node.uastSuperTypes
                    val isViewModel = superTypes.any { type ->
                        val text = type.type.canonicalText
                        text.contains("ViewModel") && !text.contains("AndroidViewModel")
                    }

                    if (isViewModel) {
                        for (uField in node.fields) {
                            val fieldType = uField.type.canonicalText

                            if (LeakLensInspectionUtils.isActivityOrFragmentType(uField.type) ||
                                fieldType.contains("android.content.Context") ||
                                fieldType.contains("android.view.View") ||
                                fieldType.contains("android.graphics.drawable.Drawable")
                            ) {
                                val elementToHighlight =
                                    uField.uastAnchor?.sourcePsi ?: uField.sourcePsi
                                    ?: node.sourcePsi ?: continue
                                val description =
                                    "LeakLens: Storing ${uField.type.presentableText} in a ViewModel will cause a memory leak."

                                holder.registerProblem(
                                    elementToHighlight,
                                    description,
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                    AskGeminiFix(
                                        description,
                                        node.name ?: "ViewModel",
                                        LeakLensInspectionUtils.getLineNumber(elementToHighlight)
                                    )
                                )

                                fileIssues.add(
                                    createLeakInfo(node.name ?: "ViewModel", uField)
                                )
                            }
                        }
                    }

                    return false
                }
            },
            arrayOf(UClass::class.java)
        )
    }

    private fun createLeakInfo(className: String, field: UField): LeakInfo {
        val line = field.sourcePsi?.let { LeakLensInspectionUtils.getLineNumber(it) } ?: 0
        return LeakInfo(
            signature = "viewmodel_leak_${className}_$line",
            shortDescription = "ViewModel stores View/Context",
            leakTrace = "Field '${field.name}' in $className (line $line)",
            retainedObjectClassName = field.type.presentableText,
            retainedByteSize = 0,
            retainedObjectCount = 1,
            severity = LeakSeverity.WARNING,
            referenceChain = emptyList(),
            suggestedFix = "Remove the View/Context reference from the ViewModel. Use LiveData, StateFlow, or events to communicate with the UI."
        )
    }
}
