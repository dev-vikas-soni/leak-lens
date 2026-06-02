package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.*
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import com.intellij.uast.UastHintedVisitorAdapter

/**
 * Detects coroutines launched in GlobalScope that capture Context/Activity references.
 * Pattern: `GlobalScope.launch { ... this@Activity ... }`
 * 
 * Migrated to UAST to support both Java and Kotlin.
 */
class GlobalScopeWithContextInspection : LocalInspectionTool() {

    override fun getGroupDisplayName(): String = "LeakLens"
    override fun getDisplayName(): String = "GlobalScope coroutine may leak Activity/Context"
    override fun getShortName(): String = "LeakLensGlobalScopeWithContext"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val methodName = node.methodName
                    val receiver = node.receiver

                    // Detect GlobalScope.launch or GlobalScope.async
                    if (receiver?.asSourceString() == "GlobalScope" && methodName in listOf("launch", "async")) {
                        // Check if inside an Activity or Fragment
                        val containingClass = node.getContainingUClass() ?: return false
                        if (isActivityOrFragment(containingClass)) {
                            val elementToHighlight = node.methodIdentifier?.sourcePsi ?: node.sourcePsi ?: return false
                            holder.registerProblem(
                                elementToHighlight,
                                "LeakLens: GlobalScope.${methodName} in an Activity/Fragment may cause a memory leak. " +
                                "Use lifecycleScope or viewModelScope instead, which auto-cancel on lifecycle end.",
                                ProblemHighlightType.WARNING
                            )
                        }
                    }
                    return false
                }
            },
            arrayOf(UCallExpression::class.java)
        )
    }

    private fun isActivityOrFragment(uClass: UClass): Boolean {
        var current = uClass.javaPsi.superClass
        while (current != null) {
            val name = current.qualifiedName ?: ""
            if (name.contains("Activity") || name.contains("Fragment")) return true
            current = current.superClass
        }
        return false
    }
}

