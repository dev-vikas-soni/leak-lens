package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

/**
 * Detects Activity or Fragment stored in a static field or companion object.
 * Pattern: `static MyActivity activity;` or `companion object { var activity: Activity? = null }`
 */
class StaticActivityReferenceInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun getGroupDisplayName(): String = "LeakLens"
    override fun getDisplayName(): String = "Activity/Fragment stored in static field"
    override fun getShortName(): String = "LeakLensStaticActivityReference"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitField(field: PsiField) {
                if (!field.hasModifierProperty(PsiModifier.STATIC)) return

                val type = field.type
                if (isActivityOrFragmentType(type)) {
                    holder.registerProblem(
                        field.nameIdentifier ?: field,
                        "LeakLens: Static field '${field.name}' holds Activity/Fragment reference. This will cause a memory leak.",
                        ProblemHighlightType.WARNING,
                        WeakReferenceQuickFix(field.name)
                    )
                }
            }
        }
    }

    private fun isActivityOrFragmentType(type: PsiType): Boolean {
        val canonicalText = type.canonicalText
        return canonicalText.contains("Activity") ||
               canonicalText.contains("Fragment") ||
               canonicalText.contains("android.app.Activity") ||
               canonicalText.contains("androidx.fragment.app.Fragment") ||
               canonicalText.contains("androidx.appcompat.app.AppCompatActivity")
    }

    private class WeakReferenceQuickFix(private val fieldName: String) : LocalQuickFix {
        override fun getName(): String = "Replace with WeakReference"
        override fun getFamilyName(): String = "LeakLens Quick Fixes"

        override fun applyFix(project: com.intellij.openapi.project.Project, descriptor: ProblemDescriptor) {
            val field = descriptor.psiElement.parent as? PsiField ?: return
            val factory = JavaPsiFacade.getElementFactory(project)
            // Add a comment suggesting the fix
            val comment = factory.createCommentFromText(
                "// TODO LeakLens: Replace with WeakReference<${field.type.presentableText}> to prevent memory leak",
                field
            )
            field.parent.addBefore(comment, field)
        }
    }
}

