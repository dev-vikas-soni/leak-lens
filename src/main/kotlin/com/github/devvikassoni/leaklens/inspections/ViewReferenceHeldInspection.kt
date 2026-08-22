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
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Detects View references held in Fragment fields that are not nulled out in onDestroyView.
 */
class ViewReferenceHeldInspection : LocalInspectionTool() {

    override fun getGroupDisplayName() = "LeakLens"
    override fun getDisplayName() = "View reference held beyond lifecycle in Fragment"
    override fun getShortName() = "LeakLensViewReferenceHeld"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val fileIssues = mutableListOf<LeakInfo>()

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
                        // Look for name = null or _name = null (common for backing fields) or name?.let { it = null } etc.
                        // We use a simple but broader string check for UAST source string.
                        val isNulled = bodyText.contains("$name = null") ||
                                bodyText.contains("$name=null") ||
                                bodyText.contains("_$name = null") ||
                                bodyText.contains("$name?.let") ||
                                bodyText.contains("$name.clear") // For some custom binding types

                        if (!isNulled) {
                            val elementToHighlight = field.uastAnchor?.sourcePsi ?: field.sourcePsi ?: continue
                            val description =
                                "LeakLens: View field '$name' is not nulled in onDestroyView()."
                            holder.registerProblem(
                                elementToHighlight,
                                description,
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                NullifyInOnDestroyViewFix(name),
                                AskGeminiFix(
                                    description,
                                    field.type.canonicalText,
                                    LeakLensInspectionUtils.getLineNumber(elementToHighlight)
                                )
                            )

                            fileIssues.add(createLeakInfo(field))
                        }
                    }
                    return false
                }
            },
            arrayOf(UClass::class.java)
        )
    }

    private fun createLeakInfo(field: UField): LeakInfo {
        val line = field.sourcePsi?.let { LeakLensInspectionUtils.getLineNumber(it) } ?: 0
        return LeakInfo(
            signature = "view_reference_leak_${field.name}_$line",
            shortDescription = "View reference ${field.name} not cleared",
            leakTrace = "Fragment field: ${field.name} (line $line)",
            retainedObjectClassName = "android.view.View",
            retainedByteSize = 0,
            retainedObjectCount = 1,
            severity = LeakSeverity.WARNING,
            referenceChain = emptyList(),
            suggestedFix = "Set ${field.name} = null in onDestroyView() to allow GC."
        )
    }

    private class NullifyInOnDestroyViewFix(private val fieldName: String) : LocalQuickFix {
        override fun getName() = "Nullify '$fieldName' in onDestroyView()"
        override fun getFamilyName() = "LeakLens quick fixes"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
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
                    val newFunc =
                        factory.createFunction(
                            "override fun onDestroyView() {\n    super.onDestroyView()\n    $fieldName = null\n}"
                        )
                    ktClass.add(newFunc)
                }
            }
        }
    }
}
