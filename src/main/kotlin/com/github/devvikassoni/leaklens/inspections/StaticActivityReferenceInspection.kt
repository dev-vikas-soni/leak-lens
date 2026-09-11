package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.inspections.registry.RuleRegistry
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiField
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UField
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Detects Activity or Fragment stored in a static field or companion object.
 */
class StaticActivityReferenceInspection :
    BaseLeakLensInspection(RuleRegistry.STATIC_ACTIVITY_REFERENCE) {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {

                override fun visitField(node: UField): Boolean {
                    if (node.isStatic && LeakLensInspectionUtils.isActivityOrFragmentType(node.type)) {
                        val sourcePsi = node.sourcePsi ?: node.javaPsi
                        if (sourcePsi != null && LeakLensInspectionUtils.isApplicationContext(
                                sourcePsi
                            )
                        ) return false

                        val elementToHighlight =
                            node.uastAnchor?.sourcePsi ?: node.sourcePsi ?: return false
                        val fieldName = node.name

                        val description =
                            "Static field '$fieldName' holds an Activity/Fragment reference. This causes a memory leak as static fields outlive the Activity lifecycle."
                        registerLeak(
                            holder = holder,
                            element = elementToHighlight,
                            description = description,
                            className = node.getContainingUClass()?.javaPsi?.qualifiedName,
                            functionName = null,
                            symbolName = fieldName,
                            suggestedFix = "Wrap the reference in a WeakReference or clear it in onDestroy()."
                        )
                    }
                    return false
                }
            },
            arrayOf(UField::class.java)
        )
    }

    override fun getQuickFixes(element: com.intellij.psi.PsiElement): Array<com.intellij.codeInspection.LocalQuickFix> {
        val uField = element.toUElementOfType<UField>() ?: return emptyArray()
        return arrayOf(WrapWithWeakReferenceFix(uField.name))
    }

    internal class WrapWithWeakReferenceFix(private val fieldName: String) :
        com.intellij.codeInspection.LocalQuickFix {
        override fun getName() = "Wrap '$fieldName' with WeakReference"
        override fun getFamilyName() = "LeakLens quick fixes"

        override fun applyFix(
            project: com.intellij.openapi.project.Project,
            descriptor: com.intellij.codeInspection.ProblemDescriptor
        ) {
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
                    ktFactory.createProperty(
                        "private val ${fieldName}Ref = java.lang.ref.WeakReference<$fieldType>($fieldName)"
                    )
                ktProperty.parent.addAfter(newProp, ktProperty)
            }
        }
    }
}
