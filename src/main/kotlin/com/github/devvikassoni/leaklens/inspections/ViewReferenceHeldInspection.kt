package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.*
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import com.intellij.uast.UastHintedVisitorAdapter

/**
 * Detects View references held in Fragment fields that are not nulled out in onDestroyView.
 * Pattern: Fragment field of type View/ViewBinding without cleanup.
 * 
 * Migrated to UAST to support both Java and Kotlin.
 */
class ViewReferenceHeldInspection : LocalInspectionTool() {

    override fun getGroupDisplayName(): String = "LeakLens"
    override fun getDisplayName(): String = "View reference held beyond lifecycle in Fragment"
    override fun getShortName(): String = "LeakLensViewReferenceHeld"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitClass(node: UClass): Boolean {
                    if (!isFragment(node)) return false

                    // Find fields that hold View or ViewBinding references
                    val viewFields = node.fields.filter { field ->
                        isViewOrBindingType(field.type)
                    }

                    if (viewFields.isEmpty()) return false

                    // Check if onDestroyView nulls them out
                    val onDestroyView = node.methods.find { it.name == "onDestroyView" }
                    val bodyText = onDestroyView?.uastBody?.asSourceString() ?: ""

                    for (field in viewFields) {
                        val fieldName = field.name
                        val isNulled = bodyText.contains("$fieldName = null") ||
                                       bodyText.contains("$fieldName=null") ||
                                       bodyText.contains("_$fieldName = null") // Common Kotlin pattern for backing fields
                        
                        if (!isNulled) {
                            val elementToHighlight = field.uastAnchor?.sourcePsi ?: field.sourcePsi ?: continue
                            holder.registerProblem(
                                elementToHighlight,
                                "LeakLens: View/Binding field '$fieldName' is not nulled in onDestroyView(). " +
                                "When Fragment goes on back stack, the view is destroyed but the field retains it, causing a leak. " +
                                "Set $fieldName = null in onDestroyView().",
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

    private fun isFragment(uClass: UClass): Boolean {
        // 1. Check resolved superclass chain
        var current = uClass.javaPsi.superClass
        while (current != null) {
            val name = current.qualifiedName ?: ""
            if (name.contains("Fragment")) return true
            current = current.superClass
        }
        // 2. Fallback: check raw super type reference text (handles unresolved/mock classes)
        uClass.javaPsi.extendsList?.referenceElements?.forEach { ref ->
            val text = ref.text ?: ""
            val refName = ref.referenceName ?: ""
            if (text.contains("Fragment") || refName.contains("Fragment")) return true
        }
        // 3. UAST super types
        uClass.uastSuperTypes.forEach { superType ->
            val text = superType.sourcePsi?.text ?: ""
            val canonicalText = runCatching { superType.type.canonicalText }.getOrDefault("")
            if (text.contains("Fragment") || canonicalText.contains("Fragment")) return true
        }
        return false
    }

    private fun isViewOrBindingType(type: PsiType): Boolean {
        val name = type.canonicalText
        return name.contains("View") || name.contains("Binding") ||
               name.contains("android.widget.") || name.contains("android.view.")
    }
}

