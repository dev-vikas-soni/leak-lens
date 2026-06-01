package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

/**
 * Detects coroutines launched in GlobalScope that capture Context/Activity references.
 * Pattern: `GlobalScope.launch { ... this@Activity ... }`
 */
class GlobalScopeWithContextInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun getGroupDisplayName(): String = "LeakLens"
    override fun getDisplayName(): String = "GlobalScope coroutine may leak Activity/Context"
    override fun getShortName(): String = "LeakLensGlobalScopeWithContext"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)

                val methodExpr = expression.methodExpression
                val qualifier = methodExpr.qualifierExpression?.text ?: ""
                val methodName = methodExpr.referenceName ?: ""

                // Detect GlobalScope.launch or GlobalScope.async
                if (qualifier == "GlobalScope" && methodName in listOf("launch", "async")) {
                    // Check if inside an Activity or Fragment
                    val containingClass = PsiTreeUtil.getParentOfType(expression, PsiClass::class.java) ?: return
                    if (isActivityOrFragment(containingClass)) {
                        holder.registerProblem(
                            expression.methodExpression,
                            "LeakLens: GlobalScope.${methodName} in an Activity/Fragment may cause a memory leak. " +
                            "Use lifecycleScope or viewModelScope instead, which auto-cancel on lifecycle end.",
                            ProblemHighlightType.WARNING
                        )
                    }
                }
            }
        }
    }

    private fun isActivityOrFragment(psiClass: PsiClass): Boolean {
        var current = psiClass.superClass
        while (current != null) {
            val name = current.qualifiedName ?: ""
            if (name.contains("Activity") || name.contains("Fragment")) return true
            current = current.superClass
        }
        return false
    }
}

