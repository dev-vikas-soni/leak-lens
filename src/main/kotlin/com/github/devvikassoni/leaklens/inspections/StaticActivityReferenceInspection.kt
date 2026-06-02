package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.*
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import com.intellij.uast.UastHintedVisitorAdapter

/**
 * Detects Activity or Fragment stored in a static field or companion object.
 * Pattern: `static MyActivity activity;` or `companion object { var activity: Activity? = null }`
 * 
 * Migrated to UAST to support both Java and Kotlin.
 */
class StaticActivityReferenceInspection : LocalInspectionTool() {

    override fun getGroupDisplayName(): String = "LeakLens"
    override fun getDisplayName(): String = "Activity/Fragment stored in static field"
    override fun getShortName(): String = "LeakLensStaticActivityReference"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitField(node: UField): Boolean {
                    if (node.isStatic && isActivityOrFragmentType(node.type)) {
                        val elementToHighlight = node.uastAnchor?.sourcePsi ?: node.sourcePsi ?: return false
                        holder.registerProblem(
                            elementToHighlight,
                            "LeakLens: Static field '${node.name}' holds Activity/Fragment reference. This will cause a memory leak.",
                            ProblemHighlightType.WARNING,
                            WeakReferenceQuickFix(node.name)
                        )
                    }
                    return false
                }
            },
            arrayOf(UField::class.java)
        )
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
            val element = descriptor.psiElement
            val factory = JavaPsiFacade.getElementFactory(project)
            
            if (element.language.id == "JAVA") {
                val field = element.parent as? PsiField ?: return
                val comment = factory.createCommentFromText(
                    "// TODO LeakLens: Replace with WeakReference<${field.type.presentableText}> to prevent memory leak",
                    field
                )
                field.parent.addBefore(comment, field)
            } else {
                val comment = factory.createCommentFromText(
                    "// TODO LeakLens: Replace with WeakReference to prevent memory leak",
                    element
                )
                element.parent.addBefore(comment, element)
            }
        }
    }
}

