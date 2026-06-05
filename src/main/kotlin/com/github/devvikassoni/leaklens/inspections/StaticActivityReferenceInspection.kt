package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.*
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.intellij.openapi.project.Project

/**
 * Detects Activity or Fragment stored in a static field or companion object.
 * Migrated to UAST for robust multi-language support.
 */
class StaticActivityReferenceInspection : LocalInspectionTool() {

    override fun getGroupDisplayName() = "LeakLens"
    override fun getDisplayName() = "Activity/Fragment stored in static field"
    override fun getShortName() = "LeakLensStaticActivityReference"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val fileIssues = mutableListOf<LeakInfo>()
        
        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitField(node: UField): Boolean {
                    if (node.isStatic && LeakLensInspectionUtils.isActivityOrFragmentType(node.type)) {
                        val elementToHighlight = node.uastAnchor?.sourcePsi ?: node.sourcePsi ?: return false
                        
                        holder.registerProblem(
                            elementToHighlight,
                            "LeakLens: Static field '${node.name}' holds an Activity/Fragment reference. " +
                            "This causes a memory leak as static fields outlive the Activity lifecycle.",
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            WrapWithWeakReferenceFix(node.name)
                        )
                        
                        if (isOnTheFly) {
                            fileIssues.add(createLeakInfo(node))
                        }
                    }
                    return false
                }
                
                override fun afterVisitFile(node: UFile) {
                    LeakLensInspectionUtils.reportLiveIssue(holder, isOnTheFly, "StaticActivityReference", fileIssues)
                }
            },
            arrayOf(UField::class.java, UFile::class.java)
        )
    }

    private fun createLeakInfo(node: UField): LeakInfo {
        val line = node.sourcePsi?.let { LeakLensInspectionUtils.getLineNumber(it) } ?: 0
        return LeakInfo(
            signature = "static_leak_${node.name}_$line",
            shortDescription = "Static Field holding Activity/Fragment",
            leakTrace = "Static field: ${node.name} (line $line)",
            retainedObjectClassName = node.type.canonicalText,
            retainedByteSize = 0,
            retainedObjectCount = 1,
            severity = LeakSeverity.WARNING,
            referenceChain = emptyList(),
            suggestedFix = "Wrap the reference in a WeakReference or clear it in onDestroy()."
        )
    }

    private class WrapWithWeakReferenceFix(private val fieldName: String) : LocalQuickFix {
        override fun getName() = "Wrap '$fieldName' with WeakReference"
        override fun getFamilyName() = "LeakLens quick fixes"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement
            val factory = JavaPsiFacade.getElementFactory(project)
            val uField = element.toUElementOfType<UField>() ?: return
            val fieldType = uField.type.presentableText
            
            if (element.language.id == "JAVA") {
                val psiField = uField.javaPsi as? PsiField ?: return
                val newField = factory.createFieldFromText(
                    "private static java.lang.ref.WeakReference<$fieldType> ${fieldName}Ref;", 
                    psiField.parent
                )
                psiField.replace(newField)
            } else {
                val comment = factory.createCommentFromText("// LeakLens: Consider using WeakReference<$fieldType> to avoid leaks", element)
                element.parent.addBefore(comment, element)
            }
        }
    }
}
