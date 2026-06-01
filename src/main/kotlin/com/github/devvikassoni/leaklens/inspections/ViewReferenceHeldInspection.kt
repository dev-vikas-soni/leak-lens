package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

/**
 * Detects View references held in Fragment fields that are not nulled out in onDestroyView.
 * Pattern: Fragment field of type View/ViewBinding without cleanup.
 */
class ViewReferenceHeldInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun getGroupDisplayName(): String = "LeakLens"
    override fun getDisplayName(): String = "View reference held beyond lifecycle in Fragment"
    override fun getShortName(): String = "LeakLensViewReferenceHeld"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitClass(aClass: PsiClass) {
                if (!isFragment(aClass)) return

                // Find fields that hold View or ViewBinding references
                val viewFields = aClass.fields.filter { field ->
                    isViewOrBindingType(field.type)
                }

                if (viewFields.isEmpty()) return

                // Check if onDestroyView nulls them out
                val onDestroyView = aClass.findMethodsByName("onDestroyView", false).firstOrNull()
                val bodyText = onDestroyView?.body?.text ?: ""

                for (field in viewFields) {
                    val isNulled = bodyText.contains("${field.name} = null") ||
                                   bodyText.contains("${field.name}=null")
                    if (!isNulled) {
                        holder.registerProblem(
                            field.nameIdentifier ?: field,
                            "LeakLens: View/Binding field '${field.name}' is not nulled in onDestroyView(). " +
                            "When Fragment goes on back stack, the view is destroyed but the field retains it, causing a leak. " +
                            "Set ${field.name} = null in onDestroyView().",
                            ProblemHighlightType.WARNING
                        )
                    }
                }
            }
        }
    }

    private fun isFragment(psiClass: PsiClass): Boolean {
        var current = psiClass.superClass
        while (current != null) {
            val name = current.qualifiedName ?: ""
            if (name.contains("Fragment")) return true
            current = current.superClass
        }
        return false
    }

    private fun isViewOrBindingType(type: PsiType): Boolean {
        val name = type.canonicalText
        return name.contains("View") || name.contains("Binding") ||
               name.contains("android.widget.") || name.contains("android.view.")
    }
}

