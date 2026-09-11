package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.inspections.registry.RuleRegistry
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UField
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Detects View references held in Fragment fields that are not nulled out in onDestroyView.
 */
class ViewReferenceHeldInspection : BaseLeakLensInspection(RuleRegistry.VIEW_REFERENCE_HELD) {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitClass(node: UClass): Boolean {
                    if (!LeakLensInspectionUtils.isFragment(node)) return false

                    val viewFields = node.fields.filter {
                        LeakLensInspectionUtils.isViewOrBindingType(it.type)
                    }
                    if (viewFields.isEmpty()) return false

                    val onDestroyView = node.methods.find { it.name == "onDestroyView" }
                    val bodyText = onDestroyView?.uastBody?.asSourceString() ?: ""

                    for (field in viewFields) {
                        val name = field.name
                        val isNulled = bodyText.contains("$name = null") ||
                                bodyText.contains("$name=null") ||
                                bodyText.contains("_$name = null") ||
                                bodyText.contains("$name?.let") ||
                                bodyText.contains("$name.clear")

                        if (!isNulled) {
                            val elementToHighlight = field.uastAnchor?.sourcePsi ?: field.sourcePsi ?: continue

                            registerLeak(
                                holder = holder,
                                element = elementToHighlight,
                                description = "View field '$name' is not nulled in onDestroyView().",
                                className = node.javaPsi.qualifiedName,
                                symbolName = "view_$name",
                                suggestedFix = "Set $name = null in onDestroyView() to allow GC."
                            )
                        }
                    }
                    return false
                }
            },
            arrayOf(UClass::class.java)
        )
    }

    override fun getQuickFixes(element: com.intellij.psi.PsiElement): Array<com.intellij.codeInspection.LocalQuickFix> {
        val uField = element.toUElementOfType<UField>() ?: return emptyArray()
        return arrayOf(NullifyInOnDestroyViewFix(uField.name))
    }

    private class NullifyInOnDestroyViewFix(private val fieldName: String) :
        com.intellij.codeInspection.LocalQuickFix {
        override fun getName() = "Nullify '$fieldName' in onDestroyView()"
        override fun getFamilyName() = "LeakLens quick fixes"

        override fun applyFix(
            project: com.intellij.openapi.project.Project,
            descriptor: com.intellij.codeInspection.ProblemDescriptor
        ) {
            val element = descriptor.psiElement
            val uField = element.toUElementOfType<UField>() ?: return
            val uClass = uField.getContainingUClass() ?: return

            if (element.language.id == "JAVA") {
                val factory = JavaPsiFacade.getElementFactory(project)
                val psiClass = uClass.javaPsi
                val onDestroyView = psiClass.findMethodsByName("onDestroyView", false).firstOrNull()

                if (onDestroyView != null) {
                    val body = onDestroyView.body ?: return
                    body.addBefore(factory.createStatementFromText("$fieldName = null;", psiClass), body.rBrace)
                } else {
                    val newMethod = factory.createMethodFromText(
                        "@Override public void onDestroyView() { super.onDestroyView(); $fieldName = null; }",
                        psiClass
                    )
                    psiClass.add(newMethod)
                }
            } else if (element.language.id == "kotlin") {
                val factory = org.jetbrains.kotlin.psi.KtPsiFactory(project)
                val ktClass =
                    uClass.sourcePsi as? org.jetbrains.kotlin.psi.KtClassOrObject ?: return
                val onDestroyView =
                    ktClass.declarations.filterIsInstance<org.jetbrains.kotlin.psi.KtNamedFunction>()
                        .find { it.name == "onDestroyView" }

                if (onDestroyView != null) {
                    val body = onDestroyView.bodyBlockExpression ?: return
                    val newExpr = factory.createExpression("$fieldName = null")
                    body.addBefore(newExpr, body.rBrace)
                } else {
                    val newFunc = factory.createFunction(
                            "override fun onDestroyView() {\n    super.onDestroyView()\n    $fieldName = null\n}"
                        )
                    ktClass.add(newFunc)
                }
            }
        }
    }
}
