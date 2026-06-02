package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.*
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import com.intellij.uast.UastHintedVisitorAdapter

/**
 * Detects Handler usage without removeCallbacksAndMessages in onDestroy.
 * Pattern: Activity has a Handler field but no cleanup in onDestroy.
 * 
 * Migrated to UAST to support both Java and Kotlin.
 */
class MissingRemoveCallbacksInspection : LocalInspectionTool() {

    override fun getGroupDisplayName(): String = "LeakLens"
    override fun getDisplayName(): String = "Missing removeCallbacksAndMessages in onDestroy"
    override fun getShortName(): String = "LeakLensMissingRemoveCallbacks"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitClass(node: UClass): Boolean {
                    if (!isActivityOrFragment(node)) return false

                    // Find Handler fields
                    val handlerFields = node.fields.filter { field ->
                        field.type.canonicalText.contains("Handler")
                    }

                    if (handlerFields.isEmpty()) return false

                    // Check if onDestroy has removeCallbacksAndMessages
                    val onDestroy = node.methods.find { it.name == "onDestroy" || it.name == "onDestroyView" }
                    val bodyText = onDestroy?.uastBody?.asSourceString() ?: ""

                    val hasCleanup = bodyText.contains("removeCallbacksAndMessages") ||
                                     bodyText.contains("removeCallbacks")

                    if (!hasCleanup) {
                        for (field in handlerFields) {
                            val elementToHighlight = field.uastAnchor?.sourcePsi ?: field.sourcePsi ?: continue
                            holder.registerProblem(
                                elementToHighlight,
                                "LeakLens: Handler '${field.name}' may cause a leak. " +
                                "Call ${field.name}.removeCallbacksAndMessages(null) in onDestroy().",
                                ProblemHighlightType.WARNING
                            )
                        }
                    }
                    return false
                }
            },
            arrayOf(UClass::class.java)
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

