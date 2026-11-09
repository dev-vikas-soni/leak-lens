package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifier
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UField
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Detects View / ViewBinding fields in a Fragment that are not set to `null` in `onDestroyView()`.
 * The Fragment outlives its view (back stack), so such fields leak the whole detached hierarchy.
 */
class ViewReferenceHeldInspection : LeakLensInspectionBase() {

    override fun getDisplayName() = "View reference held beyond lifecycle in Fragment"
    override fun getShortName() = "LeakLensViewReferenceHeld"

    override fun buildLeakVisitor(reporter: LeakReporter): PsiElementVisitor =
        UastHintedVisitorAdapter.create(
            reporter.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitClass(node: UClass): Boolean {
                    if (!AndroidTypes.isFragment(node.javaPsi)) return false
                    val viewFields = node.fields.filter { !it.isStatic && AndroidTypes.isViewOrBinding(it.type) }
                    if (viewFields.isEmpty()) return false

                    val cleared = clearedInOnDestroyView(node, viewFields)
                    for (field in viewFields) {
                        if (field in cleared) continue
                        val anchor = field.uastAnchor?.sourcePsi ?: field.sourcePsi ?: continue
                        val fix = if (canBeNulled(field)) NullifyInOnDestroyViewFix(field.name) else null
                        reporter.report(
                            anchor,
                            "LeakLens: View field '${field.name}' is not cleared in onDestroyView(); " +
                                "the Fragment retains its destroyed view hierarchy.",
                            Finding(
                                key = LeakReporter.memberKey(field, field.name),
                                title = "View reference '${field.name}' not cleared",
                                location = "Fragment field ${field.name}",
                                retainedClass = field.type.canonicalText,
                                suggestedFix = "Make '${field.name}' nullable and set it to null in onDestroyView(), " +
                                    "or use a lifecycle-aware delegate (e.g. viewBinding/autoCleared).",
                            ),
                            *listOfNotNull(fix).toTypedArray(),
                        )
                    }
                    return false
                }
            },
            arrayOf(UClass::class.java)
        )

    private fun clearedInOnDestroyView(node: UClass, fields: List<UField>): Set<UField> {
        val cleared = mutableSetOf<UField>()
        node.methods.filter { it.name == "onDestroyView" }.forEach { method ->
            method.uastBody?.accept(object : AbstractUastVisitor() {
                override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                    val right = node.rightOperand.skipParenthesizedExprDown()
                    if (node.operator == UastBinaryOperator.ASSIGN && right is ULiteralExpression && right.isNull) {
                        fields.firstOrNull { AndroidTypes.refersTo(node.leftOperand, it) }?.let { cleared += it }
                    }
                    return super.visitBinaryExpression(node)
                }
            })
        }
        return cleared
    }

    private fun canBeNulled(field: UField): Boolean = when (val src = field.sourcePsi) {
        is KtProperty -> src.isVar && !src.hasModifier(org.jetbrains.kotlin.lexer.KtTokens.LATEINIT_KEYWORD) &&
            src.typeReference?.text?.endsWith("?") == true
        is PsiField -> !src.hasModifierProperty(PsiModifier.FINAL)
        else -> false
    }

    private class NullifyInOnDestroyViewFix(private val fieldName: String) : LocalQuickFix {
        override fun getName() = "Set '$fieldName' to null in onDestroyView()"
        override fun getFamilyName() = "LeakLens: Clear view reference"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement ?: return
            if (LeakFixUtils.isKotlin(element)) {
                val ktClass = PsiTreeUtil.getParentOfType(element, KtClassOrObject::class.java) ?: return
                LeakFixUtils.addToKotlinOverride(ktClass, "onDestroyView", "$fieldName = null", atStart = false)
            } else {
                val psiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java) ?: return
                LeakFixUtils.addToJavaOverride(
                    psiClass,
                    "onDestroyView",
                    "public",
                    "$fieldName = null;",
                    atStart = false
                )
            }
        }
    }
}
