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
 * Detects Handler usage without removeCallbacksAndMessages in onDestroy.
 */
class MissingRemoveCallbacksInspection :
    BaseLeakLensInspection(RuleRegistry.MISSING_REMOVE_CALLBACKS) {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
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

                            registerLeak(
                                holder = holder,
                                element = elementToHighlight,
                                description = "Handler '$fieldName' may cause a leak. Call removeCallbacks in onDestroy().",
                                className = node.javaPsi.qualifiedName,
                                symbolName = "handler_$fieldName",
                                suggestedFix = "Call $fieldName.removeCallbacksAndMessages(null) in onDestroy()."
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
        return arrayOf(RemoveCallbacksQuickFix(uField.name))
    }

    private class RemoveCallbacksQuickFix(private val handlerName: String) :
        com.intellij.codeInspection.LocalQuickFix {
        override fun getName() = "Add removeCallbacksAndMessages in onDestroy"
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
                val onDestroy = psiClass.findMethodsByName("onDestroy", false).firstOrNull()
                    ?: psiClass.findMethodsByName("onDestroyView", false).firstOrNull()

                if (onDestroy != null) {
                    val body = onDestroy.body ?: return
                    body.addBefore(
                        factory.createStatementFromText(
                            "$handlerName.removeCallbacksAndMessages(null);",
                            psiClass
                        ),
                        body.rBrace
                    )
                } else {
                    val newMethod = factory.createMethodFromText(
                        "@Override protected void onDestroy() { super.onDestroy(); $handlerName.removeCallbacksAndMessages(null); }",
                        psiClass
                    )
                    psiClass.add(newMethod)
                }
            } else if (element.language.id == "kotlin") {
                val factory = org.jetbrains.kotlin.psi.KtPsiFactory(project)
                val ktClass =
                    uClass.sourcePsi as? org.jetbrains.kotlin.psi.KtClassOrObject ?: return
                val onDestroy =
                    ktClass.declarations.filterIsInstance<org.jetbrains.kotlin.psi.KtNamedFunction>()
                        .find { it.name == "onDestroy" || it.name == "onDestroyView" }

                if (onDestroy != null) {
                    val body = onDestroy.bodyBlockExpression ?: return
                    val newExpr =
                        factory.createExpression("$handlerName.removeCallbacksAndMessages(null)")
                    body.addBefore(newExpr, body.rBrace)
                } else {
                    val newFunc = factory.createFunction(
                            "override fun onDestroy() {\n    super.onDestroy()\n    $handlerName.removeCallbacksAndMessages(null)\n}"
                        )
                    ktClass.add(newFunc)
                }
            }
        }
    }
}
