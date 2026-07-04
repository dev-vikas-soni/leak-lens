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
import com.intellij.psi.PsiField
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UField
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Detects Activity or Fragment stored in a static field or companion object.
 * Uses UastHintedVisitorAdapter so the visitor fires only on UField nodes,
 * avoiding the O(N) toUElement() cost of visiting every PSI element.
 */
class StaticActivityReferenceInspection : LocalInspectionTool() {

    override fun getGroupDisplayName() = "LeakLens"
    override fun getDisplayName() = "Activity/Fragment stored in static field"
    override fun getShortName() = "LeakLensStaticActivityReference"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val fileIssues = mutableListOf<LeakInfo>()

        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {

                override fun visitField(node: UField): Boolean {
                    if (node.isStatic && LeakLensInspectionUtils.isActivityOrFragmentType(node.type)) {
                        val elementToHighlight =
                            node.uastAnchor?.sourcePsi ?: node.sourcePsi ?: return false
                        val fieldName = node.name
                        val description =
                            "LeakLens: Static field '$fieldName' holds an Activity/Fragment reference. " +
                                    "This causes a memory leak as static fields outlive the Activity lifecycle."

                        holder.registerProblem(
                            elementToHighlight,
                            description,
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            WrapWithWeakReferenceFix(fieldName),
                            AskGeminiFix(
                                description,
                                node.type.canonicalText,
                                LeakLensInspectionUtils.getLineNumber(elementToHighlight)
                            ),
                        )

                        fileIssues.add(createLeakInfo(node))
                    }
                    return false
                }
            },
            arrayOf(UField::class.java)
        )
    }

    private fun createLeakInfo(node: UField): LeakInfo {
        val line = node.sourcePsi?.let { LeakLensInspectionUtils.getLineNumber(it) } ?: 0
        return LeakInfo(
            signature = "static_leak_${node.name}_$line",
            shortDescription = "Static Field holding Activity/Fragment",
            leakTrace = "Static field: ${node.name} (line $line)",
            retainedObjectClassName = node.type.canonicalText,
            retainedByteSize = 0L,
            retainedObjectCount = 1,
            severity = LeakSeverity.WARNING,
            referenceChain = emptyList(),
            suggestedFix = "Wrap the reference in a WeakReference or clear it in onDestroy()."
        )
    }

    internal class WrapWithWeakReferenceFix(private val fieldName: String) : LocalQuickFix {
        override fun getName() = "Wrap '$fieldName' with WeakReference"
        override fun getFamilyName() = "LeakLens quick fixes"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement
            val uField = element.toUElementOfType<UField>() ?: return
            val fieldType = uField.type.presentableText

            if (element.language.id == "JAVA") {
                val factory = JavaPsiFacade.getElementFactory(project)
                val psiField = (uField.javaPsi as? PsiField) ?: return
                val newField = factory.createFieldFromText(
                    "private static java.lang.ref.WeakReference<$fieldType> ${fieldName}Ref;",
                    psiField.parent
                )
                psiField.replace(newField)
            } else if (element.language.id == "kotlin") {
                val ktFactory = org.jetbrains.kotlin.psi.KtPsiFactory(project)
                val ktProperty = uField.sourcePsi as? org.jetbrains.kotlin.psi.KtProperty ?: return
                val newProp =
                    ktFactory.createProperty("private val ${fieldName}Ref = java.lang.ref.WeakReference<$fieldType>($fieldName)")
                ktProperty.parent.addAfter(newProp, ktProperty)
            }
        }
    }
}
