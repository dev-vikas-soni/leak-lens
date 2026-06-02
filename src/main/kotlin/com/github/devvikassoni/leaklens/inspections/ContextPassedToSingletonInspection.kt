package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.*
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import com.intellij.uast.UastHintedVisitorAdapter

/**
 * Detects Context (Activity) passed to a Singleton or stored in a static holder.
 * Pattern: `MySingleton.init(this)` where `this` is an Activity.
 * 
 * Migrated to UAST to support both Java and Kotlin.
 */
class ContextPassedToSingletonInspection : LocalInspectionTool() {

    override fun getGroupDisplayName(): String = "LeakLens"
    override fun getDisplayName(): String = "Activity Context passed to Singleton"
    override fun getShortName(): String = "LeakLensContextPassedToSingleton"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val args = node.valueArguments
                    for (arg in args) {
                        // Check if 'this' (an Activity) or 'context' is passed
                        if (isActivityContext(arg)) {
                            val method = node.resolve() ?: continue
                            val containingClass = method.containingClass ?: continue

                            if (isSingleton(containingClass)) {
                                val paramIndex = args.indexOf(arg)
                                val paramType = method.parameterList.parameters.getOrNull(paramIndex)?.type?.canonicalText ?: ""

                                if (paramType.contains("Context") || paramType.contains("Activity")) {
                                    val elementToHighlight = arg.sourcePsi ?: node.sourcePsi ?: continue
                                    holder.registerProblem(
                                        elementToHighlight,
                                        "LeakLens: Passing Activity Context to a Singleton will cause a memory leak. " +
                                        "Use 'context.applicationContext' or '@ApplicationContext' instead.",
                                        ProblemHighlightType.WARNING
                                    )
                                }
                            }
                        }
                    }
                    return false
                }
            },
            arrayOf(UCallExpression::class.java)
        )
    }

    private fun isActivityContext(arg: UExpression): Boolean {
        if (arg is UThisExpression) return true
        if (arg is USimpleNameReferenceExpression && (arg.identifier == "context" || arg.identifier == "activity")) return true
        return false
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

