package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

/**
 * Detects Handler usage without removeCallbacksAndMessages in onDestroy.
 * Pattern: Activity has a Handler field but no cleanup in onDestroy.
 */
class MissingRemoveCallbacksInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun getGroupDisplayName(): String = "LeakLens"
    override fun getDisplayName(): String = "Missing removeCallbacksAndMessages in onDestroy"
    override fun getShortName(): String = "LeakLensMissingRemoveCallbacks"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitClass(aClass: PsiClass) {
                if (!isActivityOrFragment(aClass)) return

                // Find Handler fields
                val handlerFields = aClass.fields.filter { field ->
                    field.type.canonicalText.contains("Handler")
                }

                if (handlerFields.isEmpty()) return

                // Check if onDestroy has removeCallbacksAndMessages
                val onDestroy = aClass.findMethodsByName("onDestroy", false).firstOrNull()
                    ?: aClass.findMethodsByName("onDestroyView", false).firstOrNull()

                val hasCleanup = onDestroy?.body?.text?.contains("removeCallbacksAndMessages") == true ||
                                 onDestroy?.body?.text?.contains("removeCallbacks") == true

                if (!hasCleanup) {
                    for (field in handlerFields) {
                        holder.registerProblem(
                            field.nameIdentifier ?: field,
                            "LeakLens: Handler '${field.name}' may cause a leak. " +
                            "Call ${field.name}.removeCallbacksAndMessages(null) in onDestroy().",
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

