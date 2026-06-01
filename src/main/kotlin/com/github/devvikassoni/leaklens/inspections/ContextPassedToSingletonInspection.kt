package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.*

/**
 * Detects Context (Activity) passed to a Singleton or stored in a static holder.
 * Pattern: `MySingleton.init(this)` where `this` is an Activity.
 */
class ContextPassedToSingletonInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun getGroupDisplayName(): String = "LeakLens"
    override fun getDisplayName(): String = "Activity Context passed to Singleton"
    override fun getShortName(): String = "LeakLensContextPassedToSingleton"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)

                val args = expression.argumentList.expressions
                for (arg in args) {
                    // Check if 'this' (an Activity) is passed as an argument
                    if (arg is PsiThisExpression || (arg is PsiReferenceExpression && arg.text == "context")) {
                        val method = expression.resolveMethod() ?: continue
                        val containingClass = method.containingClass ?: continue

                        // Check if the receiving class is a singleton (has static INSTANCE or object)
                        if (isSingleton(containingClass)) {
                            val paramType = method.parameterList.parameters
                                .getOrNull(args.indexOf(arg))?.type?.canonicalText ?: ""

                            if (paramType.contains("Context") || paramType.contains("Activity")) {
                                holder.registerProblem(
                                    arg,
                                    "LeakLens: Passing Activity Context to a Singleton will cause a memory leak. " +
                                    "Use 'context.applicationContext' or '@ApplicationContext' instead.",
                                    ProblemHighlightType.WARNING
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isSingleton(psiClass: PsiClass): Boolean {
        // Check for static INSTANCE field (Kotlin object pattern)
        val hasInstance = psiClass.fields.any { field ->
            field.hasModifierProperty(PsiModifier.STATIC) &&
            (field.name == "INSTANCE" || field.name == "instance" || field.name == "sInstance")
        }
        // Check for @Singleton annotation
        val hasSingletonAnnotation = psiClass.annotations.any {
            it.qualifiedName?.contains("Singleton") == true
        }
        return hasInstance || hasSingletonAnnotation
    }
}

