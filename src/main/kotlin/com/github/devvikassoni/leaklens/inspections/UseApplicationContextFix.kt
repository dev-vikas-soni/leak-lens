package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAssignmentExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSuperTypeList
import org.jetbrains.kotlin.psi.KtThisExpression

/**
 * Removes a stored Context field from a Worker and rewrites its usages to the inherited
 * `applicationContext` / `getApplicationContext()`.
 *
 * - Kotlin `class W(private val ctx: Context, …)`: drops `private val` and rewrites member usages.
 * - Kotlin body property `private val ctx = context`: removes the property and rewrites usages.
 * - Java field: removes the field and its constructor assignment and rewrites usages.
 */
class UseApplicationContextFix(private val fieldName: String) : LocalQuickFix {

    override fun getName(): String = "Replace '$fieldName' with applicationContext"
    override fun getFamilyName(): String = "LeakLens: Use applicationContext"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        val declaration = PsiTreeUtil.getParentOfType(
            element, false, KtParameter::class.java, KtProperty::class.java, PsiField::class.java
        ) ?: return
        when (declaration) {
            is KtParameter -> fixKotlinParameter(project, declaration)
            is KtProperty -> fixKotlinProperty(project, declaration)
            is PsiField -> fixJavaField(project, declaration)
        }
    }

    private fun fixKotlinParameter(project: Project, parameter: KtParameter) {
        val ktClass = PsiTreeUtil.getParentOfType(parameter, KtClassOrObject::class.java) ?: return
        val body = ktClass.body
        if (body != null) replaceKotlinUsages(project, parameter, LocalSearchScope(body))
        VISIBILITY_MODIFIERS.forEach { parameter.removeModifier(it) }
        parameter.valOrVarKeyword?.delete()
        LeakFixUtils.reformat(project, parameter)
    }

    private fun fixKotlinProperty(project: Project, property: KtProperty) {
        val ktClass = PsiTreeUtil.getParentOfType(property, KtClassOrObject::class.java) ?: return
        replaceKotlinUsages(project, property, LocalSearchScope(ktClass))
        property.delete()
    }

    private fun replaceKotlinUsages(project: Project, target: PsiElement, scope: LocalSearchScope) {
        val factory = KtPsiFactory(project)
        val refs = ReferencesSearch.search(target, scope).findAll().map { it.element }
        for (ref in refs) {
            if (PsiTreeUtil.getParentOfType(ref, KtSuperTypeList::class.java) != null) continue
            val qualified = ref.parent as? KtDotQualifiedExpression
            val toReplace = if (qualified?.receiverExpression is KtThisExpression &&
                qualified.selectorExpression == ref
            ) {
                qualified
            } else {
                ref
            }
            toReplace.replace(factory.createExpression("applicationContext"))
        }
    }

    private fun fixJavaField(project: Project, field: PsiField) {
        val psiClass = field.containingClass ?: return
        val factory = JavaPsiFacade.getElementFactory(project)
        val refs = ReferencesSearch.search(field, LocalSearchScope(psiClass)).findAll().map { it.element }
        for (ref in refs) {
            val expr = ref as? PsiReferenceExpression ?: continue
            val assignment = expr.parent as? PsiAssignmentExpression
            if (assignment != null && assignment.lExpression == expr) {
                (assignment.parent as? PsiExpressionStatement)?.delete()
                continue
            }
            expr.replace(factory.createExpressionFromText("getApplicationContext()", expr))
        }
        field.delete()
    }

    private companion object {
        val VISIBILITY_MODIFIERS = listOf(
            KtTokens.PRIVATE_KEYWORD,
            KtTokens.PROTECTED_KEYWORD,
            KtTokens.INTERNAL_KEYWORD,
            KtTokens.PUBLIC_KEYWORD,
        )
    }
}
